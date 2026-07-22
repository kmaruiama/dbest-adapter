package dbest.features.config

import dbest.kernel.json.json
import dbest.kernel.json.jsonText
import dbest.kernel.json.obj
import dbest.kernel.json.objOf
import dbest.kernel.json.parsedJson
import dbest.kernel.json.string
import java.nio.file.Files
import java.nio.file.Path

fun configuredDir(): Path? {
    val file = configFile()
    if (!Files.exists(file)) return null
    val fields = objOf(parsedJson(Files.readString(file)))
    if ("sessionsDir" !in fields) return null
    return Path.of(fields.string("sessionsDir"))
}

fun setSessionsDir(dir: Path): Path {
    val absolute = dir.toAbsolutePath()
    Files.createDirectories(absolute)
    val file = configFile()
    Files.createDirectories(file.parent)
    Files.writeString(file, jsonText(obj("sessionsDir" to json(absolute.toString()))))
    return absolute
}

private fun configFile(): Path = Path.of(System.getProperty("user.home"), ".dbest", "config.json")
