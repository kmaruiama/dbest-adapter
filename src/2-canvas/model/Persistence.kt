package dbest.model

import dbest.json.field
import dbest.json.int
import dbest.json.obj
import dbest.json.objOf
import dbest.json.historyOf
import dbest.json.json
import dbest.json.jsonText
import dbest.json.parsedJson
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

//utils para salvar e carregar arquivos de sessao. so isso.

const val SESSION_FORMAT_VERSION = 1

fun save(history: History, path: String) {
    val file = obj("version" to json(SESSION_FORMAT_VERSION), "history" to json(history))
    val target = Path.of(path).toAbsolutePath()
    Files.createDirectories(target.parent)
    val tmp = target.resolveSibling(target.fileName.toString() + ".tmp")
    Files.writeString(tmp, jsonText(file))
    try {
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (e: AtomicMoveNotSupportedException) {
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
    }
}

fun load(path: String): History {
    val file = objOf(parsedJson(Files.readString(Path.of(path))))
    val version = file.int("version")
    require(version == SESSION_FORMAT_VERSION, {
        "A versao $version do arquivo de sessao nao eh suportada (a esperada eh $SESSION_FORMAT_VERSION)"
    })
    return historyOf(file.field("history"))
}
