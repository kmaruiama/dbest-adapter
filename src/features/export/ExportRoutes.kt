package dbest.features.export

import dbest.features.canvas.graph.Session
import dbest.features.canvas.query.execute
import dbest.features.canvas.nodeId
import dbest.features.canvas.query.schema
import dbest.features.sessions.Sessions
import dbest.features.sessions.Workspace
import dbest.features.sessions.runExclusive
import dbest.features.sessions.workspaceOf
import dbest.kernel.util.fileSafe
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

fun exportRoutes(sessions: Sessions): RoutingHttpHandler = routes(
    "/sessions/{sid}/nodes/{id}/export" bind GET to { request ->
        val workspace = workspaceOf(sessions, request)
        runExclusive(sessions.engineLock) { exportResponse(workspace, request) }
    },
)

private fun currentSession(workspace: Workspace): Session = workspace.canvas.get().history.session

private fun exportResponse(workspace: Workspace, request: Request): Response {
    val session = currentSession(workspace)
    val id = nodeId(session, request)
    val format = exportFormatOf(request.query("format") ?: "csv")
    val table = request.query("table") ?: "export"
    val body = exportRows(format, table, schema(session, id, workspace.tables), execute(session, id, workspace.tables))
    return Response(Status.OK)
        .header("Content-Type", format.contentType)
        .header("Content-Disposition", "attachment; filename=\"${fileSafe(table)}.${format.extension}\"")
        .body(body)
}
