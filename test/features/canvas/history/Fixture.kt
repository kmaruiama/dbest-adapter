package dbest.features.canvas.history

import dbest.features.canvas.graph.AggNode
import dbest.features.canvas.graph.AliasNode
import dbest.features.canvas.graph.BTreeSpec
import dbest.features.canvas.graph.CollapseNode
import dbest.features.canvas.graph.CrossNode
import dbest.features.canvas.graph.CsvSpec
import dbest.features.canvas.graph.DistinctNode
import dbest.features.canvas.graph.Edge
import dbest.features.canvas.graph.ExplodeNode
import dbest.features.canvas.graph.FilterNode
import dbest.features.canvas.graph.HashIndexNode
import dbest.features.canvas.graph.JoinNode
import dbest.features.canvas.graph.LimitNode
import dbest.features.canvas.graph.MaterializeNode
import dbest.features.canvas.graph.MemoizeNode
import dbest.features.canvas.graph.MemorySpec
import dbest.features.canvas.graph.NodeId
import dbest.features.canvas.graph.Port
import dbest.features.canvas.graph.Position
import dbest.features.canvas.graph.ProjectNode
import dbest.features.canvas.graph.RowNumberNode
import dbest.features.canvas.graph.ScanNode
import dbest.features.canvas.graph.SetOpNode
import dbest.features.canvas.graph.SortNode
import dbest.features.canvas.graph.TableId
import dbest.features.canvas.history.AddNode
import dbest.features.canvas.history.AddTable
import dbest.features.canvas.history.Batch
import dbest.features.canvas.history.Connect
import dbest.features.canvas.history.Disconnect
import dbest.features.canvas.history.History
import dbest.features.canvas.history.Move
import dbest.features.canvas.history.RemoveNode
import dbest.features.canvas.history.RemoveTable
import dbest.features.canvas.history.SetNode
import dbest.features.canvas.history.edit
import dbest.features.canvas.history.undo
import dbest.kernel.adapter.JoinAlgorithm
import dbest.kernel.adapter.JoinType
import dbest.kernel.adapter.SetKind
import dbest.kernel.adapter.and
import dbest.kernel.adapter.asc
import dbest.kernel.adapter.avg
import dbest.kernel.adapter.booleanColumn
import dbest.kernel.adapter.col
import dbest.kernel.adapter.count
import dbest.kernel.adapter.countAll
import dbest.kernel.adapter.countNull
import dbest.kernel.adapter.desc
import dbest.kernel.adapter.doubleColumn
import dbest.kernel.adapter.eq
import dbest.kernel.adapter.first
import dbest.kernel.adapter.floatColumn
import dbest.kernel.adapter.gt
import dbest.kernel.adapter.gte
import dbest.kernel.adapter.intColumn
import dbest.kernel.adapter.isNotNull
import dbest.kernel.adapter.isNull
import dbest.kernel.adapter.last
import dbest.kernel.adapter.longColumn
import dbest.kernel.adapter.lt
import dbest.kernel.adapter.lte
import dbest.kernel.adapter.max
import dbest.kernel.adapter.min
import dbest.kernel.adapter.neq
import dbest.kernel.adapter.on
import dbest.kernel.adapter.or
import dbest.kernel.adapter.qualified
import dbest.kernel.adapter.stringColumn
import dbest.kernel.adapter.sum

fun fixtureHistory(): History {
    val commands = listOf(
        AddTable(
            TableId(1),
            MemorySpec(
                "users",
                listOf(
                    intColumn("id", primaryKey = true), stringColumn("name"), longColumn("visits"),
                    floatColumn("score"), doubleColumn("ratio"), booleanColumn("active"),
                    stringColumn("nick", nullable = true),
                ),
                listOf(
                    mapOf("id" to 1, "name" to "Ana", "visits" to 10L, "score" to 1.5f, "ratio" to 0.25, "active" to true, "nick" to null),
                    mapOf("id" to 2, "name" to "Bruno", "visits" to 3L, "score" to 2.5f, "ratio" to 0.5, "active" to false, "nick" to "bru"),
                ),
            ),
        ),
        AddTable(
            TableId(2),
            CsvSpec(
                "orders", "data/orders.csv",
                listOf(intColumn("id", primaryKey = true), intColumn("user_id"), intColumn("total")),
                separator = ';', delimiter = '\'', headerLine = 2,
            ),
        ),
        AddTable(TableId(3), BTreeSpec("archive", "data/archive.btree", cacheSize = 512)),
        AddTable(TableId(4), BTreeSpec("scratch", "data/scratch.btree")),
        RemoveTable(TableId(4)),
        AddNode(NodeId(1), ScanNode(TableId(1), "u"), Position(0.0, 0.0)),
        AddNode(NodeId(2), ScanNode(TableId(2), "o"), Position(0.0, 120.0)),
        AddNode(NodeId(3), JoinNode(listOf(on("u.id", "o.user_id")), type = JoinType.LEFT, algorithm = JoinAlgorithm.HASH), Position(160.0, 60.0)),
        AddNode(
            NodeId(4),
            FilterNode(
                and(
                    gte("o.total", 100), or(eq("u.active", true), isNull("u.nick")),
                    isNotNull("u.name"), neq("u.id", col("o.user_id")),
                    gt("u.score", 1.5f), lt("u.ratio", 0.9), lte("u.visits", 100L),
                    eq("u.name", "Ana"),
                )
            ),
            Position(320.0, 60.0),
        ),
        AddNode(NodeId(5), ProjectNode(listOf("u.name", "o.total")), Position(480.0, 60.0)),
        AddNode(NodeId(6), SortNode(listOf(asc("u.name"), desc("o.total"))), Position(0.0, 240.0)),
        AddNode(NodeId(7), DistinctNode(hashed = false), Position(0.0, 300.0)),
        AddNode(NodeId(8), LimitNode(10, offset = 5), Position(0.0, 360.0)),
        AddNode(NodeId(9), AliasNode("u", "people"), Position(0.0, 420.0)),
        AddNode(NodeId(10), CollapseNode("all"), Position(0.0, 480.0)),
        AddNode(NodeId(11), ExplodeNode("u.tags", ";"), Position(0.0, 540.0)),
        AddNode(NodeId(12), RowNumberNode("r", "row", start = 5), Position(0.0, 600.0)),
        AddNode(
            NodeId(13),
            AggNode(
                "byUser", qualified("o.user_id"),
                listOf(sum("total"), count("id"), max("total"), min("total"), avg("total"), first("id"), last("id"), countAll("id"), countNull("id")),
            ),
            Position(0.0, 660.0),
        ),
        AddNode(NodeId(14), AggNode("overall", null, listOf(sum("total"))), Position(0.0, 720.0)),
        AddNode(NodeId(15), MaterializeNode, Position(0.0, 780.0)),
        AddNode(NodeId(16), MemoizeNode, Position(0.0, 840.0)),
        AddNode(NodeId(17), HashIndexNode, Position(0.0, 900.0)),
        AddNode(NodeId(18), CrossNode, Position(0.0, 960.0)),
        AddNode(NodeId(19), SetOpNode(SetKind.UNION, hashed = false), Position(0.0, 1020.0)),
        AddNode(NodeId(20), SetOpNode(SetKind.APPEND), Position(0.0, 1080.0)),
        Connect(Edge(NodeId(1), NodeId(3), Port.LEFT)),
        Connect(Edge(NodeId(2), NodeId(3), Port.RIGHT)),
        Connect(Edge(NodeId(3), NodeId(4), Port.ONLY)),
        Connect(Edge(NodeId(4), NodeId(5), Port.ONLY)),
        Move(NodeId(5), Position(500.0, 61.0)),
        SetNode(NodeId(8), LimitNode(20)),
        Batch(
            listOf(
                AddNode(NodeId(21), DistinctNode(), Position(640.0, 60.0)),
                Connect(Edge(NodeId(5), NodeId(21), Port.ONLY)),
            )
        ),
        Disconnect(Edge(NodeId(4), NodeId(5), Port.ONLY)),
        RemoveNode(NodeId(20)),
    )
    return undo(commands.fold(History(limit = 50)) { history, command -> edit(history, command) })
}
