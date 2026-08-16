package dbest.model

import dbest.adapter.Agg
import dbest.adapter.AggFunction
import dbest.adapter.ColumnRef
import dbest.adapter.CompareOp
import dbest.adapter.Comparison
import dbest.adapter.JoinAlgorithm
import dbest.adapter.JoinTerm
import dbest.adapter.JoinType
import dbest.adapter.LogicalKind
import dbest.adapter.QualifiedCol
import dbest.adapter.SetKind
import dbest.adapter.SortKey
import dbest.misc.existsInCollection
import dbest.misc.filterCollection
import dbest.misc.mapCollection

/*
Catalogo da paleta: um chip por VARIANTE de operador, nao um por tipo de node. O DBest do Swing
tinha um botao para cada algoritmo de join e cada modo de set op, e ate agora o cliente espelhava
essa matriz a mao — inclusive quais combinacoes o engine consegue executar. Repare que nao existe
chip de full outer join nested-loop, nem de right semi/anti nested-loop: sao exatamente os casos
que a engine recusa em tempo de execucao (422). Esse conhecimento eh daqui, entao a lista mora
aqui e o cliente so a desenha.

Os templates sao construidos pelos mesmos data classes que /commands valida, entao um template
ilegal nao chega a ser servido — quebra na inicializacao. O PLACEHOLDER existe justamente para
passar pelos require() de nao-vazio enquanto o usuario ainda nao configurou o operador.
*/

// marcador de "ainda nao configurado": satisfaz os require() de nao-vazio e aparece como "?" no canvas
const val PLACEHOLDER: String = "?"

enum class Arity { SOURCE, UNARY, BINARY }

// o nome que vai no fio eh o mesmo que o cliente ja usava para agrupar a paleta
enum class OperatorCategory(val wire: String) {
    ALGEBRA("Algebra"),
    AGGREGATION("Aggregation"),
    ETL("ETL"),
    INDEX("Index"),
    JOINS("Joins"),
    SEMI_ANTI_JOINS("SemiAntiJoins"),
    SETS("Sets"),
    BOOLEAN("Boolean"),
}

val CATEGORY_ORDER: List<OperatorCategory> = listOf(
    OperatorCategory.ALGEBRA,
    OperatorCategory.AGGREGATION,
    OperatorCategory.ETL,
    OperatorCategory.INDEX,
    OperatorCategory.JOINS,
    OperatorCategory.SEMI_ANTI_JOINS,
    OperatorCategory.SETS,
    OperatorCategory.BOOLEAN,
)

data class PaletteChip(
    val key: String,
    val symbol: String,
    val category: OperatorCategory,
    val template: Node,
)

// aridade a partir da propria hierarquia selada: nada a manter em sincronia
fun arityOf(node: Node): Arity = when (node) {
    is SourceNode -> Arity.SOURCE
    is UnaryNode -> Arity.UNARY
    is BinaryNode -> Arity.BINARY
}

// como o cliente desenha cada campo. O cliente nao conhece operador nenhum: ele le esta lista e
// monta o formulario. CONDITION eh o unico caso irredutivel — a condicao do filter eh uma arvore
// recursiva, entao o cliente tem um editor proprio para ela.
enum class Widget { TEXT, INT, FLAG, COLUMN, QUALIFIED, PICK, CONDITION, LIST, ROWS }

// um campo de formulario. options so vale para PICK, item so para LIST, of so para ROWS.
data class FieldSpec(
    val at: String,
    val widget: Widget,
    val options: List<String> = emptyList(),
    val item: Widget? = null,
    val of: List<FieldSpec> = emptyList(),
)

private fun <T : Enum<T>> names(values: List<T>): List<String> = mapCollection(values, { it.name })

// os campos editaveis de cada tipo de node, na ordem em que o formulario os mostra. Um tipo
// ausente daqui nao tem formulario (materialize, memoize, hashIndex, cross) e cai pronto no canvas.
// O scan tambem fica de fora: nasce da lista de tabelas, nao da paleta.
val FIELDS: Map<String, List<FieldSpec>> = mapOf(
    "filter" to listOf(
        FieldSpec("condition", Widget.CONDITION),
    ),
    "project" to listOf(
        FieldSpec("columns", Widget.LIST, item = Widget.COLUMN),
    ),
    "sort" to listOf(
        FieldSpec(
            "keys",
            Widget.ROWS,
            of = listOf(FieldSpec("column", Widget.COLUMN), FieldSpec("ascending", Widget.FLAG)),
        ),
    ),
    "distinct" to listOf(
        FieldSpec("hashed", Widget.FLAG),
    ),
    "limit" to listOf(
        FieldSpec("count", Widget.INT),
        FieldSpec("offset", Widget.INT),
    ),
    "alias" to listOf(
        FieldSpec("from", Widget.COLUMN),
        FieldSpec("to", Widget.TEXT),
    ),
    "collapse" to listOf(
        FieldSpec("alias", Widget.TEXT),
    ),
    "explode" to listOf(
        FieldSpec("column", Widget.COLUMN),
        FieldSpec("delimiter", Widget.TEXT),
    ),
    "rowNumber" to listOf(
        FieldSpec("alias", Widget.TEXT),
        FieldSpec("column", Widget.TEXT),
        FieldSpec("start", Widget.INT),
    ),
    "agg" to listOf(
        FieldSpec("alias", Widget.TEXT),
        FieldSpec("by", Widget.QUALIFIED),
        FieldSpec(
            "aggregates",
            Widget.ROWS,
            of = listOf(
                FieldSpec("function", Widget.PICK, options = names(AggFunction.entries)),
                FieldSpec("column", Widget.COLUMN),
            ),
        ),
        FieldSpec("hashed", Widget.FLAG),
    ),
    "removeColumns" to listOf(
        FieldSpec("columns", Widget.LIST, item = Widget.COLUMN),
        FieldSpec("alias", Widget.TEXT),
    ),
    "join" to listOf(
        FieldSpec("type", Widget.PICK, options = names(JoinType.entries)),
        FieldSpec("algorithm", Widget.PICK, options = names(JoinAlgorithm.entries)),
        FieldSpec(
            "on",
            Widget.ROWS,
            of = listOf(FieldSpec("left", Widget.QUALIFIED), FieldSpec("right", Widget.QUALIFIED)),
        ),
    ),
    "setOp" to listOf(
        FieldSpec("kind", Widget.PICK, options = names(SetKind.entries)),
        FieldSpec("hashed", Widget.FLAG),
    ),
    "logicalOp" to listOf(
        FieldSpec("kind", Widget.PICK, options = names(LogicalKind.entries)),
    ),
    "exists" to listOf(
        FieldSpec("bilateral", Widget.FLAG),
    ),
)

// tem formulario de parametro (a acao "editar") quem tem campos declarados
fun isEditable(kind: String): Boolean = existsInCollection(kind, FIELDS)

// os campos que separam uma variante da outra dentro do mesmo tipo de node. O cliente usa isso
// para descobrir de qual chip veio um node que ja esta no canvas — e com isso, qual simbolo
// desenhar. Tipos ausentes daqui tem um simbolo so.
val VARIANT_FIELDS: Map<String, List<String>> = mapOf(
    "join" to listOf("type", "algorithm"),
    "setOp" to listOf("kind", "hashed"),
    "distinct" to listOf("hashed"),
    "exists" to listOf("bilateral"),
    "logicalOp" to listOf("kind"),
)

// termo de igualdade placeholder dos chips de join, que o aluno preenche depois
private fun joinOn(): List<JoinTerm> = listOf(
    JoinTerm(QualifiedCol(PLACEHOLDER, PLACEHOLDER), QualifiedCol(PLACEHOLDER, PLACEHOLDER)),
)

private fun join(type: JoinType, algorithm: JoinAlgorithm): Node = JoinNode(joinOn(), type, algorithm)

val CATALOG: List<PaletteChip> = listOf(
    // algebra
    PaletteChip(
        "filter",
        "σ",
        OperatorCategory.ALGEBRA,
        FilterNode(Comparison(ColumnRef(null, PLACEHOLDER), CompareOp.EQ, PLACEHOLDER)),
    ),
    PaletteChip("projection", "π", OperatorCategory.ALGEBRA, ProjectNode(listOf(PLACEHOLDER))),
    PaletteChip("selectColumns", "S", OperatorCategory.ALGEBRA, RemoveColumnsNode(listOf(PLACEHOLDER))),
    PaletteChip("rename", "ρ", OperatorCategory.ALGEBRA, AliasNode(PLACEHOLDER, PLACEHOLDER)),
    PaletteChip("sort", "↕", OperatorCategory.ALGEBRA, SortNode(listOf(SortKey(PLACEHOLDER, true)))),
    PaletteChip("limit", "L", OperatorCategory.ALGEBRA, LimitNode(10)),
    PaletteChip("duplicateRemoval", "Δ", OperatorCategory.ALGEBRA, DistinctNode(false)),
    PaletteChip("hashDuplicateRemoval", "#Δ", OperatorCategory.ALGEBRA, DistinctNode(true)),

    // agregacao
    PaletteChip(
        "aggregation",
        "∑",
        OperatorCategory.AGGREGATION,
        // um agregado placeholder: AggNode exige pelo menos um, entao um template com a lista
        // vazia seria recusado pelo proprio /commands ao ser solto no canvas
        AggNode(PLACEHOLDER, null, listOf(Agg(PLACEHOLDER, AggFunction.COUNT))),
    ),
    PaletteChip("collapse", "{}", OperatorCategory.AGGREGATION, CollapseNode(PLACEHOLDER)),

    // etl
    PaletteChip("explode", "E", OperatorCategory.ETL, ExplodeNode(PLACEHOLDER)),
    PaletteChip("autoInc", "A", OperatorCategory.ETL, RowNumberNode(PLACEHOLDER, PLACEHOLDER)),

    // indices
    PaletteChip("hash", "#", OperatorCategory.INDEX, HashIndexNode),
    PaletteChip("memoize", "ℳ", OperatorCategory.INDEX, MemoizeNode),
    PaletteChip("materialization", "⧉", OperatorCategory.INDEX, MaterializeNode),

    // joins
    PaletteChip("join", "|X|", OperatorCategory.JOINS, join(JoinType.INNER, JoinAlgorithm.NESTED_LOOP)),
    PaletteChip("mergeJoin", "↕|X|", OperatorCategory.JOINS, join(JoinType.INNER, JoinAlgorithm.MERGE)),
    PaletteChip("hashJoin", "#|X|", OperatorCategory.JOINS, join(JoinType.INNER, JoinAlgorithm.HASH)),
    PaletteChip("leftOuterJoin", "⟕", OperatorCategory.JOINS, join(JoinType.LEFT, JoinAlgorithm.NESTED_LOOP)),
    PaletteChip("rightOuterJoin", "⟖", OperatorCategory.JOINS, join(JoinType.RIGHT, JoinAlgorithm.NESTED_LOOP)),
    PaletteChip("mergeLeftOuterJoin", "↕⟕", OperatorCategory.JOINS, join(JoinType.LEFT, JoinAlgorithm.MERGE)),
    PaletteChip("mergeRightOuterJoin", "↕⟖", OperatorCategory.JOINS, join(JoinType.RIGHT, JoinAlgorithm.MERGE)),
    PaletteChip("mergeFullOuterJoin", "↕⟗", OperatorCategory.JOINS, join(JoinType.FULL, JoinAlgorithm.MERGE)),
    PaletteChip("hashLeftOuterJoin", "#⟕", OperatorCategory.JOINS, join(JoinType.LEFT, JoinAlgorithm.HASH)),
    PaletteChip("hashRightOuterJoin", "#⟖", OperatorCategory.JOINS, join(JoinType.RIGHT, JoinAlgorithm.HASH)),
    PaletteChip("hashFullOuterJoin", "#⟗", OperatorCategory.JOINS, join(JoinType.FULL, JoinAlgorithm.HASH)),
    PaletteChip("cartesianProduct", "✕", OperatorCategory.JOINS, CrossNode),

    // semi / anti joins
    PaletteChip("semiJoin", "⋉", OperatorCategory.SEMI_ANTI_JOINS, join(JoinType.LEFT_SEMI, JoinAlgorithm.NESTED_LOOP)),
    PaletteChip("mergeLeftSemiJoin", "↕⋉", OperatorCategory.SEMI_ANTI_JOINS, join(JoinType.LEFT_SEMI, JoinAlgorithm.MERGE)),
    PaletteChip("mergeRightSemiJoin", "↕⋊", OperatorCategory.SEMI_ANTI_JOINS, join(JoinType.RIGHT_SEMI, JoinAlgorithm.MERGE)),
    PaletteChip("hashLeftSemiJoin", "#⋉", OperatorCategory.SEMI_ANTI_JOINS, join(JoinType.LEFT_SEMI, JoinAlgorithm.HASH)),
    PaletteChip("hashRightSemiJoin", "#⋊", OperatorCategory.SEMI_ANTI_JOINS, join(JoinType.RIGHT_SEMI, JoinAlgorithm.HASH)),
    PaletteChip("antiJoin", "▷", OperatorCategory.SEMI_ANTI_JOINS, join(JoinType.LEFT_ANTI, JoinAlgorithm.NESTED_LOOP)),
    PaletteChip("mergeLeftAntiJoin", "↕▷", OperatorCategory.SEMI_ANTI_JOINS, join(JoinType.LEFT_ANTI, JoinAlgorithm.MERGE)),
    PaletteChip("mergeRightAntiJoin", "↕◁", OperatorCategory.SEMI_ANTI_JOINS, join(JoinType.RIGHT_ANTI, JoinAlgorithm.MERGE)),
    PaletteChip("hashLeftAntiJoin", "#▷", OperatorCategory.SEMI_ANTI_JOINS, join(JoinType.LEFT_ANTI, JoinAlgorithm.HASH)),
    PaletteChip("hashRightAntiJoin", "#◁", OperatorCategory.SEMI_ANTI_JOINS, join(JoinType.RIGHT_ANTI, JoinAlgorithm.HASH)),

    // conjuntos e existencia
    PaletteChip("append", "+", OperatorCategory.SETS, SetOpNode(SetKind.APPEND, false)),
    PaletteChip("union", "∪", OperatorCategory.SETS, SetOpNode(SetKind.UNION, false)),
    PaletteChip("hashUnion", "#∪", OperatorCategory.SETS, SetOpNode(SetKind.UNION, true)),
    PaletteChip("intersection", "∩", OperatorCategory.SETS, SetOpNode(SetKind.INTERSECT, false)),
    PaletteChip("hashIntersection", "#∩", OperatorCategory.SETS, SetOpNode(SetKind.INTERSECT, true)),
    PaletteChip("difference", "-", OperatorCategory.SETS, SetOpNode(SetKind.EXCEPT, false)),
    PaletteChip("hashDifference", "#-", OperatorCategory.SETS, SetOpNode(SetKind.EXCEPT, true)),
    PaletteChip("unilateralExistence", "∃⟗", OperatorCategory.SETS, ExistsNode(false)),
    PaletteChip("bilateralExistence", "∃⨝", OperatorCategory.SETS, ExistsNode(true)),

    // booleanos
    PaletteChip("logicalAnd", "∧", OperatorCategory.BOOLEAN, LogicalOpNode(LogicalKind.AND)),
    PaletteChip("logicalOr", "∨", OperatorCategory.BOOLEAN, LogicalOpNode(LogicalKind.OR)),
    PaletteChip("logicalXor", "⊻", OperatorCategory.BOOLEAN, LogicalOpNode(LogicalKind.XOR)),
)

// os tipos de node que a paleta oferece, cada um uma vez (o cliente indexa aridade/editavel por aqui)
fun catalogKinds(): List<String> {
    val seen = LinkedHashSet<String>()
    for (chip in CATALOG) {
        seen.add(operatorKind(chip.template))
    }
    return seen.toList()
}

// um node de exemplo por tipo, para o codec ler aridade e afins sem repetir a busca
fun sampleOf(kind: String): Node {
    val chips = filterCollection(CATALOG, { operatorKind(it.template) == kind })
    return chips.first().template
}
