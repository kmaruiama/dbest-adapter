package dbest.features.canvas.history

import dbest.features.canvas.graph.Edge
import dbest.features.canvas.graph.Node
import dbest.features.canvas.graph.NodeId
import dbest.features.canvas.graph.Position
import dbest.features.canvas.graph.ScanNode
import dbest.features.canvas.graph.Session
import dbest.features.canvas.graph.TableId
import dbest.features.canvas.graph.TableSpec
import dbest.features.canvas.graph.inputPorts
import dbest.features.canvas.graph.operatorKind
import dbest.kernel.util.anyInCollection
import dbest.kernel.util.collectionMinusItem
import dbest.kernel.util.collectionPlusItem
import dbest.kernel.util.existsInCollection
import dbest.kernel.util.filterCollection
import dbest.kernel.util.foldCollection
import dbest.kernel.util.isEmpty
import dbest.kernel.util.mapCollection
import dbest.kernel.util.mapMinusKey
import dbest.kernel.util.mapPlusEntry
import dbest.kernel.util.reverseCollection

sealed interface Command


data class AddTable(val id: TableId, val spec: TableSpec) : Command

data class RemoveTable(val id: TableId) : Command

data class AddNode(val id: NodeId, val node: Node, val at: Position) : Command

data class SetNode(val id: NodeId, val node: Node) : Command

data class RemoveNode(val id: NodeId) : Command

data class Connect(val edge: Edge) : Command

data class Disconnect(val edge: Edge) : Command

data class Move(val id: NodeId, val to: Position) : Command

data class Batch(val commands: List<Command>) : Command

fun apply(session: Session, command: Command): Session = when (command) {
    is AddTable -> {
        require(!existsInCollection(command.id, session.tables), { "A tabela ${command.id.value} ja existe" })
        session.copy(tables = mapPlusEntry(command.id, command.spec, session.tables))
    }

    is RemoveTable -> {
        require(existsInCollection(command.id, session.tables), { "A tabela ${command.id.value} nao existe" })
        require(!tableIsScanned(session, command.id), { "A tabela ${command.id.value} ainda eh escaneada por um node" })
        session.copy(tables = mapMinusKey(command.id, session.tables))
    }

    is AddNode -> {
        require(!existsInCollection(command.id, session.nodes), { "O node #${command.id.value} ja existe" })
        requireScannedTable(session, command.node)
        session.copy(
            nodes = mapPlusEntry(command.id, command.node, session.nodes),
            layout = mapPlusEntry(command.id, command.at, session.layout),
        )
    }

    is SetNode -> {
        require(existsInCollection(command.id, session.nodes), { "O node #${command.id.value} nao existe" })
        requireScannedTable(session, command.node)
        val current = session.nodes.getValue(command.id)
        require(operatorKind(current) == operatorKind(command.node), {
            "O node #${command.id.value} eh um ${operatorKind(current)} e nao pode virar um ${operatorKind(command.node)}"
        })
        session.copy(nodes = mapPlusEntry(command.id, command.node, session.nodes))
    }

    is RemoveNode -> {
        require(existsInCollection(command.id, session.nodes), { "O node #${command.id.value} nao existe" })
        val edges = filterCollection(session.edges, { it.from != command.id && it.to != command.id })
        session.copy(
            nodes = mapMinusKey(command.id, session.nodes),
            edges = edges,
            layout = mapMinusKey(command.id, session.layout),
        )
    }

    is Connect -> {
        val edge = command.edge
        require(existsInCollection(edge.from, session.nodes), { "O node #${edge.from.value} nao existe" })
        require(existsInCollection(edge.to, session.nodes), { "O node #${edge.to.value} nao existe" })
        require(edge.from != edge.to, { "Um node nao pode alimentar a si mesmo" })
        require(!existsInCollection(edge, session.edges), { "Esses nodes ja estao conectados em ${edge.port}" })
        require(existsInCollection(edge.port, inputPorts(session.nodes.getValue(edge.to))), {
            "O node #${edge.to.value} nao aceita entrada ${edge.port}"
        })
        require(!anyInCollection(session.edges, { it.to == edge.to && it.port == edge.port }), {
            "A entrada ${edge.port} do node #${edge.to.value} ja esta conectada"
        })
        require(!feeds(session, edge.to, edge.from), { "Conectar criaria um ciclo" })
        session.copy(edges = collectionPlusItem(edge, session.edges))
    }

    is Disconnect -> {
        require(existsInCollection(command.edge, session.edges), { "Nao existe essa conexao para desconectar" })
        session.copy(edges = collectionMinusItem(command.edge, session.edges))
    }

    is Move -> {
        require(existsInCollection(command.id, session.nodes), { "O node #${command.id.value} nao existe" })
        session.copy(layout = mapPlusEntry(command.id, command.to, session.layout))
    }

    is Batch -> foldCollection(session, command.commands, ::apply)
}

fun invert(session: Session, command: Command): Command = when (command) {
    is AddTable -> RemoveTable(command.id)
    is RemoveTable -> AddTable(command.id, session.tables.getValue(command.id))
    is AddNode -> RemoveNode(command.id)
    is SetNode -> SetNode(command.id, session.nodes.getValue(command.id))
    is RemoveNode -> Batch(
        buildList {
            add(AddNode(command.id, session.nodes.getValue(command.id), session.layout.getValue(command.id)))
            val connected = filterCollection(session.edges, { it.from == command.id || it.to == command.id })
            for (edge in connected) {
                add(Connect(edge))
            }
        }
    )
    is Connect -> Disconnect(command.edge)
    is Disconnect -> Connect(command.edge)
    is Move -> Move(command.id, session.layout.getValue(command.id))
    is Batch -> {
        var state = session
        val inverses = mapCollection(command.commands, { inner ->
            val inverse = invert(state, inner)
            state = apply(state, inner)
            inverse
        })
        Batch(reverseCollection(inverses))
    }
}

private fun tableIsScanned(session: Session, table: TableId): Boolean {
    return anyInCollection(session.nodes.values, { it is ScanNode && it.table == table })
}

private fun requireScannedTable(session: Session, node: Node) {
    if (node is ScanNode) {
        require(existsInCollection(node.table, session.tables), { "A tabela ${node.table.value} nao existe" })
    }
}

private fun feeds(session: Session, producer: NodeId, target: NodeId): Boolean {
    val seen = HashSet<NodeId>()
    val frontier = ArrayDeque(listOf(producer))
    while (!isEmpty(frontier)) {
        val current = frontier.removeFirst()
        if (current == target) return true
        if (seen.add(current)) {
            val outgoing = filterCollection(session.edges, { it.from == current })
            for (edge in outgoing) {
                frontier.add(edge.to)
            }
        }
    }
    return false
}
