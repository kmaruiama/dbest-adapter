package dbest.features.canvas.query

import dbest.features.canvas.graph.AggNode
import dbest.features.canvas.graph.AliasNode
import dbest.features.canvas.graph.CollapseNode
import dbest.features.canvas.graph.CrossNode
import dbest.features.canvas.graph.DistinctNode
import dbest.features.canvas.graph.ExistsNode
import dbest.features.canvas.graph.ExplodeNode
import dbest.features.canvas.graph.FilterNode
import dbest.features.canvas.graph.HashIndexNode
import dbest.features.canvas.graph.JoinNode
import dbest.features.canvas.graph.LimitNode
import dbest.features.canvas.graph.LogicalOpNode
import dbest.features.canvas.graph.MaterializeNode
import dbest.features.canvas.graph.MemoizeNode
import dbest.features.canvas.graph.NodeId
import dbest.features.canvas.graph.Port
import dbest.features.canvas.graph.ProjectNode
import dbest.features.canvas.graph.RemoveColumnsNode
import dbest.features.canvas.graph.RowNumberNode
import dbest.features.canvas.graph.ScanNode
import dbest.features.canvas.graph.Session
import dbest.features.canvas.graph.SetOpNode
import dbest.features.canvas.graph.SortNode
import dbest.features.canvas.graph.inputPorts
import dbest.kernel.adapter.EngineException
import dbest.kernel.adapter.GroupBy
import dbest.kernel.adapter.LogicalKind
import dbest.kernel.adapter.Plan
import dbest.kernel.adapter.SchemaColumn
import dbest.kernel.adapter.SetKind
import dbest.kernel.adapter.agg
import dbest.kernel.adapter.alias
import dbest.kernel.adapter.append
import dbest.kernel.adapter.bilateralExistence
import dbest.kernel.adapter.collapse
import dbest.kernel.adapter.cross
import dbest.kernel.adapter.distinct
import dbest.kernel.adapter.except
import dbest.kernel.adapter.execute
import dbest.kernel.adapter.exists
import dbest.kernel.adapter.explode
import dbest.kernel.adapter.filter
import dbest.kernel.adapter.hashIndex
import dbest.kernel.adapter.intersect
import dbest.kernel.adapter.join
import dbest.kernel.adapter.limit
import dbest.kernel.adapter.logicalAnd
import dbest.kernel.adapter.logicalOr
import dbest.kernel.adapter.logicalXor
import dbest.kernel.adapter.materialize
import dbest.kernel.adapter.memoize
import dbest.kernel.adapter.project
import dbest.kernel.adapter.removeColumns
import dbest.kernel.adapter.rowNumber
import dbest.kernel.adapter.scan
import dbest.kernel.adapter.schema
import dbest.kernel.adapter.sort
import dbest.kernel.adapter.unilateralExistence
import dbest.kernel.adapter.union
import dbest.kernel.adapter.validate
import dbest.kernel.util.anyInCollection
import dbest.kernel.util.concatCollections
import dbest.kernel.util.existsInCollection
import dbest.kernel.util.filterCollection
import dbest.kernel.util.firstInCollection
import dbest.kernel.util.isEmpty
import dbest.kernel.util.mapCollection
import dbest.kernel.util.orDefault
import dbest.kernel.util.sortCollectionBy

data class Problem(val node: NodeId, val message: String)

fun roots(session: Session): List<NodeId> {
    val sinks = filterCollection(session.nodes.keys, { id -> !anyInCollection(session.edges, { it.from == id }) })
    return sortCollectionBy(sinks, { it.value })
}

fun plan(session: Session, root: NodeId, tables: OpenTables): Plan {
    val node = session.nodes[root]

    if (node == null) {
        throw EngineException.PlanError("O node #${root.value} nao existe")
    }

    fun input(port: Port): Plan {
        val edge = firstInCollection(session.edges, { it.to == root && it.port == port })
        if (edge == null) {
            throw EngineException.PlanError("O node #${root.value} esta sem sua entrada ${port.name}")
        }
        return plan(session, edge.from, tables)
    }

    return when (node) {
        is ScanNode -> scan(resolve(tables, session, node.table), node.alias)
        is FilterNode -> filter(input(Port.ONLY), node.condition)
        is ProjectNode -> project(input(Port.ONLY), *node.columns.toTypedArray())
        is RemoveColumnsNode -> removeColumns(input(Port.ONLY), *node.columns.toTypedArray(), alias = node.alias)
        is SortNode -> sort(input(Port.ONLY), *node.keys.toTypedArray())
        is DistinctNode -> distinct(input(Port.ONLY), node.hashed)
        is LimitNode -> limit(input(Port.ONLY), node.count, node.offset)
        is AliasNode -> alias(input(Port.ONLY), node.from, node.to)
        is CollapseNode -> collapse(input(Port.ONLY), node.alias)
        is ExplodeNode -> explode(input(Port.ONLY), node.column, node.delimiter)
        is RowNumberNode -> rowNumber(input(Port.ONLY), node.alias, node.column, node.start)
        is AggNode -> {
            val aggregates = node.aggregates.toTypedArray()
            val by = node.by
            if (by == null) agg(input(Port.ONLY), node.alias, *aggregates)
            else agg(input(Port.ONLY), node.alias, GroupBy(by), *aggregates, hashed = node.hashed)
        }
        is MaterializeNode -> materialize(input(Port.ONLY))
        is MemoizeNode -> memoize(input(Port.ONLY))
        is HashIndexNode -> hashIndex(input(Port.ONLY))
        is JoinNode -> join(input(Port.LEFT), input(Port.RIGHT), *node.on.toTypedArray(), type = node.type, algorithm = node.algorithm)
        is CrossNode -> cross(input(Port.LEFT), input(Port.RIGHT))
        is SetOpNode -> when (node.kind) {
            SetKind.UNION -> union(input(Port.LEFT), input(Port.RIGHT), node.hashed)
            SetKind.INTERSECT -> intersect(input(Port.LEFT), input(Port.RIGHT), node.hashed)
            SetKind.EXCEPT -> except(input(Port.LEFT), input(Port.RIGHT), node.hashed)
            SetKind.APPEND -> append(input(Port.LEFT), input(Port.RIGHT))
        }
        is LogicalOpNode -> when (node.kind) {
            LogicalKind.AND -> logicalAnd(input(Port.LEFT), input(Port.RIGHT))
            LogicalKind.OR -> logicalOr(input(Port.LEFT), input(Port.RIGHT))
            LogicalKind.XOR -> logicalXor(input(Port.LEFT), input(Port.RIGHT))
        }
        is ExistsNode ->
            if (node.bilateral) bilateralExistence(input(Port.LEFT), input(Port.RIGHT))
            else unilateralExistence(input(Port.LEFT), input(Port.RIGHT))
    }
}

fun execute(session: Session, root: NodeId, tables: OpenTables): List<Map<String, Any?>> =
    execute(plan(session, root, tables))

fun execute(session: Session, root: NodeId, tables: OpenTables, offset: Int, limit: Int): List<Map<String, Any?>> =
    execute(plan(session, root, tables), offset, limit)

fun exists(session: Session, root: NodeId, tables: OpenTables): Boolean =
    exists(plan(session, root, tables))

fun schema(session: Session, root: NodeId, tables: OpenTables): List<SchemaColumn> =
    schema(plan(session, root, tables))

fun problems(session: Session, tables: OpenTables): List<Problem> {
    val structural = buildList {
        for ((id, node) in session.nodes) {
            for (port in inputPorts(node)) {
                val connected = anyInCollection(session.edges, { it.to == id && it.port == port })
                if (!connected) {
                    add(Problem(id, "falta a entrada ${port.name}"))
                }
            }
        }
    }
    val incomplete = HashSet<NodeId>()
    for (problem in structural) {
        incomplete.add(problem.node)
    }
    val complete = filterCollection(roots(session), { root ->
        !anyInCollection(subtree(session, root), { existsInCollection(it, incomplete) })
    })
    val semantic = buildList {
        for (root in complete) {
            try {
                addAll(mapCollection(validate(plan(session, root, tables)), { Problem(root, it) }))
            } catch (e: EngineException.PlanError) {
                add(Problem(root, orDefault(e.message, "plano invalido")))
            } catch (e: IllegalArgumentException) {
                add(Problem(root, orDefault(e.message, "plano invalido")))
            } catch (e: RuntimeException) {
                add(Problem(root, orDefault(e.message, "plano invalido")))
            }
        }
    }
    return concatCollections(structural, semantic)
}

private fun subtree(session: Session, root: NodeId): Set<NodeId> {
    val seen = HashSet<NodeId>()
    val frontier = ArrayDeque(listOf(root))
    while (!isEmpty(frontier)) {
        val current = frontier.removeFirst()
        if (seen.add(current)) {
            val incoming = filterCollection(session.edges, { it.to == current })
            for (edge in incoming) {
                frontier.add(edge.from)
            }
        }
    }
    return seen
}
