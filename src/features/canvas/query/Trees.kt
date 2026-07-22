package dbest.features.canvas.query

import dbest.features.canvas.graph.NodeId
import dbest.features.canvas.graph.Session

data class Tree(val root: NodeId, val nodes: List<NodeId>)

fun trees(session: Session): List<Tree> =
    roots(session).map { root -> Tree(root, upstream(session, root)) }

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
