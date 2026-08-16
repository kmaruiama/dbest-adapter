package dbest.model

// "conjunto de arvores" de uma sessao: para cada raiz do canvas, a raiz mais o fechamento de
// montante dela. Persistido no arquivo .dbest como indice derivado (a UI recomputa do grafo).

data class Tree(val root: NodeId, val nodes: List<NodeId>)

fun trees(session: Session): List<Tree> =
    roots(session).map { root -> Tree(root, upstream(session, root)) }

// ids alcancados subindo as edges (to -> from) a partir de start, start incluso, sem repetir.
// Ex.: scan 1 -> filter 2 -> sort 3; upstream(3) -> [3, 2, 1].
fun upstream(session: Session, start: NodeId): List<NodeId> {
    val seen = LinkedHashSet<NodeId>()
    val stack = ArrayDeque<NodeId>()
    stack.addLast(start)
    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        if (!seen.add(node)) continue
        for (edge in session.edges) {
            if (edge.to == node) stack.addLast(edge.from)
        }
    }
    return seen.toList()
}
