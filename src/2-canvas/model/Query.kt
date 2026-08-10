package dbest.model

import dbest.adapter.EngineException
import dbest.adapter.GroupBy
import dbest.adapter.LogicalKind
import dbest.adapter.Plan
import dbest.adapter.SchemaColumn
import dbest.adapter.SetKind
import dbest.adapter.agg
import dbest.adapter.alias
import dbest.adapter.append
import dbest.adapter.bilateralExistence
import dbest.adapter.collapse
import dbest.adapter.cross
import dbest.adapter.distinct
import dbest.adapter.except
import dbest.adapter.execute
import dbest.adapter.exists
import dbest.adapter.explode
import dbest.adapter.filter
import dbest.adapter.hashIndex
import dbest.adapter.intersect
import dbest.adapter.join
import dbest.adapter.limit
import dbest.adapter.logicalAnd
import dbest.adapter.logicalOr
import dbest.adapter.logicalXor
import dbest.adapter.materialize
import dbest.adapter.memoize
import dbest.adapter.project
import dbest.adapter.removeColumns
import dbest.adapter.rowNumber
import dbest.adapter.scan
import dbest.adapter.schema
import dbest.adapter.sort
import dbest.adapter.unilateralExistence
import dbest.adapter.union
import dbest.adapter.validate
import dbest.misc.anyInCollection
import dbest.misc.concatCollections
import dbest.misc.existsInCollection
import dbest.misc.filterCollection
import dbest.misc.firstInCollection
import dbest.misc.isEmpty
import dbest.misc.mapCollection
import dbest.misc.orDefault
import dbest.misc.sortCollectionBy

data class Problem(val node: NodeId, val message: String)

fun roots(session: Session): List<NodeId> {
    val sinks = filterCollection(session.nodes.keys, { id -> !anyInCollection(session.edges, { it.from == id }) })
    return sortCollectionBy(sinks, { it.value })
}


// transcreve recursivamente os nos do canvas para um plan.
// NAO CONFUNDIR COM A TRANSCRICAO DO PLAN PARA AQUILO QUE RODA DE FACTO NA ENGINE!
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
