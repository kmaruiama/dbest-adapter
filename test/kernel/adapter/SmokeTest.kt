package dbest.kernel.adapter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SmokeTest {

    private fun users(): TableHandle {
        val t = memoryTable("users", intColumn("id", primaryKey = true), stringColumn("name"), intColumn("age"))
        insert(t, mapOf("id" to 1, "name" to "Ana", "age" to 22))
        insert(t, mapOf("id" to 2, "name" to "Bruno", "age" to 17))
        insert(t, mapOf("id" to 3, "name" to "Carla", "age" to 34))
        return t
    }

    private fun orders(): TableHandle {
        val t = memoryTable("orders", intColumn("id", primaryKey = true), intColumn("user_id"), intColumn("total"))
        insert(t, mapOf("id" to 10, "user_id" to 1, "total" to 250))
        insert(t, mapOf("id" to 11, "user_id" to 1, "total" to 50))
        insert(t, mapOf("id" to 12, "user_id" to 3, "total" to 900))
        return t
    }

    @Test
    fun `scan filter project`() {
        val plan =
            project(
                filter(
                    scan(users(), "u"),
                    gte("age", 18)),
                "name")

        assertEquals(listOf<Any?>("Ana", "Carla"), execute(plan).map { it["u.name"] })
    }

    @Test
    fun `join correlates rows across tables`() {
        val plan =
            filter(
                join(scan(users(), "u"), scan(orders(), "o"), on("u.id", "o.user_id")),
                gt("o.total", 100))

        val rows = execute(plan)
        assertEquals(2, rows.size)
        assertEquals(setOf<Any?>("Ana", "Carla"), rows.map { it["u.name"] }.toSet())
        assertEquals(setOf<Any?>(250, 900), rows.map { it["o.total"] }.toSet())
    }

    @Test
    fun `join then project keeps only the named columns`() {
        val plan =
            project(
                join(scan(users(), "u"), scan(orders(), "o"), on("u.id", "o.user_id")),
                "u.name", "o.total")

        val rows = execute(plan)
        assertEquals(3, rows.size)
        assertEquals(setOf("u.name", "o.total"), rows.first().keys)
    }

    @Test
    fun `schema without execution, memoized per plan`() {
        val plan = filter(scan(users(), "u"), gte("age", 18))

        val cols = schema(plan)
        assertEquals(3, cols.size)
        assertEquals("u", cols[0].source)
        assertEquals(listOf("id", "name", "age"), cols.map { it.name })
        assertTrue(cols[0].primaryKey)

        assertSame(cols, schema(plan))
    }

    @Test
    fun `unknown column is a PlanError, flagged by validate`() {
        val bad = filter(scan(users(), "u"), gt("salary", 1000))

        assertFailsWith<EngineException.PlanError> { execute(bad) }
        assertTrue(validate(bad).isNotEmpty())
        assertTrue(validate(scan(users(), "u")).isEmpty())
    }

    @Test
    fun `exists pulls at most one row`() {
        assertTrue(exists(scan(users(), "u")))
        assertFalse(exists(filter(scan(users(), "u"), gt("age", 99))))
    }

    @Test
    fun `sort and limit`() {
        val plan = limit(sort(scan(users(), "u"), desc("age")), 2)

        assertEquals(listOf<Any?>(34, 22), execute(plan).map { it["u.age"] })
    }

    @Test
    fun aggregation() {
        val plan = agg(scan(orders(), "o"), "byUser", by("o.user_id"), sum("total"))

        val totals = execute(plan).associate { it["byUser.user_id"] to it["byUser.SUM_total"] }
        assertEquals<Map<Any?, Any?>>(mapOf(1 to 300, 3 to 900), totals)
    }

    private fun interleavedOrders(): TableHandle {
        val t = memoryTable("orders", intColumn("id", primaryKey = true), intColumn("user_id"), intColumn("total"))
        insert(t, mapOf("id" to 10, "user_id" to 1, "total" to 250))
        insert(t, mapOf("id" to 11, "user_id" to 3, "total" to 900))
        insert(t, mapOf("id" to 12, "user_id" to 1, "total" to 50))
        return t
    }

    @Test
    fun `hash aggregation groups unsorted input`() {
        val plan = agg(scan(interleavedOrders(), "o"), "byUser", by("o.user_id"), sum("total"), hashed = true)

        val totals = execute(plan).associate { it["byUser.user_id"] to it["byUser.SUM_total"] }
        assertEquals<Map<Any?, Any?>>(mapOf(1 to 300, 3 to 900), totals)
    }

    @Test
    fun `sort-based aggregation groups when the student sorts first`() {
        val plan = agg(sort(scan(interleavedOrders(), "o"), asc("user_id")), "byUser", by("o.user_id"), sum("total"), hashed = false)

        val totals = execute(plan).associate { it["byUser.user_id"] to it["byUser.SUM_total"] }
        assertEquals<Map<Any?, Any?>>(mapOf(1 to 300, 3 to 900), totals)
    }

    @Test
    fun `sort-based aggregation fragments groups on unsorted input`() {
        val plan = agg(scan(interleavedOrders(), "o"), "byUser", by("o.user_id"), sum("total"), hashed = false)

        val rows = execute(plan)
        assertEquals(listOf<Any?>(1, 3, 1), rows.map { it["byUser.user_id"] })
        assertEquals(listOf<Any?>(250, 900, 50), rows.map { it["byUser.SUM_total"] })
    }

    @Test
    fun `removeColumns drops the listed columns and merges under the alias`() {
        val plan = removeColumns(scan(users(), "u"), "u.name", alias = "r")

        val rows = execute(plan)
        assertEquals(3, rows.size)
        assertEquals(setOf("r.id", "r.age"), rows.first().keys)
    }

    @Test
    fun `existence emits at most one witness tuple`() {
        val adults = filter(scan(users(), "u"), gte("age", 18))
        val nobody = filter(scan(users(), "u"), gt("age", 99))

        assertEquals(1, execute(bilateralExistence(adults, scan(orders(), "o"))).size)
        assertEquals(0, execute(bilateralExistence(adults, nobody)).size)
        assertEquals(1, execute(unilateralExistence(nobody, adults)).size)
        assertEquals(0, execute(unilateralExistence(nobody, nobody)).size)
    }

    @Test
    fun `logical and or emit one boolean row from side existence`() {
        val full = { scan(users(), "u") }
        val none = { filter(scan(users(), "u"), gt("age", 99)) }

        assertEquals(listOf<Any?>(true), execute(logicalAnd(full(), full())).map { it["condition.EVAL"] })
        assertEquals(listOf<Any?>(false), execute(logicalAnd(full(), none())).map { it["condition.EVAL"] })
        assertEquals(listOf<Any?>(false), execute(logicalAnd(none(), full())).map { it["condition.EVAL"] })

        assertEquals(listOf<Any?>(true), execute(logicalOr(full(), none())).map { it["condition.EVAL"] })
        assertEquals(listOf<Any?>(true), execute(logicalOr(none(), full())).map { it["condition.EVAL"] })
        assertEquals(listOf<Any?>(false), execute(logicalOr(none(), none())).map { it["condition.EVAL"] })
    }

    @Test
    fun `logical xor discriminates only when a side is itself a condition`() {
        val full = { scan(users(), "u") }
        val none = { filter(scan(users(), "u"), gt("age", 99)) }

        assertEquals(listOf<Any?>(false), execute(logicalXor(full(), full())).map { it["condition.EVAL"] })
        assertEquals(listOf<Any?>(false), execute(logicalXor(full(), none())).map { it["condition.EVAL"] })

        val falseCond = { logicalAnd(none(), none()) }
        val trueCond = { logicalAnd(full(), full()) }
        assertEquals(listOf<Any?>(true), execute(logicalXor(falseCond(), full())).map { it["condition.EVAL"] })
        assertEquals(listOf<Any?>(false), execute(logicalXor(trueCond(), full())).map { it["condition.EVAL"] })
    }

    @Test
    fun `alias renames a source mid-plan`() {
        val plan = filter(alias(scan(users(), "u"), "u", "x"), gte("x.age", 18))

        val rows = execute(plan)
        assertEquals(2, rows.size)
        assertEquals(setOf("x.id", "x.name", "x.age"), rows.first().keys)
    }

    @Test
    fun `collapse merges joined sources under one alias`() {
        val plan =
            collapse(
                project(
                    join(scan(users(), "u"), scan(orders(), "o"), on("u.id", "o.user_id")),
                    "u.name", "o.total"),
                "r")

        val rows = execute(plan)
        assertEquals(3, rows.size)
        assertEquals(setOf("r.name", "r.total"), rows.first().keys)
    }

    @Test
    fun `explode splits a delimited column into rows`() {
        val t = memoryTable("posts", intColumn("id", primaryKey = true), stringColumn("tags"))
        insert(t, mapOf("id" to 1, "tags" to "a,b,c"))

        val plan = explode(scan(t, "p"), "p.tags")

        assertEquals(listOf<Any?>("a", "b", "c"), execute(plan).map { it["p.tags"] })
    }

    @Test
    fun `rowNumber numbers tuples from start`() {
        val plan = rowNumber(sort(scan(users(), "u"), asc("age")), "n", "idx", start = 10)

        val rows = execute(plan)
        assertEquals(listOf<Any?>(10, 11, 12), rows.map { it["n.idx"] })
        assertEquals(listOf<Any?>("Bruno", "Ana", "Carla"), rows.map { it["u.name"] })
    }

    @Test
    fun `caching wrappers are transparent`() {
        val base = filter(scan(users(), "u"), gte("age", 18))
        val expected = execute(base)

        assertEquals(expected, execute(materialize(base)))
        assertEquals(expected, execute(memoize(base)))
        assertEquals(expected, execute(hashIndex(base)))
    }

    @Test
    fun `csv table reads a file and rejects writes`() {
        val file = kotlin.io.path.createTempFile("people", ".csv").toFile()
        file.deleteOnExit()
        file.writeText("id,name,age\n1,Ana,22\n2,Bruno,17\n3,Carla,34\n")

        val t = csvTable(file.path, "people", intColumn("id", primaryKey = true), stringColumn("name"), intColumn("age"))

        val rows = execute(filter(scan(t, "p"), gte("age", 18)))
        assertEquals(listOf<Any?>("Ana", "Carla"), rows.map { it["p.name"] })
        assertFailsWith<EngineException.StorageError> {
            insert(t, mapOf("id" to 4, "name" to "Duda", "age" to 40))
        }
    }

    @Test
    fun `xml table scans a file with typed columns and rejects writes`() {
        val file = kotlin.io.path.createTempFile("employees", ".xml").toFile()
        file.deleteOnExit()
        file.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <employees>
                <employee><name>Ana</name><age>22</age><dept>TI</dept></employee>
                <employee><name>Bruno</name><age>17</age><dept>RH</dept></employee>
                <employee><name>Carla</name><age>34</age><dept>TI</dept></employee>
            </employees>
            """.trimIndent(),
        )

        val t = xmlTable(file.path, "employees", stringColumn("name"), intColumn("age"), stringColumn("dept"))

        val rows = execute(scan(t, "e"))
        assertEquals(listOf<Any?>("Ana", "Bruno", "Carla"), rows.map { it["e.name"] })
        assertEquals(listOf<Any?>(22, 17, 34), rows.map { it["e.age"] })

        assertFailsWith<EngineException.StorageError> {
            insert(t, mapOf("name" to "Duda", "age" to 40, "dept" to "TI"))
        }

        val filtered = execute(filter(scan(t, "e"), gte("age", 18)))
        assertEquals(3, filtered.size)
    }

    @Test
    fun `stats count engine work and reset to zero`() {
        resetStats()
        execute(filter(scan(users(), "u"), gte("age", 18)))

        assertTrue(stats().nextCalls > 0)
        resetStats()
        assertEquals(0, stats().nextCalls)
    }

    @Test
    fun `invalid plans are rejected at construction, not at execution`() {
        val u = users()
        assertFailsWith<EngineException.PlanError> {
            agg(scan(orders(), "o"), "g", by("user_id"), sum("total"))
        }
        assertFailsWith<EngineException.PlanError> { eq("name", null) }
        assertFailsWith<EngineException.PlanError> {
            insert(u, mapOf("id" to 9, "salary" to 100))
        }
        assertFailsWith<IllegalArgumentException> { project(scan(u, "u")) }
        assertFailsWith<IllegalArgumentException> { join(scan(u, "u"), scan(orders(), "o")) }
        assertFailsWith<IllegalArgumentException> { limit(scan(u, "u"), 0) }
    }

    private fun admins(): TableHandle {
        val t = memoryTable("admins", intColumn("id", primaryKey = true), stringColumn("name"), intColumn("age"))
        insert(t, mapOf("id" to 100, "name" to "Root", "age" to 40))
        return t
    }

    @Test
    fun `union inherits the source qualifier its left input carries`() {
        val plan = union(scan(users(), "u"), scan(admins(), "a"), hashed = false)

        val rows = execute(plan)
        assertEquals(4, rows.size)
        assertEquals(setOf("u.id", "u.name", "u.age"), rows.first().keys)
    }

    @Test
    fun `append merges rows from both sides under the left qualifier`() {
        val plan = append(scan(users(), "u"), scan(admins(), "a"))

        val rows = execute(plan)
        assertEquals(4, rows.size)
        assertEquals(setOf<Any?>("Ana", "Bruno", "Carla", "Root"), rows.map { it["u.name"] }.toSet())
    }

    @Test
    fun `set ops fall back to a synthetic alias when the left side mixes qualifiers`() {
        val joined = { join(scan(users(), "u"), scan(orders(), "o"), on("u.id", "o.user_id")) }
        val plan = union(joined(), joined(), hashed = false)

        assertEquals(setOf("Union"), schema(plan).map { it.source }.toSet())
    }

    @Test
    fun `set ops reject operands with a different number of columns`() {
        val narrowAdmins = memoryTable("narrowAdmins", intColumn("id", primaryKey = true), stringColumn("name"))
        insert(narrowAdmins, mapOf("id" to 100, "name" to "Root"))

        assertFailsWith<EngineException.PlanError> {
            execute(append(scan(users(), "u"), scan(narrowAdmins, "a")))
        }
        assertTrue(validate(union(scan(users(), "u"), scan(narrowAdmins, "a"))).isNotEmpty())
    }

    private fun bigTable(rows: Int): TableHandle {
        val t = memoryTable("big", intColumn("id", primaryKey = true))
        for (i in 1..rows) insert(t, mapOf("id" to i))
        return t
    }

    @Test
    fun `sequential paging resumes instead of rescanning from the start`() {
        val plan = scan(bigTable(50), "b")

        resetStats()
        val firstPage = execute(plan, offset = 0, limit = 10)
        val afterFirst = stats().nextCalls

        val secondPage = execute(plan, offset = 10, limit = 10)
        val afterSecond = stats().nextCalls

        assertEquals((1..10).toList(), firstPage.map { it["b.id"] })
        assertEquals((11..20).toList(), secondPage.map { it["b.id"] })
        assertTrue(afterSecond - afterFirst < 20)
    }

    @Test
    fun `paging to an offset before the cached cursor still returns correct rows`() {
        val plan = scan(bigTable(50), "b")

        execute(plan, offset = 20, limit = 10)
        val rewound = execute(plan, offset = 0, limit = 10)

        assertEquals((1..10).toList(), rewound.map { it["b.id"] })
    }

    @Test
    fun `LRU page-cursor cache evicts the oldest plan once capacity is exceeded`() {
        val table = bigTable(50)
        val evictedPlan = scan(table, "p0")

        execute(evictedPlan, offset = 0, limit = 5)
        for (i in 1..8) {
            execute(scan(table, "p$i"), offset = 0, limit = 5)
        }

        resetStats()
        val resumed = execute(evictedPlan, offset = 5, limit = 5)
        val nextCalls = stats().nextCalls

        assertEquals((6..10).toList(), resumed.map { it["p0.id"] })
        assertTrue(nextCalls >= 10)
    }
}
