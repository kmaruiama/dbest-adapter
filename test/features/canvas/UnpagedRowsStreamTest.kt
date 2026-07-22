package dbest.features.canvas

import dbest.features.sessions.EngineLock
import dbest.features.sessions.acquireExclusive
import dbest.kernel.adapter.Plan
import dbest.kernel.adapter.agg
import dbest.kernel.adapter.by
import dbest.kernel.adapter.collapse
import dbest.kernel.adapter.compactRowJson
import dbest.kernel.adapter.execute
import dbest.kernel.adapter.intColumn
import dbest.kernel.adapter.insert
import dbest.kernel.adapter.join
import dbest.kernel.adapter.memoryTable
import dbest.kernel.adapter.on
import dbest.kernel.adapter.project
import dbest.kernel.adapter.rowNumber
import dbest.kernel.adapter.scan
import dbest.kernel.adapter.schema
import dbest.kernel.adapter.stringColumn
import dbest.kernel.adapter.sum
import dbest.kernel.json.compactJsonText
import kotlin.test.Test
import kotlin.test.assertEquals

class UnpagedRowsStreamTest {

    @Test
    fun `cursor stream preserves materialized row values and schema order`() {
        val users = memoryTable("users", intColumn("id", primaryKey = true), stringColumn("name"))
        insert(users, mapOf("id" to 1, "name" to "A\n\"na\\"))
        insert(users, mapOf("id" to 2, "name" to "Bruno"))
        val orders = memoryTable("orders", intColumn("id", primaryKey = true), intColumn("user_id"), intColumn("total"))
        insert(orders, mapOf("id" to 10, "user_id" to 1, "total" to 250))
        insert(orders, mapOf("id" to 11, "user_id" to 2, "total" to 900))

        val joined = project(join(scan(users, "u"), scan(orders, "o"), on("u.id", "o.user_id")), "u.name", "o.total")
        val plans = listOf<Plan>(
            joined,
            collapse(joined, "result"),
            rowNumber(joined, "n", "position", start = 7),
            agg(scan(orders, "o"), "totals", by("o.user_id"), sum("total")),
        )

        for (plan in plans) {
            val expected = execute(plan).map { compactJsonText(compactRowJson(it, schema(plan))) }
            val actual = openUnpagedRowsStream(plan, acquireExclusive(EngineLock()))
                .bufferedReader()
                .use { it.lineSequence().toList() }

            assertEquals(expected, actual)
        }
    }
}
