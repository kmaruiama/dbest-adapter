package dbest.http

import dbest.json.commandOf
import dbest.json.json
import dbest.json.obj
import dbest.json.parsedJson
import dbest.json.rowsJson
import dbest.misc.mapCollection
import dbest.model.NodeId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes

// mapa de rotas do servidor

fun router(canvas: Canvas): HttpHandler = errorFilter.then(
    routes(
        "/session" bind GET to { sessionResponse(canvas) },
        "/commands" bind POST to { request -> ackResponse(canvas.edit(commandOf(parsedJson(request.bodyString())))) },
        "/undo" bind POST to { ackResponse(canvas.undo()) },
        "/redo" bind POST to { ackResponse(canvas.redo()) },
        "/roots" bind GET to { jsonResponse(Status.OK, JsonArray(mapCollection(canvas.roots(), { json(it) }))) },
        "/problems" bind GET to { jsonResponse(Status.OK, JsonArray(mapCollection(canvas.problems(), { json(it) }))) },
        "/nodes/{id}/rows" bind GET to { request -> rowsResponse(canvas, request) },
        "/nodes/{id}/schema" bind GET to { request ->
            jsonResponse(Status.OK, JsonArray(mapCollection(canvas.schema(nodeId(canvas, request)), { json(it) })))
        },
        "/nodes/{id}/exists" bind GET to { request ->
            jsonResponse(Status.OK, obj("exists" to json(canvas.exists(nodeId(canvas, request)))))
        },
    )
)

private fun sessionResponse(canvas: Canvas): Response {
    val (session, ack) = canvas.view()
    val body = obj(
        "revision" to json(ack.revision),
        "session" to json(session),
        "canUndo" to json(ack.canUndo),
        "canRedo" to json(ack.canRedo),
    )
    return jsonResponse(Status.OK, body).header("ETag", ack.revision.toString())
}

private fun ackResponse(ack: Ack): Response =
    jsonResponse(Status.OK, ackJson(ack)).header("ETag", ack.revision.toString())

private fun ackJson(ack: Ack): JsonElement = obj(
    "revision" to json(ack.revision),
    "canUndo" to json(ack.canUndo),
    "canRedo" to json(ack.canRedo),
    "applied" to ack.applied?.let { json(it) },
)

private fun rowsResponse(canvas: Canvas, request: Request): Response {
    val id = nodeId(canvas, request)
    val offset = request.query("offset")
    val limit = request.query("limit")
    val rows = if (offset != null && limit != null) {
        canvas.rows(id, intParam("offset", offset), intParam("limit", limit))
    } else {
        canvas.rows(id)
    }
    return jsonResponse(Status.OK, rowsJson(rows))
}

private fun nodeId(canvas: Canvas, request: Request): NodeId {
    val raw = request.path("id") ?: throw NotFoundException("id de node ausente")
    val value = raw.toIntOrNull() ?: throw IllegalArgumentException("id de node invalido: '$raw'")
    val id = NodeId(value)
    if (id !in canvas.session().nodes) {
        throw NotFoundException("O node #$value nao existe")
    }
    return id
}

private fun intParam(name: String, raw: String): Int =
    raw.toIntOrNull() ?: throw IllegalArgumentException("o parametro '$name' precisa ser um inteiro, recebi '$raw'")
