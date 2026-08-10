package dbest.http

import dbest.json.json
import dbest.json.obj
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import org.http4k.core.Response
import org.http4k.core.Status

/*
Seletor de arquivo nativo aberto NA MAQUINA DO SERVIDOR. O navegador nunca expoe o caminho
real de um arquivo escolhido (so os bytes), entao o servidor — que roda na mesma maquina no
uso local — abre o dialogo nativo e devolve o caminho absoluto. Assim nenhum byte do arquivo
trafega por HTTP: a engine le o .csv / .xml / .head / .dat direto pelo path (CsvSpec / XmlSpec /
BTreeSpec). So faz sentido quando servidor e navegador estao na mesma maquina.
*/

private val SEPARATORS = listOf(',', ';', '\t', '|')

private class Picked(val path: String, val fileName: String)

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
        lower.endsWith(".xml") -> jsonResponse(
            Status.OK,
            obj("kind" to json("xml"), "path" to json(picked.path), "name" to json(name)),
        )
        else -> errorResponse(
            Status.BAD_REQUEST,
            "tipo de arquivo nao suportado: '${picked.fileName}' (use .csv, .xml, .head ou .dat)",
        )
    }
}

private fun openNativeDialog(): Picked? {
    val holder = arrayOfNulls<Picked>(1)
    EventQueue.invokeAndWait {
        val owner = Frame()
        owner.isAlwaysOnTop = true
        val dialog = FileDialog(owner, "DBest — escolher tabela (.csv / .xml / .head / .dat)", FileDialog.LOAD)
        dialog.isVisible = true
        val directory = dialog.directory
        val file = dialog.file
        dialog.dispose()
        owner.dispose()
        if (directory != null && file != null) {
            holder[0] = Picked(File(directory, file).absolutePath, file)
        }
    }
    return holder[0]
}

private fun csvBody(path: String, name: String): JsonElement {
    val (headerLine, sampleLine) = File(path).bufferedReader().use { reader ->
        (reader.readLine() ?: "") to (reader.readLine() ?: "")
    }
    val separator = SEPARATORS.maxByOrNull { candidate -> headerLine.count { it == candidate } } ?: ','
    val columns = splitLine(headerLine, separator)
    val sample = splitLine(sampleLine, separator)
    return obj(
        "kind" to json("csv"),
        "path" to json(path),
        "name" to json(name),
        "separator" to json(separator.toString()),
        "columns" to JsonArray(columns.map { json(it) }),
        "sample" to JsonArray(sample.map { json(it) }),
    )
}

private fun splitLine(line: String, separator: Char): List<String> =
    if (line.isEmpty()) emptyList() else line.split(separator).map { it.trim().trim('"') }

private fun baseName(fileName: String): String {
    val dot = fileName.lastIndexOf('.')
    return if (dot > 0) fileName.substring(0, dot) else fileName
}
