package dbest.kernel.http

import dbest.features.canvas.canvasRoutes
import dbest.features.canvas.nodeRoutes
import dbest.features.catalog.catalogRoutes
import dbest.features.config.configRoutes
import dbest.features.export.exportRoutes
import dbest.features.ingest.ingestRoutes
import dbest.features.library.libraryRoutes
import dbest.features.sessions.Sessions
import dbest.features.sessions.sessionRoutes
import org.http4k.core.HttpHandler
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.routing.routes

fun router(sessions: Sessions): HttpHandler {
    val api = routes(
        catalogRoutes(),
        configRoutes(),
        sessionRoutes(sessions),
        canvasRoutes(sessions),
        nodeRoutes(sessions),
        exportRoutes(sessions),
        libraryRoutes(),
        ingestRoutes(),
        shutdownRoute,
    )
    val frontend = frontendRoutes()
    return accessLogFilter.then(errorFilter).then { request ->
        api(request).let { if (it.status == Status.NOT_FOUND) frontend(request) else it }
    }
}

