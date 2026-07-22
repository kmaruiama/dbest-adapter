package dbest.kernel.http

import dbest.kernel.json.json
import dbest.kernel.json.jsonText
import dbest.kernel.json.obj
import kotlinx.serialization.json.JsonElement
import org.http4k.core.Response
import org.http4k.core.Status

const val JSON = "application/json; charset=utf-8"

class NotFoundException(message: String) : RuntimeException(message)

fun jsonResponse(status: Status, element: JsonElement): Response =
    Response(status).header("Content-Type", JSON).body(jsonText(element))

fun errorResponse(status: Status, message: String): Response =
    jsonResponse(status, obj("error" to json(message)))
