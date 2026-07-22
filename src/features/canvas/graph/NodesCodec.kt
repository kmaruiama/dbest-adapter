package dbest.features.canvas.graph

import dbest.kernel.adapter.JoinAlgorithm
import dbest.kernel.adapter.JoinType
import dbest.kernel.adapter.LogicalKind
import dbest.kernel.adapter.SetKind
import dbest.kernel.adapter.aggOf
import dbest.kernel.adapter.conditionOf
import dbest.kernel.adapter.joinTermOf
import dbest.kernel.adapter.json
import dbest.kernel.adapter.qualifiedColOf
import dbest.kernel.adapter.sortKeyOf
import dbest.kernel.json.boolean
import dbest.kernel.json.elementsOf
import dbest.kernel.json.enum
import dbest.kernel.json.field
import dbest.kernel.json.int
import dbest.kernel.json.json
import dbest.kernel.json.obj
import dbest.kernel.json.objOf
import dbest.kernel.json.string
import dbest.kernel.json.stringOf
import dbest.kernel.json.tag
import dbest.kernel.json.wireError
import dbest.kernel.util.mapCollection
import dbest.kernel.util.transformOr
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

fun json(node: Node): JsonElement = when (node) {
    is ScanNode -> obj("@type" to json(operatorKind(node)), "table" to json(node.table), "alias" to json(node.alias))
    is FilterNode -> obj("@type" to json(operatorKind(node)), "condition" to json(node.condition))
    is ProjectNode -> obj("@type" to json(operatorKind(node)), "columns" to JsonArray(mapCollection(node.columns, ::json)))
    is SortNode -> obj("@type" to json(operatorKind(node)), "keys" to JsonArray(mapCollection(node.keys, ::json)))
    is DistinctNode -> obj("@type" to json(operatorKind(node)), "hashed" to json(node.hashed))
    is LimitNode -> obj(
        "@type" to json(operatorKind(node)),
        "count" to json(node.count),
        "offset" to json(node.offset),
    )
    is AliasNode -> obj("@type" to json(operatorKind(node)), "from" to json(node.from), "to" to json(node.to))
    is CollapseNode -> obj("@type" to json(operatorKind(node)), "alias" to json(node.alias))
    is ExplodeNode -> obj(
        "@type" to json(operatorKind(node)),
        "column" to json(node.column),
        "delimiter" to json(node.delimiter),
    )
    is RowNumberNode -> obj(
        "@type" to json(operatorKind(node)),
        "alias" to json(node.alias),
        "column" to json(node.column),
        "start" to json(node.start),
    )
    is AggNode -> obj(
        "@type" to json(operatorKind(node)),
        "alias" to json(node.alias),
        "by" to transformOr(node.by, ::json, JsonNull),
        "aggregates" to JsonArray(mapCollection(node.aggregates, ::json)),
        "hashed" to json(node.hashed),
    )
    is RemoveColumnsNode -> obj(
        "@type" to json(operatorKind(node)),
        "columns" to JsonArray(mapCollection(node.columns, ::json)),
        "alias" to json(node.alias),
    )
    is MaterializeNode -> obj("@type" to json(operatorKind(node)))
    is MemoizeNode -> obj("@type" to json(operatorKind(node)))
    is HashIndexNode -> obj("@type" to json(operatorKind(node)))
    is JoinNode -> obj(
        "@type" to json(operatorKind(node)),
        "on" to JsonArray(mapCollection(node.on, ::json)),
        "type" to json(node.type.name),
        "algorithm" to json(node.algorithm.name),
    )
    is CrossNode -> obj("@type" to json(operatorKind(node)))
    is SetOpNode -> obj(
        "@type" to json(operatorKind(node)),
        "kind" to json(node.kind.name),
        "hashed" to json(node.hashed),
    )
    is LogicalOpNode -> obj("@type" to json(operatorKind(node)), "kind" to json(node.kind.name))
    is ExistsNode -> obj("@type" to json(operatorKind(node)), "bilateral" to json(node.bilateral))
}

fun nodeOf(element: JsonElement): Node {
    val fields = objOf(element)
    return when (val tag = fields.tag()) {
        "scan" -> ScanNode(TableId(fields.int("table")), fields.string("alias"))
        "filter" -> FilterNode(conditionOf(fields.field("condition")))
        "project" -> ProjectNode(mapCollection(elementsOf(fields.field("columns")), ::stringOf))
        "sort" -> SortNode(mapCollection(elementsOf(fields.field("keys")), ::sortKeyOf))
        "distinct" -> DistinctNode(fields.boolean("hashed", default = true))
        "limit" -> LimitNode(fields.int("count"), fields.int("offset", default = 0))
        "alias" -> AliasNode(fields.string("from"), fields.string("to"))
        "collapse" -> CollapseNode(fields.string("alias"))
        "explode" -> ExplodeNode(fields.string("column"), transformOr(fields["delimiter"], ::stringOf, ","))
        "rowNumber" -> RowNumberNode(fields.string("alias"), fields.string("column"), fields.int("start", default = 1))
        "agg" -> {
            val by = fields.field("by")
            AggNode(
                fields.string("alias"),
                if (by is JsonNull) null else qualifiedColOf(by),
                mapCollection(elementsOf(fields.field("aggregates")), ::aggOf),
                fields.boolean("hashed", default = true),
            )
        }
        "removeColumns" -> RemoveColumnsNode(
            mapCollection(elementsOf(fields.field("columns")), ::stringOf),
            transformOr(fields["alias"], ::stringOf, "Projection"),
        )
        "materialize" -> MaterializeNode
        "memoize" -> MemoizeNode
        "hashIndex" -> HashIndexNode
        "join" -> JoinNode(
            mapCollection(elementsOf(fields.field("on")), ::joinTermOf),
            fields.enum("type", default = JoinType.INNER),
            fields.enum("algorithm", default = JoinAlgorithm.NESTED_LOOP),
        )
        "cross" -> CrossNode
        "setOp" -> SetOpNode(fields.enum<SetKind>("kind"), fields.boolean("hashed", default = true))
        "logicalOp" -> LogicalOpNode(fields.enum<LogicalKind>("kind"))
        "exists" -> ExistsNode(fields.boolean("bilateral", default = false))
        else -> wireError("node desconhecido '$tag'")
    }
}
