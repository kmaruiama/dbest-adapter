package dbest.kernel.http

import dbest.kernel.json.json
import dbest.kernel.json.obj
import kotlin.system.exitProcess
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

internal var shutdownAction: () -> Unit = { exitProcess(0) }

val shutdownRoute: RoutingHttpHandler = routes(
    "/shutdown" bind POST to { shutdownResponse() },
)

private fun shutdownResponse(): Response {
    Thread {
        Thread.sleep(SHUTDOWN_DELAY_MS)
        shutdownAction()
    }.apply { isDaemon = true }.start()
    return jsonResponse(Status.OK, obj("shuttingDown" to json(true)))
}

private const val SHUTDOWN_DELAY_MS = 300L
