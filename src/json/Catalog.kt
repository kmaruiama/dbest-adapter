package dbest.json

import dbest.misc.isEmpty
import dbest.misc.mapCollection
import dbest.misc.valueUnless
import dbest.model.CATALOG
import dbest.model.CATEGORY_ORDER
import dbest.model.FIELDS
import dbest.model.FieldSpec
import dbest.model.PaletteChip
import dbest.model.VARIANT_FIELDS
import dbest.model.Widget
import dbest.model.arityOf
import dbest.model.catalogKinds
import dbest.model.isEditable
import dbest.model.operatorKind
import dbest.model.sampleOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/*
Codec do catalogo da paleta (GET /operators). So tem encoder: o catalogo eh estatico e nunca volta
do cliente, como /problems.

Nao existe mais tabela de DEFAULTS aqui: json(node) em Nodes.kt passou a emitir todo campo, entao o
cliente compara um node do canvas com o template de um chip campo a campo, sem repor nada antes.

O "fields" de cada tipo eh o que deixa o cliente montar o formulario sem conhecer operador nenhum:
ele le a lista e desenha um input por campo.
*/

private fun widgetJson(widget: Widget): JsonElement = json(widget.name.lowercase())

// um campo do formulario. options/item/of saem so quando o widget daquele campo os usa
private fun json(spec: FieldSpec): JsonElement = obj(
    "at" to json(spec.at),
    "widget" to widgetJson(spec.widget),
    "options" to valueUnless(JsonArray(mapCollection(spec.options, { json(it) })), isEmpty(spec.options)),
    "item" to if (spec.item == null) null else widgetJson(spec.item),
    "of" to valueUnless(JsonArray(mapCollection(spec.of, ::json)), isEmpty(spec.of)),
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

// os fatos por TIPO de node: aridade, os campos do formulario (ausente quando o tipo nao tem um) e
// os campos que separam uma variante da outra (ausente quando o tipo tem um simbolo so)
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

// o catalogo inteiro. Nao inclui o scan: ele nao tem chip (nasce da lista de tabelas, nao da
// paleta), e o cliente ja o trata como o unico node sem entradas.
fun catalogJson(): JsonElement = obj(
    "categories" to JsonArray(mapCollection(CATEGORY_ORDER, { json(it.wire) })),
    "types" to typesJson(),
    "operators" to JsonArray(mapCollection(CATALOG, { json(it) })),
)
