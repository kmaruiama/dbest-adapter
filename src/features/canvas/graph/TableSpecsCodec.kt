package dbest.features.canvas.graph

import dbest.kernel.adapter.columnOf
import dbest.kernel.adapter.json
import dbest.kernel.adapter.rowsJson
import dbest.kernel.adapter.rowsOf
import dbest.kernel.json.char
import dbest.kernel.json.elementsOf
import dbest.kernel.json.field
import dbest.kernel.json.int
import dbest.kernel.json.json
import dbest.kernel.json.obj
import dbest.kernel.json.objOf
import dbest.kernel.json.string
import dbest.kernel.json.stringOrNull
import dbest.kernel.json.tag
import dbest.kernel.json.wireError
import dbest.kernel.util.isEmpty
import dbest.kernel.util.mapCollection
import dbest.kernel.util.transformOr
import dbest.kernel.util.valueUnless
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

fun json(spec: TableSpec): JsonElement = when (spec) {
    is MemorySpec -> obj(
        "@type" to json("memory"),
        "name" to json(spec.name),
        "columns" to JsonArray(mapCollection(spec.columns, ::json)),
        "rows" to valueUnless(rowsJson(spec.rows), isEmpty(spec.rows)),
    )
    is CsvSpec -> obj(
        "@type" to json("csv"),
        "name" to json(spec.name),
        "path" to json(spec.path),
        "columns" to JsonArray(mapCollection(spec.columns, ::json)),
        "separator" to valueUnless(json(spec.separator.toString()), spec.separator == ','),
        "delimiter" to valueUnless(json(spec.delimiter.toString()), spec.delimiter == '"'),
        "headerLine" to valueUnless(json(spec.headerLine), spec.headerLine == 1),
    )
    is BTreeSpec -> obj(
        "@type" to json("btree"),
        "name" to json(spec.name),
        "path" to json(spec.path),
        "cacheSize" to valueUnless(json(spec.cacheSize), spec.cacheSize == 100_000),
    )
    is XmlSpec -> obj(
        "@type" to json("xml"),
        "name" to json(spec.name),
        "path" to json(spec.path),
        "columns" to JsonArray(mapCollection(spec.columns, ::json)),
        "rootElement" to transformOr(spec.rootElement, ::json, JsonNull),
        "recordElement" to transformOr(spec.recordElement, ::json, JsonNull),
    )
}

fun tableSpecOf(element: JsonElement): TableSpec {
    val fields = objOf(element)
    return when (val tag = fields.tag()) {
        "memory" -> MemorySpec(
            fields.string("name"),
            mapCollection(elementsOf(fields.field("columns")), ::columnOf),
            transformOr(fields["rows"], ::rowsOf, emptyList()),
        )
        "csv" -> CsvSpec(
            fields.string("name"),
            fields.string("path"),
            mapCollection(elementsOf(fields.field("columns")), ::columnOf),
            fields.char("separator", default = ','),
            fields.char("delimiter", default = '"'),
            fields.int("headerLine", default = 1),
        )
        "btree" -> BTreeSpec(
            fields.string("name"),
            fields.string("path"),
            fields.int("cacheSize", default = 100_000),
        )
        "xml" -> XmlSpec(
            fields.string("name"),
            fields.string("path"),
            mapCollection(elementsOf(fields.field("columns")), ::columnOf),
            fields.stringOrNull("rootElement"),
            fields.stringOrNull("recordElement"),
        )
        else -> wireError("spec de tabela desconhecido '$tag'")
    }
}
