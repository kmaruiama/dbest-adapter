package dbest.features.catalog

import dbest.kernel.adapter.Agg
import dbest.kernel.adapter.AggFunction
import dbest.kernel.adapter.ColumnRef
import dbest.kernel.adapter.CompareOp
import dbest.kernel.adapter.Comparison
import dbest.kernel.adapter.JoinAlgorithm
import dbest.kernel.adapter.JoinTerm
import dbest.kernel.adapter.JoinType
import dbest.kernel.adapter.LogicalKind
import dbest.kernel.adapter.QualifiedCol
import dbest.kernel.adapter.SetKind
import dbest.kernel.adapter.SortKey
import dbest.features.canvas.graph.AggNode
import dbest.features.canvas.graph.AliasNode
import dbest.features.canvas.graph.BinaryNode
import dbest.features.canvas.graph.CollapseNode
import dbest.features.canvas.graph.CrossNode
import dbest.features.canvas.graph.DistinctNode
import dbest.features.canvas.graph.ExistsNode
import dbest.features.canvas.graph.ExplodeNode
import dbest.features.canvas.graph.FilterNode
import dbest.features.canvas.graph.HashIndexNode
import dbest.features.canvas.graph.JoinNode
import dbest.features.canvas.graph.LimitNode
import dbest.features.canvas.graph.LogicalOpNode
import dbest.features.canvas.graph.MaterializeNode
import dbest.features.canvas.graph.MemoizeNode
import dbest.features.canvas.graph.Node
import dbest.features.canvas.graph.ProjectNode
import dbest.features.canvas.graph.RemoveColumnsNode
import dbest.features.canvas.graph.RowNumberNode
import dbest.features.canvas.graph.ScanNode
import dbest.features.canvas.graph.SetOpNode
import dbest.features.canvas.graph.SortNode
import dbest.features.canvas.graph.SourceNode
import dbest.features.canvas.graph.UnaryNode
import dbest.features.canvas.graph.operatorKind
import dbest.kernel.util.existsInCollection
import dbest.kernel.util.filterCollection
import dbest.kernel.util.mapCollection

const val PLACEHOLDER: String = "?"

enum class Arity { SOURCE, UNARY, BINARY }

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

fun arityOf(node: Node): Arity = when (node) {
    is SourceNode -> Arity.SOURCE
    is UnaryNode -> Arity.UNARY
    is BinaryNode -> Arity.BINARY
}

enum class Widget { TEXT, INT, FLAG, COLUMN, QUALIFIED, PICK, CONDITION, LIST, ROWS }

data class FieldSpec(
    val at: String,
    val widget: Widget,
    val options: List<String> = emptyList(),
    val item: Widget? = null,
    val of: List<FieldSpec> = emptyList(),
    val nullable: Boolean = false,
)

private fun <T : Enum<T>> names(values: List<T>): List<String> = mapCollection(values, { it.name })

val FIELDS: Map<String, List<FieldSpec>> = mapOf(
    "filter" to listOf(
        FieldSpec("condition", Widget.CONDITION, options = names(CompareOp.entries)),
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
        FieldSpec("by", Widget.QUALIFIED, nullable = true),
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

fun isEditable(kind: String): Boolean = existsInCollection(kind, FIELDS)

val VARIANT_FIELDS: Map<String, List<String>> = mapOf(
    "join" to listOf("type", "algorithm"),
    "setOp" to listOf("kind", "hashed"),
    "distinct" to listOf("hashed"),
    "exists" to listOf("bilateral"),
    "logicalOp" to listOf("kind"),
)

private fun joinOn(): List<JoinTerm> = listOf(
    JoinTerm(QualifiedCol(PLACEHOLDER, PLACEHOLDER), QualifiedCol(PLACEHOLDER, PLACEHOLDER)),
)

private fun join(type: JoinType, algorithm: JoinAlgorithm): Node = JoinNode(joinOn(), type, algorithm)

val CATALOG: List<PaletteChip> = listOf(
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

    PaletteChip(
        "aggregation",
        "∑",
        OperatorCategory.AGGREGATION,
        AggNode(PLACEHOLDER, null, listOf(Agg(PLACEHOLDER, AggFunction.COUNT))),
    ),
    PaletteChip("collapse", "{}", OperatorCategory.AGGREGATION, CollapseNode(PLACEHOLDER)),

    PaletteChip("explode", "E", OperatorCategory.ETL, ExplodeNode(PLACEHOLDER)),
    PaletteChip("autoInc", "A", OperatorCategory.ETL, RowNumberNode(PLACEHOLDER, PLACEHOLDER)),

    PaletteChip("hash", "#", OperatorCategory.INDEX, HashIndexNode),
    PaletteChip("memoize", "ℳ", OperatorCategory.INDEX, MemoizeNode),
    PaletteChip("materialization", "⧉", OperatorCategory.INDEX, MaterializeNode),

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

    PaletteChip("append", "+", OperatorCategory.SETS, SetOpNode(SetKind.APPEND, false)),
    PaletteChip("union", "∪", OperatorCategory.SETS, SetOpNode(SetKind.UNION, false)),
    PaletteChip("hashUnion", "#∪", OperatorCategory.SETS, SetOpNode(SetKind.UNION, true)),
    PaletteChip("intersection", "∩", OperatorCategory.SETS, SetOpNode(SetKind.INTERSECT, false)),
    PaletteChip("hashIntersection", "#∩", OperatorCategory.SETS, SetOpNode(SetKind.INTERSECT, true)),
    PaletteChip("difference", "-", OperatorCategory.SETS, SetOpNode(SetKind.EXCEPT, false)),
    PaletteChip("hashDifference", "#-", OperatorCategory.SETS, SetOpNode(SetKind.EXCEPT, true)),
    PaletteChip("unilateralExistence", "∃⟗", OperatorCategory.SETS, ExistsNode(false)),
    PaletteChip("bilateralExistence", "∃⨝", OperatorCategory.SETS, ExistsNode(true)),

    PaletteChip("logicalAnd", "∧", OperatorCategory.BOOLEAN, LogicalOpNode(LogicalKind.AND)),
    PaletteChip("logicalOr", "∨", OperatorCategory.BOOLEAN, LogicalOpNode(LogicalKind.OR)),
    PaletteChip("logicalXor", "⊻", OperatorCategory.BOOLEAN, LogicalOpNode(LogicalKind.XOR)),
)

fun catalogKinds(): List<String> {
    val seen = LinkedHashSet<String>()
    for (chip in CATALOG) {
        seen.add(operatorKind(chip.template))
    }
    return seen.toList()
}

fun sampleOf(kind: String): Node {
    val chips = filterCollection(CATALOG, { operatorKind(it.template) == kind })
    return chips.first().template
}
