package dbest.features.ingest

import dbest.kernel.dialogs.baseName
import dbest.kernel.dialogs.openNativeDialog
import dbest.kernel.http.errorResponse
import dbest.kernel.http.jsonResponse
import dbest.kernel.json.char
import dbest.kernel.json.int
import dbest.kernel.json.json
import dbest.kernel.json.obj
import dbest.kernel.json.objOf
import dbest.kernel.json.parsedJson
import dbest.kernel.json.string
import dbest.kernel.json.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

fun pickFileResponse(): Response {
    val picked = try {
        openNativeDialog()
    } catch (error: Throwable) {
        return errorResponse(
            Status.INTERNAL_SERVER_ERROR,
            "nao foi possivel abrir o seletor de arquivos no servidor: ${error.message}",
        )
    }
    if (picked == null) return jsonResponse(Status.OK, obj("kind" to json("cancelled")))

    val name = baseName(picked.fileName)
    val lower = picked.fileName.lowercase()
    return when {
        lower.endsWith(".head") -> jsonResponse(
            Status.OK,
            obj("kind" to json("head"), "path" to json(picked.path), "name" to json(name)),
        )
        lower.endsWith(".dat") -> jsonResponse(
            Status.OK,
            obj("kind" to json("dat"), "path" to json(picked.path), "name" to json(name)),
        )
        lower.endsWith(".csv") -> jsonResponse(Status.OK, csvBody(picked.path, name))
        lower.endsWith(".xml") -> jsonResponse(Status.OK, xmlBody(picked.path, name))
        else -> errorResponse(
            Status.BAD_REQUEST,
            "tipo de arquivo nao suportado: '${picked.fileName}' (use .csv, .xml, .head ou .dat)",
        )
    }
}

fun csvPreviewResponse(request: Request): Response {
    val body = objOf(parsedJson(request.bodyString()))
    val path = body.string("path")
    val headerLine = body.int("headerLine", 1)
    val separatorOverride = if ("separator" in body) body.char("separator", ',') else null
    val guess = sniffCsv(path, headerLine, separatorOverride)
    return jsonResponse(
        Status.OK,
        obj(
            "separator" to json(guess.separator.toString()),
            "headerLine" to json(guess.headerLine),
            "columns" to JsonArray(guess.columns.map { json(it) }),
            "sampleRows" to sampleRowsJson(guess.sampleRows),
        ),
    )
}

private fun csvBody(path: String, name: String): JsonElement {
    val guess = sniffCsv(path)
    return obj(
        "kind" to json("csv"),
        "path" to json(path),
        "name" to json(name),
        "separator" to json(guess.separator.toString()),
        "headerLine" to json(guess.headerLine),
        "columns" to JsonArray(guess.columns.map { json(it) }),
        "sampleRows" to sampleRowsJson(guess.sampleRows),
    )
}

private fun sampleRowsJson(rows: List<List<String?>>): JsonElement =
    JsonArray(rows.map { row -> JsonArray(row.map { cell -> if (cell == null) JsonNull else json(cell) }) })

fun xmlPreviewResponse(request: Request): Response {
    val body = objOf(parsedJson(request.bodyString()))
    val path = body.string("path")
    val rootElement = body.stringOrNull("rootElement")
    val recordElement = body.stringOrNull("recordElement")
    val guess = sniffXml(path, rootElement, recordElement)
    return jsonResponse(
        Status.OK,
        obj(
            "rootElement" to json(guess.rootElement),
            "recordElement" to json(guess.recordElement),
            "columns" to JsonArray(guess.columns.map { json(it) }),
            "sampleRows" to sampleRowsJson(guess.sampleRows),
            "totalRecords" to json(guess.totalRecords),
        ),
    )
}

private fun xmlBody(path: String, name: String): JsonElement {
    val guess = sniffXml(path)
    return obj(
        "kind" to json("xml"),
        "path" to json(path),
        "name" to json(name),
        "rootElement" to json(guess.rootElement),
        "recordElement" to json(guess.recordElement),
        "columns" to JsonArray(guess.columns.map { json(it) }),
        "sampleRows" to sampleRowsJson(guess.sampleRows),
        "totalRecords" to json(guess.totalRecords),
    )
}
