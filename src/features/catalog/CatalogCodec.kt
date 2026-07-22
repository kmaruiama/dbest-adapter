package dbest.features.catalog

import dbest.features.canvas.graph.json
import dbest.features.canvas.graph.operatorKind
import dbest.kernel.json.json
import dbest.kernel.json.obj
import dbest.kernel.util.isEmpty
import dbest.kernel.util.mapCollection
import dbest.kernel.util.valueUnless
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private fun widgetJson(widget: Widget): JsonElement = json(widget.name.lowercase())

private fun json(spec: FieldSpec): JsonElement = obj(
    "at" to json(spec.at),
    "widget" to widgetJson(spec.widget),
    "options" to valueUnless(JsonArray(mapCollection(spec.options, { json(it) })), isEmpty(spec.options)),
    "item" to if (spec.item == null) null else widgetJson(spec.item),
    "of" to valueUnless(JsonArray(mapCollection(spec.of, ::json)), isEmpty(spec.of)),
    "nullable" to valueUnless(json(true), !spec.nullable),
)

private fun fieldsJson(kind: String): JsonElement? {
    val specs = FIELDS[kind]
    if (specs == null) return null
    return JsonArray(mapCollection(specs, ::json))
}

private fun json(chip: PaletteChip): JsonElement = obj(
    "key" to json(chip.key),
    "type" to json(operatorKind(chip.template)),
    "symbol" to json(chip.symbol),
    "category" to json(chip.category.wire),
    "template" to json(chip.template),
)

private fun typeJson(kind: String): JsonElement {
    val variantFields = VARIANT_FIELDS[kind]
    val variants = if (variantFields == null) null else JsonArray(mapCollection(variantFields, { json(it) }))
    return obj(
        "arity" to json(arityOf(sampleOf(kind)).name.lowercase()),
        "editable" to json(isEditable(kind)),
        "fields" to fieldsJson(kind),
        "variants" to variants,
    )
}

private fun typesJson(): JsonObject {
    val types = LinkedHashMap<String, JsonElement>()
    for (kind in catalogKinds()) {
        types.put(kind, typeJson(kind))
    }
    return JsonObject(types)
}

fun catalogJson(): JsonElement = obj(
    "categories" to JsonArray(mapCollection(CATEGORY_ORDER, { json(it.wire) })),
    "types" to typesJson(),
    "operators" to JsonArray(mapCollection(CATALOG, { json(it) })),
)
