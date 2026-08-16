package dbest.model

import dbest.json.json
import dbest.json.jsonText
import dbest.json.obj
import dbest.json.objOf
import dbest.json.parsedJson
import dbest.json.string
import java.nio.file.Files
import java.nio.file.Path

/*
Config global do app, fora de qualquer sessao: por enquanto so guarda a pasta onde ficam os
arquivos .dbest. Mora em ~/.dbest/config.json — esse caminho eh fixo; o que ele guarda eh a pasta
que o usuario escolheu na primeira vez. Sem essa pasta configurada, abrir/salvar/listar sessoes
nao tem onde acontecer.
*/

private fun configFile(): Path = Path.of(System.getProperty("user.home"), ".dbest", "config.json")

// pasta das sessoes, ou null se o usuario ainda nao escolheu nenhuma
fun configuredDir(): Path? {
    val file = configFile()
    if (!Files.exists(file)) return null
    val fields = objOf(parsedJson(Files.readString(file)))
    if ("sessionsDir" !in fields) return null
    return Path.of(fields.string("sessionsDir"))
}

// grava a pasta escolhida (criando-a se preciso) e devolve o caminho absoluto persistido
fun setSessionsDir(dir: Path): Path {
    val absolute = dir.toAbsolutePath()
    Files.createDirectories(absolute)
    val file = configFile()
    Files.createDirectories(file.parent)
    Files.writeString(file, jsonText(obj("sessionsDir" to json(absolute.toString()))))
    return absolute
}
