package dbest.features.sessions

import dbest.features.config.requireDir
import dbest.kernel.dialogs.pickSaveFile
import dbest.kernel.http.NotFoundException
import dbest.kernel.http.jsonResponse
import dbest.kernel.json.json
import dbest.kernel.json.obj
import dbest.kernel.json.objOf
import dbest.kernel.json.parsedJson
import dbest.kernel.json.string
import dbest.kernel.util.fileSafe
import dbest.kernel.util.mapCollection
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes

fun sessionRoutes(sessions: Sessions): RoutingHttpHandler = routes(
    "/files" bind GET to { filesResponse() },
    "/sessions" bind GET to { sessionsResponse(sessions) },
    "/sessions" bind POST to { newSessionResponse(sessions) },
    "/sessions/open" bind POST to { request -> openSessionResponse(sessions, request) },
    "/sessions/{sid}/close" bind POST to { request -> closeSessionResponse(sessions, request) },
    "/sessions/{sid}/save" bind POST to { request -> saveSessionResponse(sessions, request) },
    "/sessions/{sid}/rename" bind POST to { request -> renameSessionResponse(sessions, request) },
)

fun workspaceOf(sessions: Sessions, request: Request): Workspace {
    val sid = request.path("sid") ?: throw NotFoundException("id de sessao ausente")
    return getSession(sessions, sid) ?: throw NotFoundException("a sessao '$sid' nao existe")
}

private fun workspaceJson(workspace: Workspace): JsonElement = obj(
    "sid" to json(workspace.id),
    "name" to json(workspace.name),
    "dirty" to json(workspace.dirty),
    "file" to workspace.file?.let { json(it.toString()) },
)

private fun sessionsResponse(sessions: Sessions): Response =
    jsonResponse(Status.OK, JsonArray(mapCollection(listSessions(sessions), { workspaceJson(it) })))

private fun newSessionResponse(sessions: Sessions): Response =
    jsonResponse(Status.OK, workspaceJson(createSession(sessions)))

private fun closeSessionResponse(sessions: Sessions, request: Request): Response {
    val sid = request.path("sid") ?: throw NotFoundException("id de sessao ausente")
    closeSession(sessions, sid)
    return jsonResponse(Status.OK, obj("closed" to json(sid)))
}

private fun filesResponse(): Response {
    val dir = requireDir()
    val files = mutableListOf<Path>()
    if (Files.isDirectory(dir)) {
        Files.newDirectoryStream(dir, "*.dbest").use { stream -> stream.forEach { files.add(it) } }
    }
    files.sort()
    val body = JsonArray(files.map { obj("name" to json(sessionName(it)), "path" to json(it.toString())) })
    return jsonResponse(Status.OK, body)
}

private fun openSessionResponse(sessions: Sessions, request: Request): Response {
    val path = Path.of(objOf(parsedJson(request.bodyString())).string("path"))
    val workspace = createSession(sessions, load(path.toString()), path, sessionName(path))
    return jsonResponse(Status.OK, workspaceJson(workspace))
}

private fun saveSessionResponse(sessions: Sessions, request: Request): Response {
    val workspace = workspaceOf(sessions, request)
    val body = request.bodyString()
    val name = if (body.isBlank()) null else objOf(parsedJson(body)).let { if ("name" in it) it.string("name") else null }
    val target = saveTarget(workspace, name)
    save(workspace.canvas.get().history, target.toString())
    workspace.file = target
    workspace.name = sessionName(target)
    workspace.dirty = false
    return jsonResponse(Status.OK, workspaceJson(workspace))
}

private fun renameSessionResponse(sessions: Sessions, request: Request): Response {
    val workspace = workspaceOf(sessions, request)
    val name = objOf(parsedJson(request.bodyString())).string("name")
    if (name.isBlank()) throw IllegalArgumentException("o nome da sessao nao pode ser vazio")
    workspace.file?.let { current ->
        val target = requireDir().resolve(fileSafe(name) + ".dbest")
        if (target != current) {
            Files.move(current, target)
            workspace.file = target
        }
    }
    workspace.name = name
    return jsonResponse(Status.OK, workspaceJson(workspace))
}

private fun saveTarget(workspace: Workspace, name: String?): Path {
    if (name != null) {
        return requireDir().resolve(fileSafe(name) + ".dbest")
    }
    workspace.file?.let { return it }
    return pickSaveFile(workspace.name.ifBlank { "sessao" } + ".dbest")
        ?: throw IllegalArgumentException("salvar cancelado")
}

private fun sessionName(path: Path): String = path.fileName.toString().removeSuffix(".dbest")
