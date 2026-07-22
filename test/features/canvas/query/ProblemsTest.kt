package dbest.features.canvas.query

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
import dbest.features.canvas.history.Command
import dbest.features.canvas.history.Connect
import dbest.features.canvas.history.Disconnect
import dbest.features.canvas.history.History
import dbest.features.canvas.history.SetNode
import dbest.features.canvas.history.edit
import dbest.kernel.adapter.gt
import dbest.kernel.adapter.intColumn
import dbest.kernel.adapter.on
import dbest.kernel.adapter.stringColumn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProblemsTest {

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

    private fun demoCommands(): List<Command> = listOf(
        AddTable(TableId(1), usersSpec()),
        AddTable(TableId(2), ordersSpec()),
        AddNode(NodeId(1), ScanNode(TableId(1), "u"), Position(0.0, 0.0)),
        AddNode(NodeId(2), ScanNode(TableId(2), "o"), Position(0.0, 120.0)),
        AddNode(NodeId(3), JoinNode(listOf(on("u.id", "o.user_id"))), Position(160.0, 60.0)),
        AddNode(NodeId(4), FilterNode(gt("o.total", 100)), Position(320.0, 60.0)),
        Connect(Edge(NodeId(1), NodeId(3), Port.LEFT)),
        Connect(Edge(NodeId(2), NodeId(3), Port.RIGHT)),
        Connect(Edge(NodeId(3), NodeId(4), Port.ONLY)),
    )

    private fun demo(): History = demoCommands().fold(History(), ::edit)

    @Test
    fun `problems reports missing inputs and engine errors`() {
        val tables = OpenTables()
        try {
            val incomplete = edit(demo(), Disconnect(Edge(NodeId(2), NodeId(3), Port.RIGHT))).session
            assertEquals(listOf(Problem(NodeId(3), "falta a entrada RIGHT")), problems(incomplete, tables))

            val badColumn = edit(demo(), SetNode(NodeId(4), FilterNode(gt("salary", 100)))).session
            val reported = problems(badColumn, tables)
            assertEquals(listOf(NodeId(4)), reported.map { it.node })
            assertTrue(reported.single().message.contains("not found"))

            assertTrue(problems(demo().session, tables).isEmpty())
        } finally {
            closeTables(tables)
        }
    }
}
