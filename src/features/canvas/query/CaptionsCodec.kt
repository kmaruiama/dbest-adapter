package dbest.features.canvas.query

import dbest.features.canvas.graph.Session
import dbest.kernel.json.json
import dbest.kernel.json.obj
import dbest.kernel.util.mapEntries
import dbest.kernel.util.transformOr
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

fun json(caption: Caption): JsonElement = obj(
    "engineClass" to transformOr(caption.engineClass, ::json, JsonNull),
    "expression" to json(caption.expression),
)

fun captionsJson(session: Session): JsonElement =
    JsonObject(mapEntries(session.nodes, { id, node -> id.value.toString() to json(caption(session, node)) }))
