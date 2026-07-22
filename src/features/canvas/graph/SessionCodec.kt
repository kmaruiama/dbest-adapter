package dbest.features.canvas.graph

import dbest.kernel.json.double
import dbest.kernel.json.elementsOf
import dbest.kernel.json.enum
import dbest.kernel.json.int
import dbest.kernel.json.json
import dbest.kernel.json.obj
import dbest.kernel.json.objOf
import dbest.kernel.json.wireError
import dbest.kernel.util.isEmpty
import dbest.kernel.util.mapCollection
import dbest.kernel.util.mapEntries
import dbest.kernel.util.transformOr
import dbest.kernel.util.valueUnless
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

fun json(id: NodeId): JsonElement = json(id.value)

fun json(id: TableId): JsonElement = json(id.value)

fun json(position: Position): JsonElement = obj("x" to json(position.x), "y" to json(position.y))

fun positionOf(element: JsonElement): Position {
    val fields = objOf(element)
    return Position(fields.double("x"), fields.double("y"))
}

fun json(edge: Edge): JsonElement =
    obj("from" to json(edge.from), "to" to json(edge.to), "port" to json(edge.port.name))

fun edgeOf(element: JsonElement): Edge {
    val fields = objOf(element)
    return Edge(NodeId(fields.int("from")), NodeId(fields.int("to")), fields.enum<Port>("port"))
}

fun json(session: Session): JsonElement = obj(
    "tables" to valueUnless(
        JsonObject(mapEntries(session.tables, { id, spec -> id.value.toString() to json(spec) })),
        isEmpty(session.tables),
    ),
    "nodes" to valueUnless(
        JsonObject(mapEntries(session.nodes, { id, node -> id.value.toString() to json(node) })),
        isEmpty(session.nodes),
    ),
    "edges" to valueUnless(JsonArray(mapCollection(session.edges, ::json)), isEmpty(session.edges)),
    "layout" to valueUnless(
        JsonObject(mapEntries(session.layout, { id, at -> id.value.toString() to json(at) })),
        isEmpty(session.layout),
    ),
)

fun sessionOf(element: JsonElement): Session {
    val fields = objOf(element)
    return Session(
        tables = byId(fields, "tables", ::TableId, ::tableSpecOf),
        nodes = byId(fields, "nodes", ::NodeId, ::nodeOf),
        edges = transformOr(fields["edges"], { LinkedHashSet(mapCollection(elementsOf(it), ::edgeOf)) }, emptySet()),
        layout = byId(fields, "layout", ::NodeId, ::positionOf),
    )
}

private fun <I, V> byId(fields: JsonObject, name: String, id: (Int) -> I, value: (JsonElement) -> V): Map<I, V> {
    val element = fields[name]
    if (element == null) {
        return emptyMap()
    }
    return mapEntries(objOf(element), { key, entry ->
        val number = key.toIntOrNull()
        if (number == null) {
            wireError("a chave '$key' de '$name' nao eh um id")
        }
        id(number) to value(entry)
    })
}
