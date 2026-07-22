package dbest.features.ingest

import org.http4k.core.Method.POST
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

fun ingestRoutes(): RoutingHttpHandler = routes(
    "/pick-file" bind POST to { pickFileResponse() },
    "/csv-preview" bind POST to { request -> csvPreviewResponse(request) },
    "/xml-preview" bind POST to { request -> xmlPreviewResponse(request) },
)
