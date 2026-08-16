package dbest.model

import dbest.json.field
import dbest.json.int
import dbest.json.obj
import dbest.json.objOf
import dbest.json.historyOf
import dbest.json.json
import dbest.json.jsonText
import dbest.json.parsedJson
import dbest.misc.mapCollection
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

//utils para salvar e carregar arquivos de sessao. so isso.

// v2 acrescenta "trees" (o indice de arvores do canvas) ao lado de "history"; v1 nao tinha esse
// campo. O load aceita as duas versoes e ignora "trees" na volta (eh derivado da session).
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

// escrita atomica (grava num .tmp e move por cima) compartilhada pelos arquivos de sessao e da
// biblioteca de queries, para nunca deixar um arquivo meio escrito se cair no meio.
internal fun writeAtomically(target: Path, text: String) {
    val absolute = target.toAbsolutePath()
    Files.createDirectories(absolute.parent)
    val tmp = absolute.resolveSibling(absolute.fileName.toString() + ".tmp")
    Files.writeString(tmp, text)
    try {
        Files.move(tmp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (e: AtomicMoveNotSupportedException) {
        Files.move(tmp, absolute, StandardCopyOption.REPLACE_EXISTING)
    }
}
