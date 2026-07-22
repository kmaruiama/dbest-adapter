package dbest.features.ingest

import kotlin.test.Test
import kotlin.test.assertEquals

class CsvLineTest {

    @Test
    fun `splitLine keeps a plain header intact`() {
        assertEquals(listOf("id", "name", "note"), splitLine("id,name,note", ','))
    }

    @Test
    fun `splitLine does not split on a separator inside quotes`() {
        assertEquals(listOf("1", "Smith, John", "hello"), splitLine("1,\"Smith, John\",hello", ','))
    }

    @Test
    fun `splitLine unwraps a plain quoted field`() {
        assertEquals(listOf("2", "Plain", "world"), splitLine("2,\"Plain\",world", ','))
    }

    @Test
    fun `splitLine resolves an escaped double-quote inside a quoted field`() {
        assertEquals(listOf("3", "she said \"hi\"", "ok"), splitLine("3,\"she said \"\"hi\"\"\",ok", ','))
    }

    @Test
    fun `splitLine handles the empty line`() {
        assertEquals(emptyList(), splitLine("", ','))
    }

    @Test
    fun `splitLine respects quoting with a non-comma separator`() {
        assertEquals(listOf("1", "a,b", "c"), splitLine("1;\"a,b\";c", ';'))
    }
}
