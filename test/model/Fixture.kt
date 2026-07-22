package dbest.model

import dbest.adapter.JoinAlgorithm
import dbest.adapter.JoinType
import dbest.adapter.SetKind
import dbest.adapter.and
import dbest.adapter.asc
import dbest.adapter.avg
import dbest.adapter.booleanColumn
import dbest.adapter.col
import dbest.adapter.count
import dbest.adapter.countAll
import dbest.adapter.countNull
import dbest.adapter.desc
import dbest.adapter.doubleColumn
import dbest.adapter.eq
import dbest.adapter.first
import dbest.adapter.floatColumn
import dbest.adapter.gt
import dbest.adapter.gte
import dbest.adapter.intColumn
import dbest.adapter.isNotNull
import dbest.adapter.isNull
import dbest.adapter.last
import dbest.adapter.longColumn
import dbest.adapter.lt
import dbest.adapter.lte
import dbest.adapter.max
import dbest.adapter.min
import dbest.adapter.neq
import dbest.adapter.on
import dbest.adapter.or
import dbest.adapter.qualified
import dbest.adapter.stringColumn
import dbest.adapter.sum

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
