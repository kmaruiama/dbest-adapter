package dbest.features.catalog

import dbest.kernel.http.jsonResponse
import org.http4k.core.Method.GET
import org.http4k.core.Status
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

fun catalogRoutes(): RoutingHttpHandler = routes(
    "/operators" bind GET to { jsonResponse(Status.OK, catalogJson()) },
)
