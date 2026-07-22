package dbest.features.export

import dbest.kernel.adapter.SchemaColumn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExportTest {

    private fun schema(): List<SchemaColumn> = listOf(
        SchemaColumn("u", "id", "INTEGER", true),
        SchemaColumn("u", "name", "STRING", false),
        SchemaColumn("u", "active", "BOOLEAN", false),
    )

    private fun rows(): List<Map<String, Any?>> = listOf(
        mapOf("u.id" to 1, "u.name" to "O'Brien", "u.active" to true),
        mapOf("u.id" to 2, "u.name" to "x, \"y\"", "u.active" to null),
    )

    @Test
    fun `csv follows schema order, quotes per RFC 4180, and blanks null`() {
        val csv = exportRows(ExportFormat.CSV, "users", schema(), rows())
        val expected =
            "id,name,active\r\n" +
                "1,O'Brien,true\r\n" +
                "2,\"x, \"\"y\"\"\",\r\n"
        assertEquals(expected, csv)
    }

    @Test
    fun `csv quotes fields containing newlines`() {
        val single = listOf(SchemaColumn("t", "note", "STRING", false))
        val row = listOf(mapOf("t.note" to "line1\nline2"))
        val csv = exportRows(ExportFormat.CSV, "t", single, row)
        assertEquals("note\r\n\"line1\nline2\"\r\n", csv)
    }

    @Test
    fun `sql emits CREATE TABLE with MySQL types and primary key`() {
        val sql = exportRows(ExportFormat.SQL, "users", schema(), rows())
        assertTrue("CREATE TABLE IF NOT EXISTS `users` (" in sql)
        assertTrue("`id` INT" in sql)
        assertTrue("`name` TEXT" in sql)
        assertTrue("`active` BOOLEAN" in sql)
        assertTrue("PRIMARY KEY (`id`)" in sql)
    }

    @Test
    fun `sql maps engine numeric types to MySQL types`() {
        val numeric = listOf(
            SchemaColumn("t", "a", "LONG", false),
            SchemaColumn("t", "b", "FLOAT", false),
            SchemaColumn("t", "c", "DOUBLE", false),
        )
        val sql = exportRows(ExportFormat.SQL, "t", numeric, emptyList())
        assertTrue("`a` BIGINT" in sql)
        assertTrue("`b` FLOAT" in sql)
        assertTrue("`c` DOUBLE" in sql)
    }

    @Test
    fun `sql literals - numbers raw, strings escaped, null NULL, boolean TRUE`() {
        val sql = exportRows(ExportFormat.SQL, "users", schema(), rows())
        assertTrue("INSERT INTO `users` (`id`, `name`, `active`) VALUES (1, 'O\\'Brien', TRUE);" in sql)
        assertTrue("VALUES (2, 'x, \"y\"', NULL);" in sql)
    }

    @Test
    fun `sql escapes backslashes then quotes (MySQL)`() {
        val single = listOf(SchemaColumn("t", "p", "STRING", false))
        val row = listOf(mapOf("t.p" to "a\\b'c"))
        val sql = exportRows(ExportFormat.SQL, "t", single, row)
        assertTrue("VALUES ('a\\\\b\\'c');" in sql)
    }

    @Test
    fun `empty rows yields header or create only, no data lines`() {
        val csv = exportRows(ExportFormat.CSV, "users", schema(), emptyList())
        assertEquals("id,name,active\r\n", csv)
        val sql = exportRows(ExportFormat.SQL, "users", schema(), emptyList())
        assertTrue("CREATE TABLE IF NOT EXISTS `users`" in sql)
        assertTrue("INSERT" !in sql)
    }

    @Test
    fun `colliding bare names qualify every column (legacy exportToCSV rule)`() {
        val joined = listOf(
            SchemaColumn("u", "id", "INTEGER", true),
            SchemaColumn("o", "id", "INTEGER", false),
            SchemaColumn("o", "total", "DOUBLE", false),
        )
        val row = listOf(mapOf("u.id" to 1, "o.id" to 9, "o.total" to 5.0))

        val csv = exportRows(ExportFormat.CSV, "orders", joined, row)
        assertEquals("u.id,o.id,o.total", csv.trim().split("\r\n").first())

        val sql = exportRows(ExportFormat.SQL, "orders", joined, row)
        assertTrue("`u.id` INT" in sql)
        assertTrue("`o.id` INT" in sql)
        assertTrue("PRIMARY KEY (`u.id`)" in sql)
        assertTrue("VALUES (1, 9, 5.0);" in sql)
    }

    @Test
    fun `exportFormatOf parses names case-insensitively and rejects the unknown`() {
        assertEquals(ExportFormat.CSV, exportFormatOf("csv"))
        assertEquals(ExportFormat.SQL, exportFormatOf("SQL"))
        assertFailsWith<IllegalArgumentException> { exportFormatOf("xlsx") }
    }
}
