package dbest.features.canvas

import dbest.features.canvas.graph.json
import dbest.features.canvas.history.commandOf
import dbest.features.canvas.query.captionsJson
import dbest.features.sessions.Sessions
import dbest.features.sessions.Workspace
import dbest.features.sessions.workspaceOf
import dbest.kernel.http.jsonResponse
import dbest.kernel.json.json
import dbest.kernel.json.obj
import dbest.kernel.json.parsedJson
import kotlinx.serialization.json.JsonElement
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

fun canvasRoutes(sessions: Sessions): RoutingHttpHandler = routes(
    "/sessions/{sid}/session" bind GET to { request -> viewResponse(workspaceOf(sessions, request)) },
    "/sessions/{sid}/commands" bind POST to { request ->
        val command = commandOf(parsedJson(request.bodyString()))
        editResponse(workspaceOf(sessions, request)) { state -> editCanvas(state, command) }
    },
    "/sessions/{sid}/undo" bind POST to { request -> editResponse(workspaceOf(sessions, request), ::undoCanvas) },
    "/sessions/{sid}/redo" bind POST to { request -> editResponse(workspaceOf(sessions, request), ::redoCanvas) },
)

private fun editResponse(workspace: Workspace, transition: (CanvasState) -> CanvasState): Response {
    lateinit var next: CanvasState
    while (true) {
        val previous = workspace.canvas.get()
        next = transition(previous)
        if (workspace.canvas.compareAndSet(previous, next)) break
    }
    workspace.dirty = true
    return ackResponse(ackFor(next))
}

private fun viewResponse(workspace: Workspace): Response {
    val state = workspace.canvas.get()
    val session = state.history.session
    val ack = ackFor(state)
    val body = obj(
        "revision" to json(ack.revision),
        "depth" to json(ack.depth),
        "session" to json(session),
        "captions" to captionsJson(session),
        "canUndo" to json(ack.canUndo),
        "canRedo" to json(ack.canRedo),
    )
    return jsonResponse(Status.OK, body)
}

private fun ackResponse(ack: Ack): Response =
    jsonResponse(Status.OK, ackJson(ack))

private fun ackJson(ack: Ack): JsonElement = obj(
    "revision" to json(ack.revision),
    "depth" to json(ack.depth),
    "canUndo" to json(ack.canUndo),
    "canRedo" to json(ack.canRedo),
)
