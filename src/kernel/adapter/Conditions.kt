package dbest.kernel.adapter

import dbest.kernel.util.isBlank

data class ColumnRef(val source: String?, val name: String) {
    init {
        require(!isBlank(name), { "O nome da coluna nao pode ser vazio" })
    }
}

fun col(ref: String): ColumnRef {
    val dot: Int = ref.indexOf('.')
    return if (dot < 0) ColumnRef(source = null, ref) else ColumnRef(ref.take(dot), ref.substring(dot + 1))
}

data class QualifiedCol(val source: String, val column: String) {
    init {
        require(!isBlank(source) && !isBlank(column), { "A coluna qualificada precisa da fonte e do nome da coluna" })
    }
}

fun qualified(ref: String): QualifiedCol {
    val c: ColumnRef = col(ref)
    val source: String = c.source
        ?: throw EngineException.PlanError("'$ref' precisa ser qualificada com sua fonte, ex: \"o.$ref\"")
    return QualifiedCol(source, c.name)
}

sealed interface Condition

enum class CompareOp { EQ, NEQ, GT, GTE, LT, LTE }

data class Comparison(val left: ColumnRef, val op: CompareOp, val right: Any) : Condition

fun eq(column: String, value: Any?): Condition = comparison(column, CompareOp.EQ, value)
fun neq(column: String, value: Any?): Condition = comparison(column, CompareOp.NEQ, value)
fun gt(column: String, value: Any?): Condition = comparison(column, CompareOp.GT, value)
fun gte(column: String, value: Any?): Condition = comparison(column, CompareOp.GTE, value)
fun lt(column: String, value: Any?): Condition = comparison(column, CompareOp.LT, value)
fun lte(column: String, value: Any?): Condition = comparison(column, CompareOp.LTE, value)

data class IsNull(val column: ColumnRef) : Condition

fun isNull(column: String): Condition = IsNull(col(column))

data class IsNotNull(val column: ColumnRef) : Condition

fun isNotNull(column: String): Condition = IsNotNull(col(column))

data class And(val conditions: List<Condition>) : Condition {
    init {
        require(conditions.size >= 2, { "and() precisa de pelo menos duas condicoes" })
    }
}

fun and(first: Condition, second: Condition, vararg rest: Condition): Condition = And(listOf(first, second, *rest))

data class Or(val conditions: List<Condition>) : Condition {
    init {
        require(conditions.size >= 2, { "or() precisa de pelo menos duas condicoes" })
    }
}

fun or(first: Condition, second: Condition, vararg rest: Condition): Condition = Or(listOf(first, second, *rest))

private fun comparison(column: String, op: CompareOp, value: Any?): Condition = when {
    value == null ->
        throw EngineException.PlanError("nao da para comparar '$column' com null, use isNull() ou isNotNull()")
    value !is ColumnRef && value !is Comparable<*> ->
        throw EngineException.PlanError("${value.javaClass.simpleName} nao pode ser usado como valor de comparacao")
    else -> Comparison(col(column), op, value)
}
