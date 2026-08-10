package dbest.model

import dbest.adapter.LogicalKind
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

    // o fixture-v1 golden eh congelado (serializador original), entao os nos novos sao
    // cobertos aqui, num round-trip proprio — um por kind de LogicalOpNode.
    @Test
    fun `logical operator nodes round-trip through the codec`() {
        val history = listOf(
            AddNode(NodeId(1), LogicalOpNode(LogicalKind.AND), Position(0.0, 0.0)),
            AddNode(NodeId(2), LogicalOpNode(LogicalKind.OR), Position(0.0, 60.0)),
            AddNode(NodeId(3), LogicalOpNode(LogicalKind.XOR), Position(0.0, 120.0)),
        ).fold(History()) { history, command -> edit(history, command) }

        assertEquals(history, historyOf(parsedJson(jsonText(json(history)))))
    }
}
