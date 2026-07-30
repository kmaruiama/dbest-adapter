package dbest.http

import dbest.json.json
import dbest.json.jsonText
import dbest.json.obj
import kotlinx.serialization.json.JsonElement
import org.http4k.core.Response
import org.http4k.core.Status

// utilitarios compartilhados de resposta. Todo corpo eh JSON serializado pelos codecs de dbest.json.

const val JSON = "application/json; charset=utf-8"

// erro lancado quando um id de rota nao existe na session — vira 404 no filtro
class NotFoundException(message: String) : RuntimeException(message)

fun jsonResponse(status: Status, element: JsonElement): Response =
    Response(status).header("Content-Type", JSON).body(jsonText(element))

fun errorResponse(status: Status, message: String): Response =
    jsonResponse(status, obj("error" to json(message)))
