package dbest.adapter

import dbest.adapter.compile.compile
import dbest.misc.isEmpty
import ibd.query.Operation
import ibd.query.QueryStats
import ibd.query.Tuple
import java.util.concurrent.ConcurrentHashMap

data class SchemaColumn(val source: String, val name: String, val type: String, val primaryKey: Boolean)

fun execute(plan: Plan): List<Map<String, Any?>> = runPlan(plan, offset = null, limit = null)

fun execute(plan: Plan, offset: Int, limit: Int): List<Map<String, Any?>> = runPlan(plan, offset, limit)

fun exists(plan: Plan): Boolean = !isEmpty(runPlan(plan, offset = 0, limit = 1))

fun schema(plan: Plan): List<SchemaColumn> = schemaCache.getOrPut(plan) { computeSchema(plan) }

fun validate(plan: Plan): List<String> =
    try {
        gate {
            val op: Operation = compile(plan)
            op.run()
            closeQuietly(op)
        }
        emptyList()
    } catch (e: EngineException.PlanError) {
        listOf(e.message ?: "plano invalido")
    }

data class EngineStats(val pkSearches: Long, val nextCalls: Long, val memoryUsed: Long, val filterComparisons: Long, val distinctComparisons: Long, val sortedTuples: Long)

fun stats(): EngineStats =
    EngineStats(QueryStats.PK_SEARCH, QueryStats.NEXT_CALLS, QueryStats.MEMORY_USED, QueryStats.COMPARE_FILTER, QueryStats.COMPARE_DISTINCT_TUPLE, QueryStats.SORT_TUPLES)

fun resetStats() {
    QueryStats.PK_SEARCH = 0
    QueryStats.NEXT_CALLS = 0
    QueryStats.MEMORY_USED = 0
    QueryStats.COMPARE_FILTER = 0
    QueryStats.COMPARE_DISTINCT_TUPLE = 0
    QueryStats.SORT_TUPLES = 0
}

private val schemaCache = ConcurrentHashMap<Plan, List<SchemaColumn>>()

private fun runPlan(plan: Plan, offset: Int?, limit: Int?): List<Map<String, Any?>> = gate {
    val op: Operation = compile(plan)
    try {
        if (offset != null && limit != null) {
            op.setPageInfo(offset, limit)
        }
        val tuples = op.run()
        val aliases: List<String> = exposedAliases(op)
        val rows: MutableList<Map<String, Any?>> = ArrayList()
        while (tuples.hasNext()) {
            rows.add(toRow(tuples.next(), aliases))
        }
        rows
    } finally {
        closeQuietly(op)
    }
}

private fun exposedAliases(op: Operation): List<String> {
    val aliases: MutableList<String> = ArrayList()
    for (source in op.exposedDataSources) {
        aliases.add(source.alias)
    }
    return aliases
}

private fun toRow(tuple: Tuple, aliases: List<String>): Map<String, Any?> {
    val row: MutableMap<String, Any?> = LinkedHashMap()
    for (i in tuple.rows.indices) {
        val dataRow = tuple.rows[i]
        val alias: String = if (i < aliases.size) aliases[i] else "src$i"
        for (column in dataRow.prototype.columns) {
            row.put("$alias.${column.name}", dataRow.getValue(column.name))
        }
    }
    return row
}

private fun computeSchema(plan: Plan): List<SchemaColumn> = gate {
    val op: Operation = compile(plan)
    op.prepareAllDataSources()
    val schema: MutableList<SchemaColumn> = ArrayList()
    for (source in op.exposedDataSources) {
        for (column in source.prototype.columns) {
            schema.add(SchemaColumn(source.alias, column.name, column.type, column.isPrimaryKey))
        }
    }
    schema
}

private fun closeQuietly(op: Operation) {
    try {
        op.close()
    } catch (ignored: Exception) {
    }
}
