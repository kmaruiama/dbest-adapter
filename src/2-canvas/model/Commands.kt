package dbest.model

import dbest.misc.anyInCollection
import dbest.misc.collectionMinusItem
import dbest.misc.collectionPlusItem
import dbest.misc.existsInCollection
import dbest.misc.filterCollection
import dbest.misc.foldCollection
import dbest.misc.isEmpty
import dbest.misc.mapCollection
import dbest.misc.mapMinusKey
import dbest.misc.mapPlusEntry
import dbest.misc.reverseCollection

/* arquivo que concentra a interface de comandos e sua aplicacao na funcao apply
detalhe: nada aqui mexe diretamente com a engine de fato! A unica parte do projeto que interage com ela
esta em adapter/compile, tudo aqui sao apenas o nosso 'mock' exclusivamente feita para a view e para o user final

percebam que apply recebe uma session e devolve uma session. Nao existe um 'objeto' session que eh explicitamente
mudado e acessado, mas sim uma serie de copias sucessivas
*/
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
    // 1. a tabela nao pode existir
    is AddTable -> {
        require(!existsInCollection(command.id, session.tables), { "A tabela ${command.id.value} ja existe" })
        session.copy(tables = mapPlusEntry(command.id, command.spec, session.tables))
    }

    // 1. a tabela deve existir
    // 2. nao podem existir operadores conectados a tabela
    is RemoveTable -> {
        require(existsInCollection(command.id, session.tables), { "A tabela ${command.id.value} nao existe" })
        require(!tableIsScanned(session, command.id), { "A tabela ${command.id.value} ainda eh escaneada por um node" })
        session.copy(tables = mapMinusKey(command.id, session.tables))
    }

    // 1. o operador nao pode existir
    // 2. a tabela a qual o node quer se conectar precisa existir
    is AddNode -> {
        require(!existsInCollection(command.id, session.nodes), { "O node #${command.id.value} ja existe" })
        requireScannedTable(session, command.node)
        session.copy(
            nodes = mapPlusEntry(command.id, command.node, session.nodes),
            layout = mapPlusEntry(command.id, command.at, session.layout),
        )
    }

    // 1. o operador deve existir
    // 2. a tabela sendo conectada deve existir
    // 3. o operador nao pode ser trocado por outro, apenas ter seu conteudo reconfigurado
    //    (isso garante de graca que as arestas conectadas continuam validas: mesmas portas)
    is SetNode -> {
        require(existsInCollection(command.id, session.nodes), { "O node #${command.id.value} nao existe" })
        requireScannedTable(session, command.node)
        val current = session.nodes.getValue(command.id)
        require(operatorKind(current) == operatorKind(command.node), {
            "O node #${command.id.value} eh um ${operatorKind(current)} e nao pode virar um ${operatorKind(command.node)}"
        })
        session.copy(nodes = mapPlusEntry(command.id, command.node, session.nodes))
    }

    // 1. o operador deve existir
    is RemoveNode -> {
        require(existsInCollection(command.id, session.nodes), { "O node #${command.id.value} nao existe" })
        val edges = filterCollection(session.edges, { it.from != command.id && it.to != command.id })
        session.copy(
            nodes = mapMinusKey(command.id, session.nodes),
            edges = edges,
            layout = mapMinusKey(command.id, session.layout),
        )
    }

    // 1. o operador from deve existir
    // 2. o operador to deve existir
    // 3. um operador nao pode se retroalimentar
    // 4. x -> y so deve possuir uma conexao e nao duas, ja que isso nao faz sentido
    // 5. se o usuario tentar enviar um payload com o port de tipo ONLY para um operador JOIN
    //    o programa deve recusar
    // 6. a entrada de um operador so pode aceitar um input
    // 7. ciclos sao proibidos
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

    // 1. o edge deve existir
    is Disconnect -> {
        require(existsInCollection(command.edge, session.edges), { "Nao existe essa conexao para desconectar" })
        session.copy(edges = collectionMinusItem(command.edge, session.edges))
    }

    // 1. o operador deve existir
    is Move -> {
        require(existsInCollection(command.id, session.nodes), { "O node #${command.id.value} nao existe" })
        session.copy(layout = mapPlusEntry(command.id, command.to, session.layout))
    }

    // um fold recursivo
    is Batch -> foldCollection(session, command.commands, ::apply)
}

// serve apenas para propositos de undo e redo
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
