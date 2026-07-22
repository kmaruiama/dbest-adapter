package dbest.features.canvas.history

import dbest.features.canvas.graph.DistinctNode
import dbest.features.canvas.graph.Edge
import dbest.features.canvas.graph.FilterNode
import dbest.features.canvas.graph.JoinNode
import dbest.features.canvas.graph.MemorySpec
import dbest.features.canvas.graph.NodeId
import dbest.features.canvas.graph.Port
import dbest.features.canvas.graph.Position
import dbest.features.canvas.graph.ScanNode
import dbest.features.canvas.graph.Session
import dbest.features.canvas.graph.TableId
import dbest.features.canvas.query.OpenTables
import dbest.features.canvas.query.closeTables
import dbest.features.canvas.query.execute
import dbest.kernel.adapter.gt
import dbest.kernel.adapter.gte
import dbest.kernel.adapter.intColumn
import dbest.kernel.adapter.on
import dbest.kernel.adapter.stringColumn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HistoryTest {

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
    fun `commands build an executable session`() {
        val tables = OpenTables()
        try {
            val rows = execute(demo().session, NodeId(4), tables)
            assertEquals(2, rows.size)
            assertEquals(setOf<Any?>("Ana", "Carla"), rows.map { it["u.name"] }.toSet())
        } finally {
            closeTables(tables)
        }
    }

    @Test
    fun `undo inverts every command kind`() {
        val commands = demoCommands() + listOf(
            Move(NodeId(4), Position(500.0, 60.0)),
            SetNode(NodeId(4), FilterNode(gte("o.total", 250))),
            Disconnect(Edge(NodeId(3), NodeId(4), Port.ONLY)),
            RemoveNode(NodeId(4)),
            Batch(listOf(
                AddNode(NodeId(5), DistinctNode(), Position(320.0, 60.0)),
                Connect(Edge(NodeId(3), NodeId(5), Port.ONLY)),
            )),
        )
        var history = History()
        for (command in commands) {
            val before = history.session
            history = edit(history, command)
            assertEquals(before, undo(history).session, "undo failed for $command")
        }
    }

    @Test
    fun `undo all the way back reaches the empty session, redo replays forward`() {
        var history = demo()
        val full = history.session
        repeat(history.undoStack.size) { history = undo(history) }
        assertEquals(Session(), history.session)
        repeat(history.redoStack.size) { history = redo(history) }
        assertEquals(full, history.session)
    }

    @Test
    fun `removing a node restores its edges and position on undo`() {
        val history = edit(demo(), RemoveNode(NodeId(3)))
        assertTrue(history.session.edges.isEmpty())

        val restored = undo(history).session
        assertEquals(demo().session, restored)
    }

    @Test
    fun `drags coalesce into a single undo step`() {
        val start = demo()
        val dragged = listOf(
            Move(NodeId(4), Position(330.0, 60.0)),
            Move(NodeId(4), Position(340.0, 60.0)),
            Move(NodeId(4), Position(350.0, 60.0)),
        ).fold(start, ::edit)

        assertEquals(start.undoStack.size + 1, dragged.undoStack.size)
        assertEquals(Position(350.0, 60.0), dragged.session.layout[NodeId(4)])
        assertEquals(Position(320.0, 60.0), undo(dragged).session.layout[NodeId(4)])
    }

    @Test
    fun `history is capped at its limit`() {
        var history = History(limit = 3)
        for (i in 1..5) {
            history = edit(history, AddTable(TableId(i), usersSpec().copy(name = "t$i")))
        }
        assertEquals(3, history.undoStack.size)
        assertEquals(5, history.session.tables.size)
    }

    @Test
    fun `bad connections are rejected`() {
        val session = demo().session
        assertFailsWith<IllegalArgumentException> {
            apply(session, Connect(Edge(NodeId(4), NodeId(1), Port.ONLY)))
        }
        assertFailsWith<IllegalArgumentException> {
            apply(session, Connect(Edge(NodeId(2), NodeId(4), Port.LEFT)))
        }
        assertFailsWith<IllegalArgumentException> {
            apply(session, Connect(Edge(NodeId(2), NodeId(3), Port.RIGHT)))
        }
        assertFailsWith<IllegalArgumentException> {
            apply(session, RemoveTable(TableId(1)))
        }
    }
}
