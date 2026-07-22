package dbest.features.library

import dbest.features.canvas.graph.sessionOf
import dbest.features.config.requireDir
import dbest.kernel.http.NotFoundException
import dbest.kernel.http.jsonResponse
import dbest.kernel.json.field
import dbest.kernel.json.json
import dbest.kernel.json.obj
import dbest.kernel.json.objOf
import dbest.kernel.json.parsedJson
import dbest.kernel.json.string
import dbest.kernel.util.mapCollection
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes

fun libraryRoutes(): RoutingHttpHandler = routes(
    "/queries" bind GET to { queriesResponse() },
    "/queries" bind POST to { request -> saveQueryResponse(request) },
    "/queries/{qid}" bind DELETE to { request -> deleteQueryResponse(request) },
)

private fun queriesResponse(): Response {
    val library = loadLibrary(requireDir())
    return jsonResponse(Status.OK, JsonArray(mapCollection(library.queries, { queryJson(it) })))
}

private fun saveQueryResponse(request: Request): Response {
    val dir = requireDir()
    val fields = objOf(parsedJson(request.bodyString()))
    val entry = SavedQuery(
        id = UUID.randomUUID().toString(),
        name = fields.string("name"),
        createdAt = Instant.now().toString(),
        subgraph = sessionOf(fields.field("subgraph")),
    )
    saveLibrary(dir, Library(loadLibrary(dir).queries + entry))
    return jsonResponse(Status.OK, queryJson(entry))
}

private fun deleteQueryResponse(request: Request): Response {
    val dir = requireDir()
    val qid = request.path("qid") ?: throw NotFoundException("id de query ausente")
    saveLibrary(dir, Library(loadLibrary(dir).queries.filter { it.id != qid }))
    return jsonResponse(Status.OK, obj("deleted" to json(qid)))
}
