package dbest.kernel.http

import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.routing.ResourceLoader
import org.http4k.routing.static

private val frontendStatic = static(ResourceLoader.Classpath("public"), "js" to ContentType("text/javascript"))

private val frontendIndex: HttpHandler = static(ResourceLoader.Classpath("public"))

fun frontendRoutes(): HttpHandler = { request: Request ->
    if (request.method != GET) {
        Response(NOT_FOUND)
    } else {
        frontendStatic(request).let { response ->
            if (response.status == NOT_FOUND) frontendIndex(Request(GET, "/index.html")) else response
        }
    }
}
