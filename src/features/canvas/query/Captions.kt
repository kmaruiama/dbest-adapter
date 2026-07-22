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
import dbest.features.canvas.graph.Node
import dbest.features.canvas.graph.ProjectNode
import dbest.features.canvas.graph.RemoveColumnsNode
import dbest.features.canvas.graph.RowNumberNode
import dbest.features.canvas.graph.ScanNode
import dbest.features.canvas.graph.Session
import dbest.features.canvas.graph.SetOpNode
import dbest.features.canvas.graph.SortNode
import dbest.features.canvas.graph.TableId
import dbest.kernel.adapter.ALIAS_CLASS
import dbest.kernel.adapter.And
import dbest.kernel.adapter.COLLAPSE_CLASS
import dbest.kernel.adapter.CROSS_CLASS
import dbest.kernel.adapter.ColumnRef
import dbest.kernel.adapter.CompareOp
import dbest.kernel.adapter.Comparison
import dbest.kernel.adapter.Condition
import dbest.kernel.adapter.EXISTS_CLASS
import dbest.kernel.adapter.EXPLODE_CLASS
import dbest.kernel.adapter.FILTER_CLASS
import dbest.kernel.adapter.HASH_INDEX_CLASS
import dbest.kernel.adapter.IsNotNull
import dbest.kernel.adapter.IsNull
import dbest.kernel.adapter.LIMIT_CLASS
import dbest.kernel.adapter.MATERIALIZE_CLASS
import dbest.kernel.adapter.MEMOIZE_CLASS
import dbest.kernel.adapter.Or
import dbest.kernel.adapter.PROJECT_CLASS
import dbest.kernel.adapter.QualifiedCol
import dbest.kernel.adapter.REMOVE_COLUMNS_CLASS
import dbest.kernel.adapter.ROW_NUMBER_CLASS
import dbest.kernel.adapter.SCAN_CLASS
import dbest.kernel.adapter.SORT_CLASS
import dbest.kernel.adapter.aggClass
import dbest.kernel.adapter.distinctClass
import dbest.kernel.adapter.joinClass
import dbest.kernel.adapter.logicalClass
import dbest.kernel.adapter.setClass
import dbest.kernel.util.mapCollection
import dbest.kernel.util.transformOr

data class Caption(val engineClass: String?, val expression: String)

fun caption(session: Session, node: Node): Caption =
    Caption(engineClassOf(node), expressionOf(session, node))

fun engineClassOf(node: Node): String? = when (node) {
    is ScanNode -> SCAN_CLASS
    is FilterNode -> FILTER_CLASS
    is ProjectNode -> PROJECT_CLASS
    is SortNode -> SORT_CLASS
    is DistinctNode -> distinctClass(node.hashed)
    is LimitNode -> LIMIT_CLASS
    is AliasNode -> ALIAS_CLASS
    is CollapseNode -> COLLAPSE_CLASS
    is ExplodeNode -> EXPLODE_CLASS
    is RowNumberNode -> ROW_NUMBER_CLASS
    is AggNode -> aggClass(node.by != null, node.hashed)
    is RemoveColumnsNode -> REMOVE_COLUMNS_CLASS
    is MaterializeNode -> MATERIALIZE_CLASS
    is MemoizeNode -> MEMOIZE_CLASS
    is HashIndexNode -> HASH_INDEX_CLASS
    is JoinNode -> joinClass(node.type, node.algorithm)
    is CrossNode -> CROSS_CLASS
    is SetOpNode -> setClass(node.kind, node.hashed)
    is LogicalOpNode -> logicalClass(node.kind)
    is ExistsNode -> EXISTS_CLASS
}

fun expressionOf(session: Session, node: Node): String = when (node) {
    is ScanNode -> "${tableName(session, node.table)} as ${node.alias}"
    is FilterNode -> conditionText(node.condition)
    is ProjectNode -> joined(node.columns)
    is SortNode -> joined(mapCollection(node.keys, { "${it.column} ${if (it.ascending) "ASC" else "DESC"}" }))
    is DistinctNode -> ""
    is LimitNode -> if (node.offset == 0) node.count.toString() else "${node.count}, ${node.offset}"
    is AliasNode -> "${node.from} → ${node.to}"
    is CollapseNode -> node.alias
    is ExplodeNode -> node.column
    is RowNumberNode -> "${node.alias} from ${node.start}"
    is AggNode -> aggregateText(node)
    is RemoveColumnsNode -> joined(node.columns)
    is MaterializeNode -> ""
    is MemoizeNode -> ""
    is HashIndexNode -> ""
    is JoinNode -> joined(mapCollection(node.on, { "${columnText(it.left)} = ${columnText(it.right)}" }))
    is CrossNode -> ""
    is SetOpNode -> node.kind.name
    is LogicalOpNode -> node.kind.name
    is ExistsNode -> ""
}

private fun tableName(session: Session, table: TableId): String =
    transformOr(session.tables[table], { it.name }, "")

private fun joined(parts: List<String>): String = parts.joinToString(", ")

private fun columnText(column: QualifiedCol): String = "${column.source}.${column.column}"

private fun columnText(column: ColumnRef): String =
    if (column.source == null) column.name else "${column.source}.${column.name}"

private fun aggregateText(node: AggNode): String {
    val aggregates: String = joined(
        mapCollection(node.aggregates, { "${it.function.name.lowercase()}(${it.column})" }),
    )
    val by: QualifiedCol? = node.by
    return if (by == null) aggregates else "${columnText(by)}: $aggregates"
}

private fun conditionText(condition: Condition): String = when (condition) {
    is Comparison -> "${columnText(condition.left)} ${operatorText(condition.op)} ${literalText(condition.right)}"
    is IsNull -> "${columnText(condition.column)} IS NULL"
    is IsNotNull -> "${columnText(condition.column)} IS NOT NULL"
    is And -> mapCollection(condition.conditions, ::nestedText).joinToString(" AND ")
    is Or -> mapCollection(condition.conditions, ::nestedText).joinToString(" OR ")
}

private fun nestedText(condition: Condition): String = when (condition) {
    is And -> "(${conditionText(condition)})"
    is Or -> "(${conditionText(condition)})"
    is Comparison -> conditionText(condition)
    is IsNull -> conditionText(condition)
    is IsNotNull -> conditionText(condition)
}

private fun operatorText(op: CompareOp): String = when (op) {
    CompareOp.EQ -> "="
    CompareOp.NEQ -> "≠"
    CompareOp.GT -> ">"
    CompareOp.GTE -> "≥"
    CompareOp.LT -> "<"
    CompareOp.LTE -> "≤"
}

private fun literalText(value: Any): String = when (value) {
    is ColumnRef -> columnText(value)
    is String -> "'$value'"
    else -> value.toString()
}
