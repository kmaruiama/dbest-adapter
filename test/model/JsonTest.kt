package dbest.model

import dbest.json.historyOf
import dbest.json.json
import dbest.json.jsonText
import dbest.json.parsedJson
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonTest {

    @Test
    fun `fixture written by the original kotlinx serializer still loads`() {
        assertEquals(fixtureHistory(), load("test/model/fixture-v1.dbest"))
    }

    @Test
    fun `every command, node, condition and spec round-trips through the codec`() {
        val history = fixtureHistory()
        assertEquals(history, historyOf(parsedJson(jsonText(json(history)))))
    }

    @Test
    fun `an empty history round-trips`() {
        assertEquals(History(), historyOf(parsedJson(jsonText(json(History())))))
    }
}
