package dbest.features.canvas.query

import dbest.features.canvas.graph.DistinctNode
import dbest.features.canvas.graph.Edge
import dbest.features.canvas.graph.MemorySpec
import dbest.features.canvas.graph.NodeId
import dbest.features.canvas.graph.Port
import dbest.features.canvas.graph.Position
import dbest.features.canvas.graph.ScanNode
import dbest.features.canvas.graph.Session
import dbest.features.canvas.graph.TableId
import dbest.features.canvas.history.AddNode
import dbest.features.canvas.history.AddTable
import dbest.features.canvas.history.Connect
import dbest.features.canvas.history.History
import dbest.features.canvas.history.edit
import dbest.kernel.adapter.intColumn
import kotlin.test.Test
import kotlin.test.assertEquals

class TreesTest {

    private fun sample(): Session {
        val commands = listOf(
            AddTable(TableId(1), MemorySpec("t", listOf(intColumn("id", primaryKey = true)), listOf(mapOf("id" to 1)))),
            AddNode(NodeId(1), ScanNode(TableId(1), "t"), Position(0.0, 0.0)),
            AddNode(NodeId(2), DistinctNode(), Position(0.0, 0.0)),
            AddNode(NodeId(3), DistinctNode(), Position(0.0, 0.0)),
            Connect(Edge(NodeId(1), NodeId(2), Port.ONLY)),
            Connect(Edge(NodeId(2), NodeId(3), Port.ONLY)),
            AddNode(NodeId(4), ScanNode(TableId(1), "t2"), Position(0.0, 0.0)),
        )
        return commands.fold(History()) { history, command -> edit(history, command) }.session
    }

    @Test
    fun `upstream walks ancestors from a node, itself included`() {
        assertEquals(listOf(NodeId(3), NodeId(2), NodeId(1)), upstream(sample(), NodeId(3)))
        assertEquals(listOf(NodeId(4)), upstream(sample(), NodeId(4)))
    }

    @Test
    fun `trees lists one entry per root with its upstream closure`() {
        val trees = trees(sample())
        assertEquals(listOf(NodeId(3), NodeId(4)), trees.map { it.root })
        val chain = trees.first { it.root == NodeId(3) }
        assertEquals(setOf(NodeId(1), NodeId(2), NodeId(3)), chain.nodes.toSet())
    }
}
