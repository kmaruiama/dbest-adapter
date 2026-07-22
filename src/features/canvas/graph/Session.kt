package dbest.features.canvas.graph

data class Session(
    val tables: Map<TableId, TableSpec> = emptyMap(),
    val nodes: Map<NodeId, Node> = emptyMap(),
    val edges: Set<Edge> = emptySet(),
    val layout: Map<NodeId, Position> = emptyMap(),
)

@JvmInline
value class NodeId(val value: Int)

@JvmInline
value class TableId(val value: Int)

data class Position(val x: Double, val y: Double)

enum class Port { ONLY, LEFT, RIGHT }

data class Edge(val from: NodeId, val to: NodeId, val port: Port)