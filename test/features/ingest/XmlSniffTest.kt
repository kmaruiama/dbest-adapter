package dbest.features.ingest

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XmlSniffTest {

    private lateinit var dir: Path

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("dbest-xml-sniff")
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun xml(name: String, content: String): String {
        val file = dir.resolve(name)
        Files.writeString(file, content)
        return file.toString()
    }

    @Test
    fun `sniffXml auto-detects root and record elements`() {
        val path = xml(
            "employees.xml",
            """
            <employees>
                <employee><name>Ana</name><age>22</age></employee>
                <employee><name>Bruno</name><age>17</age></employee>
                <employee><name>Carla</name><age>34</age></employee>
            </employees>
            """.trimIndent(),
        )
        val guess = sniffXml(path)
        assertEquals("employees", guess.rootElement)
        assertEquals("employee", guess.recordElement)
        assertEquals(listOf("name", "age"), guess.columns)
        assertEquals(3, guess.totalRecords)
        assertEquals(listOf("Ana", "22"), guess.sampleRows[0])
    }

    @Test
    fun `sniffXml respects an explicit recordElement override`() {
        val path = xml(
            "org.xml",
            """
            <org>
                <department><name>TI</name></department>
                <department><name>RH</name></department>
                <employee><name>Ana</name></employee>
                <employee><name>Bruno</name></employee>
                <employee><name>Carla</name></employee>
            </org>
            """.trimIndent(),
        )
        val guess = sniffXml(path, recordElement = "employee")
        assertEquals("employee", guess.recordElement)
        assertEquals(3, guess.totalRecords)
        assertEquals(listOf("Ana", "Bruno", "Carla"), guess.sampleRows.map { it[0] })
    }

    @Test
    fun `sniffXml flattens nested elements to dot-notation columns`() {
        val path = xml(
            "people.xml",
            """
            <people>
                <person><name>Ana</name><address><city>SP</city><zip>01000</zip></address></person>
                <person><name>Bruno</name><address><city>RJ</city><zip>20000</zip></address></person>
            </people>
            """.trimIndent(),
        )
        val guess = sniffXml(path)
        assertTrue(guess.columns.contains("address.city"))
        assertTrue(guess.columns.contains("address.zip"))
        val cityIndex = guess.columns.indexOf("address.city")
        assertEquals("SP", guess.sampleRows[0][cityIndex])
    }

    @Test
    fun `sniffXml exposes attributes as @-prefixed columns`() {
        val path = xml(
            "tagged.xml",
            """
            <items>
                <item id="1"><label>First</label></item>
                <item id="2"><label>Second</label></item>
            </items>
            """.trimIndent(),
        )
        val guess = sniffXml(path)
        assertTrue(guess.columns.contains("@id"))
        val idIndex = guess.columns.indexOf("@id")
        assertEquals("1", guess.sampleRows[0][idIndex])
    }

    @Test
    fun `sniffXml on a missing file returns an empty guess instead of throwing`() {
        val guess = sniffXml(dir.resolve("does-not-exist.xml").toString())
        assertEquals(emptyList(), guess.columns)
        assertEquals(emptyList(), guess.sampleRows)
        assertEquals(0, guess.totalRecords)
    }

    @Test
    fun `sniffXml on malformed XML returns an empty guess instead of throwing`() {
        val path = xml("broken.xml", "<not><valid</xml>")
        val guess = sniffXml(path)
        assertEquals(emptyList(), guess.columns)
    }
}
