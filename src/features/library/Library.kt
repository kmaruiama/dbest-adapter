package dbest.features.library

import dbest.features.canvas.graph.Session
import dbest.features.canvas.graph.json
import dbest.features.canvas.graph.sessionOf
import dbest.kernel.json.elementsOf
import dbest.kernel.json.field
import dbest.kernel.json.json
import dbest.kernel.json.jsonText
import dbest.kernel.json.obj
import dbest.kernel.json.objOf
import dbest.kernel.json.parsedJson
import dbest.kernel.json.string
import dbest.kernel.util.mapCollection
import dbest.kernel.util.writeAtomically
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import java.nio.file.Files
import java.nio.file.Path

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
