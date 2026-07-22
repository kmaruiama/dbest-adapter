package dbest.features.sessions

import dbest.features.canvas.graph.Session
import dbest.features.canvas.graph.json
import dbest.features.canvas.history.History
import dbest.features.canvas.history.historyOf
import dbest.features.canvas.history.json
import dbest.features.canvas.query.trees
import dbest.kernel.json.field
import dbest.kernel.json.int
import dbest.kernel.json.json
import dbest.kernel.json.jsonText
import dbest.kernel.json.obj
import dbest.kernel.json.objOf
import dbest.kernel.json.parsedJson
import dbest.kernel.util.mapCollection
import dbest.kernel.util.writeAtomically
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import java.nio.file.Files
import java.nio.file.Path

const val SESSION_FORMAT_VERSION = 2

fun save(history: History, path: String) {
    val file = obj(
        "version" to json(SESSION_FORMAT_VERSION),
        "history" to json(history),
        "trees" to treesJson(history.session),
    )
    writeAtomically(Path.of(path), jsonText(file))
}

fun load(path: String): History {
    val file = objOf(parsedJson(Files.readString(Path.of(path))))
    val version = file.int("version")
    require(version == 1 || version == 2) {
        "A versao $version do arquivo de sessao nao eh suportada (esperado 1 ou 2)"
    }
    return historyOf(file.field("history"))
}

private fun treesJson(session: Session): JsonElement = JsonArray(
    mapCollection(trees(session), { tree ->
        obj("root" to json(tree.root), "nodes" to JsonArray(mapCollection(tree.nodes, { json(it) })))
    }),
)
