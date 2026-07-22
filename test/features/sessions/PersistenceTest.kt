package dbest.features.sessions

import dbest.features.canvas.graph.Edge
import dbest.features.canvas.graph.FilterNode
import dbest.features.canvas.graph.JoinNode
import dbest.features.canvas.graph.MemorySpec
import dbest.features.canvas.graph.NodeId
import dbest.features.canvas.graph.Port
import dbest.features.canvas.graph.Position
import dbest.features.canvas.graph.ScanNode
import dbest.features.canvas.graph.TableId
import dbest.features.canvas.history.AddNode
import dbest.features.canvas.history.AddTable
import dbest.features.canvas.history.Connect
import dbest.features.canvas.history.History
import dbest.features.canvas.history.Move
import dbest.features.canvas.history.edit
import dbest.features.canvas.history.undo
import dbest.kernel.adapter.gt
import dbest.kernel.adapter.intColumn
import dbest.kernel.adapter.on
import dbest.kernel.adapter.stringColumn
import kotlin.test.Test
import kotlin.test.assertEquals

class PersistenceTest {

    private fun usersSpec() = MemorySpec(
        "users",
        listOf(intColumn("id", primaryKey = true), stringColumn("name"), intColumn("age")),
        listOf(
            mapOf("id" to 1, "name" to "Ana", "age" to 22),
            mapOf("id" to 2, "name" to "Bruno", "age" to 17),
            mapOf("id" to 3, "name" to "Carla", "age" to 34),
        ),
    )

    private fun ordersSpec() = MemorySpec(
        "orders",
        listOf(intColumn("id", primaryKey = true), intColumn("user_id"), intColumn("total")),
        listOf(
            mapOf("id" to 10, "user_id" to 1, "total" to 250),
            mapOf("id" to 11, "user_id" to 1, "total" to 50),
            mapOf("id" to 12, "user_id" to 3, "total" to 900),
        ),
    )

    private fun demo(): History = listOf(
        AddTable(TableId(1), usersSpec()),
        AddTable(TableId(2), ordersSpec()),
        AddNode(NodeId(1), ScanNode(TableId(1), "u"), Position(0.0, 0.0)),
        AddNode(NodeId(2), ScanNode(TableId(2), "o"), Position(0.0, 120.0)),
        AddNode(NodeId(3), JoinNode(listOf(on("u.id", "o.user_id"))), Position(160.0, 60.0)),
        AddNode(NodeId(4), FilterNode(gt("o.total", 100)), Position(320.0, 60.0)),
        Connect(Edge(NodeId(1), NodeId(3), Port.LEFT)),
        Connect(Edge(NodeId(2), NodeId(3), Port.RIGHT)),
        Connect(Edge(NodeId(3), NodeId(4), Port.ONLY)),
    ).fold(History(), ::edit)

    @Test
    fun `sessions persist with their history and round-trip exactly`() {
        val history = undo(edit(demo(), Move(NodeId(4), Position(400.0, 80.0))))

        val file = kotlin.io.path.createTempFile("session", ".dbest").toFile()
        file.deleteOnExit()
        save(history, file.path)

        assertEquals(history, load(file.path))
    }
}
