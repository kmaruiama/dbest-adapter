package dbest.model

import dbest.json.elementsOf
import dbest.json.field
import dbest.json.json
import dbest.json.jsonText
import dbest.json.obj
import dbest.json.objOf
import dbest.json.parsedJson
import dbest.json.sessionOf
import dbest.json.string
import dbest.misc.mapCollection
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import java.nio.file.Files
import java.nio.file.Path

/*
Biblioteca global de queries salvas: subgrafos (o node escolhido + o montante dele, junto das
tabelas que ele referencia) que o usuario guarda de uma sessao para colar em qualquer outra. Fica
num unico arquivo <pasta-das-sessoes>/queries.json, separado das sessoes. O subgraph eh so uma
Session menor, entao reaproveita os codecs de Session.
*/

const val LIBRARY_FORMAT_VERSION = 1

data class SavedQuery(val id: String, val name: String, val createdAt: String, val subgraph: Session)

data class Library(val queries: List<SavedQuery> = emptyList())

private fun libraryFile(dir: Path): Path = dir.resolve("queries.json")

fun loadLibrary(dir: Path): Library {
    val file = libraryFile(dir)
    if (!Files.exists(file)) return Library()
    val fields = objOf(parsedJson(Files.readString(file)))
    val entries = fields["queries"] ?: return Library()
    return Library(mapCollection(elementsOf(entries), ::savedQueryOf))
}

fun saveLibrary(dir: Path, library: Library) {
    val body = obj(
        "version" to json(LIBRARY_FORMAT_VERSION),
        "queries" to JsonArray(mapCollection(library.queries, ::queryJson)),
    )
    writeAtomically(libraryFile(dir), jsonText(body))
}

fun queryJson(query: SavedQuery): JsonElement = obj(
    "id" to json(query.id),
    "name" to json(query.name),
    "createdAt" to json(query.createdAt),
    "subgraph" to json(query.subgraph),
)

private fun savedQueryOf(element: JsonElement): SavedQuery {
    val fields = objOf(element)
    return SavedQuery(
        fields.string("id"),
        fields.string("name"),
        fields.string("createdAt"),
        sessionOf(fields.field("subgraph")),
    )
}
