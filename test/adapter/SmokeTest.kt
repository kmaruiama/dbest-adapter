package dbest.adapter

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
        // consecutive-run grouping over 1, 3, 1 yields three groups — the lesson, not a bug
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

        // cada operador produz uma unica linha booleana sob a fonte "condition"
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

        // fontes de dados puras: a engine trata um lado vazio como "satisfeito", entao
        // XOR eh sempre falso (true ^ true) — comportamento real fixado por probe
        assertEquals(listOf<Any?>(false), execute(logicalXor(full(), full())).map { it["condition.EVAL"] })
        assertEquals(listOf<Any?>(false), execute(logicalXor(full(), none())).map { it["condition.EVAL"] })

        // encadeando outro LogicalOp (fonte booleana de coluna unica) o XOR passa a discriminar
        val falseCond = { logicalAnd(none(), none()) } // EVAL=false
        val trueCond = { logicalAnd(full(), full()) }  // EVAL=true
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
}
