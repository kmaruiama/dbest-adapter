package dbest.features.config

import dbest.kernel.dialogs.pickDirectory
import dbest.kernel.http.jsonResponse
import dbest.kernel.json.json
import dbest.kernel.json.obj
import java.nio.file.Path
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

fun configRoutes(): RoutingHttpHandler = routes(
    "/config" bind GET to { configResponse() },
    "/config/sessions-dir" bind POST to { setDirResponse() },
)

fun requireDir(): Path =
    configuredDir() ?: throw IllegalArgumentException("a pasta das sessoes ainda nao foi configurada")

private fun configResponse(): Response {
    val dir = configuredDir()
    return jsonResponse(Status.OK, obj("sessionsDir" to dir?.let { json(it.toString()) }))
}

private fun setDirResponse(): Response {
    val picked = pickDirectory() ?: return jsonResponse(Status.OK, obj())
    return jsonResponse(Status.OK, obj("sessionsDir" to json(setSessionsDir(picked).toString())))
}
