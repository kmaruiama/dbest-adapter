package dbest.features.ingest

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CsvSniffTest {

    private lateinit var dir: Path

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("dbest-csv-sniff")
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun csv(name: String, content: String): String {
        val file = dir.resolve(name)
        Files.writeString(file, content)
        return file.toString()
    }

    @Test
    fun `sniffCsv keeps a quoted field with an embedded separator as one column`() {
        val path = csv("people.csv", "id,name,note\n1,\"Smith, John\",hello\n2,Plain,world\n")
        val guess = sniffCsv(path)
        assertEquals(',', guess.separator)
        assertEquals(listOf("id", "name", "note"), guess.columns)
        assertEquals(listOf("1", "Smith, John", "hello"), guess.sampleRows[0])
        assertEquals(listOf("2", "Plain", "world"), guess.sampleRows[1])
    }

    @Test
    fun `sniffCsv picks the semicolon separator when it is the real delimiter`() {
        val path = csv("euro.csv", "id;name;score\n1;Ana;10\n2;Bruno;20\n")
        val guess = sniffCsv(path)
        assertEquals(';', guess.separator)
        assertEquals(listOf("id", "name", "score"), guess.columns)
        assertEquals(listOf("1", "Ana", "10"), guess.sampleRows[0])
    }

    @Test
    fun `sniffCsv respects an explicit separator override instead of guessing`() {
        val path = csv("piped.csv", "id|name|score\n1|Ana|10\n")
        val guess = sniffCsv(path, separatorOverride = '|')
        assertEquals('|', guess.separator)
        assertEquals(listOf("id", "name", "score"), guess.columns)
    }

    @Test
    fun `sniffCsv respects a headerLine override for files with leading notes`() {
        val path = csv("noted.csv", "# exported 2026-08-22\nid,name\n1,Ana\n2,Bruno\n")
        val guess = sniffCsv(path, headerLine = 2)
        assertEquals(2, guess.headerLine)
        assertEquals(listOf("id", "name"), guess.columns)
        assertEquals(listOf("1", "Ana"), guess.sampleRows[0])
    }

    @Test
    fun `sniffCsv on a missing file returns an empty guess instead of throwing`() {
        val guess = sniffCsv(dir.resolve("does-not-exist.csv").toString())
        assertEquals(emptyList(), guess.columns)
        assertEquals(emptyList(), guess.sampleRows)
    }
}
