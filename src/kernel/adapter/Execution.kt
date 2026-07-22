package dbest.kernel.adapter

import dbest.kernel.adapter.compile.compile
import dbest.kernel.util.isEmpty
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
    if (offset != null && limit != null) return@gate runPagedPlan(plan, offset, limit)
    val op: Operation = compile(plan)
    try {
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

private data class PageCursor(val op: Operation, val iterator: Iterator<Tuple>, val aliases: List<String>, val consumed: Int)

private const val PAGE_CURSOR_CAPACITY = 8

private val pageCursors: LinkedHashMap<Plan, PageCursor> = LinkedHashMap(PAGE_CURSOR_CAPACITY, 0.75f, true)

private fun evictOverCapacity(cache: MutableMap<Plan, PageCursor>): Unit {
    val iterator = cache.entries.iterator()
    while (cache.size > PAGE_CURSOR_CAPACITY && iterator.hasNext()) {
        closeQuietly(iterator.next().value.op)
        iterator.remove()
    }
}

private fun runPagedPlan(plan: Plan, offset: Int, limit: Int): List<Map<String, Any?>> {
    val cursor = synchronized(pageCursors) {
        val cached = pageCursors[plan]
        if (cached != null && cached.consumed <= offset) {
            cached
        } else {
            cached?.let { closeQuietly(it.op) }
            val op = compile(plan)
            val fresh = PageCursor(op, op.run(), exposedAliases(op), consumed = 0)
            pageCursors[plan] = fresh
            evictOverCapacity(pageCursors)
            fresh
        }
    }
    return synchronized(cursor) {
        var current = cursor
        while (current.consumed < offset && current.iterator.hasNext()) {
            current.iterator.next()
            current = current.copy(consumed = current.consumed + 1)
        }
        val rows: MutableList<Map<String, Any?>> = ArrayList()
        while (rows.size < limit && current.iterator.hasNext()) {
            rows.add(toRow(current.iterator.next(), current.aliases))
            current = current.copy(consumed = current.consumed + 1)
        }
        synchronized(pageCursors) {
            if (pageCursors[plan] === cursor) {
                if (current.iterator.hasNext()) pageCursors[plan] = current else pageCursors.remove(plan)
            }
        }
        if (!current.iterator.hasNext()) {
            closeQuietly(current.op)
        }
        rows
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
