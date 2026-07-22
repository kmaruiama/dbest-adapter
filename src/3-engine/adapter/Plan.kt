package dbest.adapter

import dbest.misc.isBlank
import dbest.misc.isEmpty
import dbest.misc.varargToCollection

sealed interface Plan

// scan --------------------------------------------------------------------------------------

data class Scan(val table: TableHandle, val alias: String) : Plan {
    init {
        require(!isBlank(alias), { "O alias nao pode ser uma string vazia" })
    }
}

fun scan(table: TableHandle, alias: String): Plan = Scan(table, alias)

// unary, filters --------------------------------------------------------------------------------------

data class Filter(val input: Plan, val condition: Condition) : Plan

fun filter(input: Plan, condition: Condition): Plan = Filter(input, condition)

data class Project(val input: Plan, val columns: List<String>) : Plan {
    init {
        require(!isEmpty(columns), { "Eh necessario selecionar no minimo uma coluna para projecao" })
    }
}

fun project(input: Plan, vararg columns: String): Plan = Project(input, varargToCollection(*columns))

// A GUI antiga chama este operador de "select columns", mas a classe da engine que
// ele encapsula (RemoveColumns) descarta as colunas listadas — nomeado pelo comportamento real.
// A saida eh unificada sob o alias. "Projection" eh o alias fixo da GUI antiga.
data class RemoveColumns(val input: Plan, val columns: List<String>, val alias: String) : Plan {
    init {
        require(!isEmpty(columns), { "RemoveColumns precisa de pelo menos uma coluna" })
        require(!isBlank(alias), { "RemoveColumns precisa de um alias para sua saida" })
    }
}

fun removeColumns(input: Plan, vararg columns: String, alias: String = "Projection"): Plan =
    RemoveColumns(input, varargToCollection(*columns), alias)

data class SortKey(val column: String, val ascending: Boolean)

data class Sort(val input: Plan, val keys: List<SortKey>) : Plan {
    init {
        require(!isEmpty(keys), { "Sort precisa de pelo menos um parametro" })
    }
}

fun sort(input: Plan, vararg keys: SortKey): Plan = Sort(input, varargToCollection(*keys))

fun asc(column: String): SortKey = SortKey(column, ascending = true)

fun desc(column: String): SortKey = SortKey(column, ascending = false)

data class Distinct(val input: Plan, val hashed: Boolean) : Plan

fun distinct(input: Plan, hashed: Boolean = true): Plan = Distinct(input, hashed)

data class Limit(val input: Plan, val count: Int, val offset: Int) : Plan {
    init {
        require(count > 0, { "Limit precisa ser positivo" })
        require(offset >= 0, { "Offset nao pode ser negativo" })
    }
}

fun limit(input: Plan, count: Int, offset: Int = 0): Plan = Limit(input, count, offset)

// unarios, mutating etc --------------------------------------------------------------------------------------

data class Alias(val input: Plan, val from: String, val to: String) : Plan {
    init {
        require(!isBlank(from) && !isBlank(to), { "Alias precisa do alias atual e do novo alias da fonte" })
    }
}

fun alias(input: Plan, from: String, to: String): Plan = Alias(input, from, to)

data class Collapse(val input: Plan, val alias: String) : Plan {
    init {
        require(!isBlank(alias), { "Collapse precisa de um alias para a fonte unificada" })
    }
}

fun collapse(input: Plan, alias: String): Plan = Collapse(input, alias)

data class Explode(val input: Plan, val column: String, val delimiter: String) : Plan {
    init {
        require(!isBlank(column), { "Explode precisa de uma coluna" })
        require(!isEmpty(delimiter), { "Explode precisa de um delimitador" })
    }
}

fun explode(input: Plan, column: String, delimiter: String = ","): Plan = Explode(input, column, delimiter)

data class RowNumber(val input: Plan, val alias: String, val column: String, val start: Int) : Plan {
    init {
        require(!isBlank(alias), { "RowNumber precisa de um alias de fonte para sua saida" })
        require(!isBlank(column), { "RowNumber precisa do nome de uma coluna" })
    }
}

fun rowNumber(input: Plan, alias: String, column: String, start: Int = 1): Plan =
    RowNumber(input, alias, column, start)

// agregacao --------------------------------------------------------------------------------------

enum class AggFunction { MAX, MIN, COUNT, AVG, SUM, FIRST, LAST, COUNT_ALL, COUNT_NULL }

data class Agg(val column: String, val function: AggFunction)

data class GroupBy(val column: QualifiedCol)

fun by(column: String): GroupBy = GroupBy(qualified(column))

data class Aggregate(val input: Plan, val alias: String, val by: QualifiedCol?, val aggregates: List<Agg>, val hashed: Boolean = true) : Plan {
    init {
        require(!isBlank(alias), { "Agregacao precisa de um alias de fonte para sua saida" })
        require(!isEmpty(aggregates), { "Agregacao precisa de pelo menos um agregado" })
    }
}

fun agg(input: Plan, alias: String, vararg aggregates: Agg): Plan =
    Aggregate(input, alias, by = null, varargToCollection(*aggregates))

fun agg(input: Plan, alias: String, by: GroupBy, vararg aggregates: Agg, hashed: Boolean = true): Plan =
    Aggregate(input, alias, by.column, varargToCollection(*aggregates), hashed)

fun max(column: String): Agg = Agg(column, AggFunction.MAX)
fun min(column: String): Agg = Agg(column, AggFunction.MIN)
fun count(column: String): Agg = Agg(column, AggFunction.COUNT)
fun avg(column: String): Agg = Agg(column, AggFunction.AVG)
fun sum(column: String): Agg = Agg(column, AggFunction.SUM)
fun first(column: String): Agg = Agg(column, AggFunction.FIRST)
fun last(column: String): Agg = Agg(column, AggFunction.LAST)
fun countAll(column: String): Agg = Agg(column, AggFunction.COUNT_ALL)
fun countNull(column: String): Agg = Agg(column, AggFunction.COUNT_NULL)

// joins --------------------------------------------------------------------------------------

enum class JoinType { INNER, LEFT, RIGHT, FULL, LEFT_SEMI, RIGHT_SEMI, LEFT_ANTI, RIGHT_ANTI }

enum class JoinAlgorithm { NESTED_LOOP, HASH, MERGE }

data class JoinTerm(val left: QualifiedCol, val right: QualifiedCol)

fun on(left: String, right: String): JoinTerm = JoinTerm(qualified(left), qualified(right))

data class Join(val left: Plan, val right: Plan, val on: List<JoinTerm>, val type: JoinType, val algorithm: JoinAlgorithm) : Plan {
    init {
        require(!isEmpty(on), { "Join precisa de pelo menos um termo de igualdade" })
    }
}

fun join(left: Plan, right: Plan, vararg on: JoinTerm, type: JoinType = JoinType.INNER, algorithm: JoinAlgorithm = JoinAlgorithm.NESTED_LOOP): Plan =
    Join(left, right, varargToCollection(*on), type, algorithm)

data class CrossJoin(val left: Plan, val right: Plan) : Plan

fun cross(left: Plan, right: Plan): Plan = CrossJoin(left, right)

// operacoes entre conjuntos --------------------------------------------------------------------------------------

enum class SetKind { UNION, INTERSECT, EXCEPT, APPEND }

data class SetOp(val left: Plan, val right: Plan, val kind: SetKind, val hashed: Boolean) : Plan

fun union(left: Plan, right: Plan, hashed: Boolean = true): Plan = SetOp(left, right, SetKind.UNION, hashed)

fun intersect(left: Plan, right: Plan, hashed: Boolean = true): Plan = SetOp(left, right, SetKind.INTERSECT, hashed)

fun except(left: Plan, right: Plan, hashed: Boolean = true): Plan = SetOp(left, right, SetKind.EXCEPT, hashed)

fun append(left: Plan, right: Plan): Plan = SetOp(left, right, SetKind.APPEND, hashed = false)

// binarios, existencia --------------------------------------------------------------------------------------

data class Existence(val left: Plan, val right: Plan, val bilateral: Boolean) : Plan

fun unilateralExistence(left: Plan, right: Plan): Plan = Existence(left, right, bilateral = false)

fun bilateralExistence(left: Plan, right: Plan): Plan = Existence(left, right, bilateral = true)

// cache etc --------------------------------------------------------------------------------------

data class Materialize(val input: Plan) : Plan

fun materialize(input: Plan): Plan = Materialize(input)

data class Memoize(val input: Plan) : Plan

fun memoize(input: Plan): Plan = Memoize(input)

data class HashIndex(val input: Plan) : Plan

fun hashIndex(input: Plan): Plan = HashIndex(input)
