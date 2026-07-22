package dbest.features.canvas.history

import dbest.features.canvas.graph.LogicalOpNode
import dbest.features.canvas.graph.NodeId
import dbest.features.canvas.graph.Position
import dbest.features.canvas.graph.TableId
import dbest.features.canvas.graph.XmlSpec
import dbest.features.canvas.history.AddNode
import dbest.features.canvas.history.AddTable
import dbest.features.canvas.history.History
import dbest.features.canvas.history.edit
import dbest.features.canvas.history.historyOf
import dbest.features.canvas.history.json
import dbest.features.sessions.load
import dbest.kernel.adapter.LogicalKind
import dbest.kernel.adapter.intColumn
import dbest.kernel.adapter.stringColumn
import dbest.kernel.json.jsonText
import dbest.kernel.json.parsedJson
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonTest {

    @Test
    fun `fixture written by the original kotlinx serializer still loads`() {
        assertEquals(fixtureHistory(), load("test/features/canvas/history/fixture-v1.dbest"))
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

    @Test
    fun `logical operator nodes round-trip through the codec`() {
        val history = listOf(
            AddNode(NodeId(1), LogicalOpNode(LogicalKind.AND), Position(0.0, 0.0)),
            AddNode(NodeId(2), LogicalOpNode(LogicalKind.OR), Position(0.0, 60.0)),
            AddNode(NodeId(3), LogicalOpNode(LogicalKind.XOR), Position(0.0, 120.0)),
        ).fold(History()) { history, command -> edit(history, command) }

        assertEquals(history, historyOf(parsedJson(jsonText(json(history)))))
    }

    @Test
    fun `xml table specs round-trip through the codec`() {
        val history = listOf(
            AddTable(
                TableId(1),
                XmlSpec("employees", "data/employees.xml", listOf(stringColumn("name"), intColumn("age"))),
            ),
            AddTable(
                TableId(2),
                XmlSpec(
                    "orders", "data/orders.xml",
                    listOf(intColumn("id", primaryKey = true), intColumn("total")),
                    rootElement = "orders", recordElement = "order",
                ),
            ),
        ).fold(History()) { history, command -> edit(history, command) }

        assertEquals(history, historyOf(parsedJson(jsonText(json(history)))))
    }
}
