package dbest.json

import dbest.adapter.Agg
import dbest.adapter.AggFunction
import dbest.adapter.And
import dbest.adapter.Column
import dbest.adapter.ColumnRef
import dbest.adapter.ColumnType
import dbest.adapter.CompareOp
import dbest.adapter.Comparison
import dbest.adapter.Condition
import dbest.adapter.EngineException
import dbest.adapter.IsNotNull
import dbest.adapter.IsNull
import dbest.adapter.JoinTerm
import dbest.adapter.Or
import dbest.adapter.QualifiedCol
import dbest.adapter.SchemaColumn
import dbest.adapter.SortKey
import dbest.adapter.col
import dbest.misc.mapCollection
import dbest.misc.mapEntries
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

// codec do vocabulario de consulta do adapter — o formato de fio dos tipos de
// dbest.adapter (literais, referencias de coluna, condicoes, termos de plano, colunas).

// literais

fun literalJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is Int -> obj("int" to json(value))
    is Long -> obj("long" to JsonPrimitive(value))
    is Float -> obj("float" to JsonPrimitive(value))
    is Double -> obj("double" to json(value))
    is Boolean -> obj("bool" to json(value))
    is String -> obj("str" to json(value))
    is ColumnRef -> obj("ref" to json(if (value.source == null) value.name else "${value.source}.${value.name}"))
    else -> throw EngineException.PlanError("${value.javaClass.simpleName} nao pode ser serializado como literal")
}

fun literalOf(element: JsonElement): Any? {
    if (element is JsonNull) return null
    val entry = (element as? JsonObject)?.entries?.singleOrNull()
        ?: throw EngineException.PlanError("literal malformado: $element")
    val primitive = entry.value as? JsonPrimitive
        ?: throw EngineException.PlanError("literal malformado: $element")
    return when (entry.key) {
        "int" -> primitive.intOrNull ?: throw EngineException.PlanError("literal malformado: $element")
        "long" -> primitive.content.toLongOrNull() ?: throw EngineException.PlanError("literal malformado: $element")
        "float" -> primitive.content.toFloatOrNull() ?: throw EngineException.PlanError("literal malformado: $element")
        "double" -> primitive.doubleOrNull ?: throw EngineException.PlanError("literal malformado: $element")
        "bool" -> primitive.booleanOrNull ?: throw EngineException.PlanError("literal malformado: $element")
        "str" -> primitive.content
        "ref" -> col(primitive.content)
        else -> throw EngineException.PlanError("tag de literal desconhecida '${entry.key}'")
    }
}

fun rowJson(row: Map<String, Any?>): JsonElement =
    JsonObject(mapEntries(row) { key, value -> key to literalJson(value) })

fun rowsJson(rows: List<Map<String, Any?>>): JsonElement = JsonArray(mapCollection(rows, ::rowJson))

fun rowOf(element: JsonElement): Map<String, Any?> =
    mapEntries(objOf(element)) { key, value -> key to literalOf(value) }

fun rowsOf(element: JsonElement): List<Map<String, Any?>> = mapCollection(elementsOf(element), ::rowOf)

// referencias de coluna

fun json(ref: ColumnRef): JsonElement {
    val source = if (ref.source == null) JsonNull else json(ref.source)
    return obj("source" to source, "name" to json(ref.name))
}

fun columnRefOf(element: JsonElement): ColumnRef {
    val fields = objOf(element)
    return ColumnRef(fields.stringOrNull("source"), fields.string("name"))
}

fun json(column: QualifiedCol): JsonElement =
    obj("source" to json(column.source), "column" to json(column.column))

fun qualifiedColOf(element: JsonElement): QualifiedCol {
    val fields = objOf(element)
    return QualifiedCol(fields.string("source"), fields.string("column"))
}

// condicoes

fun json(condition: Condition): JsonElement = when (condition) {
    is Comparison -> obj(
        "@type" to json("cmp"),
        "left" to json(condition.left),
        "op" to json(condition.op.name),
        "right" to literalJson(condition.right),
    )
    is IsNull -> obj("@type" to json("isNull"), "column" to json(condition.column))
    is IsNotNull -> obj("@type" to json("isNotNull"), "column" to json(condition.column))
    is And -> obj("@type" to json("and"), "conditions" to JsonArray(mapCollection(condition.conditions, ::json)))
    is Or -> obj("@type" to json("or"), "conditions" to JsonArray(mapCollection(condition.conditions, ::json)))
}

fun conditionOf(element: JsonElement): Condition {
    val fields = objOf(element)
    return when (val tag = fields.tag()) {
        "cmp" -> Comparison(
            columnRefOf(fields.field("left")),
            fields.enum<CompareOp>("op"),
            literalOf(fields.field("right"))
                ?: throw EngineException.PlanError("o literal de uma comparacao nao pode ser null"),
        )
        "isNull" -> IsNull(columnRefOf(fields.field("column")))
        "isNotNull" -> IsNotNull(columnRefOf(fields.field("column")))
        "and" -> And(mapCollection(elementsOf(fields.field("conditions")), ::conditionOf))
        "or" -> Or(mapCollection(elementsOf(fields.field("conditions")), ::conditionOf))
        else -> wireError("condicao desconhecida '$tag'")
    }
}

// termos de plano

fun json(key: SortKey): JsonElement =
    obj("column" to json(key.column), "ascending" to json(key.ascending))

fun sortKeyOf(element: JsonElement): SortKey {
    val fields = objOf(element)
    return SortKey(fields.string("column"), fields.boolean("ascending"))
}

fun json(term: JoinTerm): JsonElement =
    obj("left" to json(term.left), "right" to json(term.right))

fun joinTermOf(element: JsonElement): JoinTerm {
    val fields = objOf(element)
    return JoinTerm(qualifiedColOf(fields.field("left")), qualifiedColOf(fields.field("right")))
}

fun json(aggregate: Agg): JsonElement =
    obj("column" to json(aggregate.column), "function" to json(aggregate.function.name))

fun aggOf(element: JsonElement): Agg {
    val fields = objOf(element)
    return Agg(fields.string("column"), fields.enum<AggFunction>("function"))
}

// colunas e schema

fun json(column: Column): JsonElement = obj(
    "name" to json(column.name),
    "type" to json(column.type.name),
    "primaryKey" to json(column.primaryKey),
    "nullable" to json(column.nullable),
)

fun columnOf(element: JsonElement): Column {
    val fields = objOf(element)
    return Column(
        fields.string("name"),
        fields.enum<ColumnType>("type"),
        fields.boolean("primaryKey"),
        fields.boolean("nullable"),
    )
}

fun json(column: SchemaColumn): JsonElement = obj(
    "source" to json(column.source),
    "name" to json(column.name),
    "type" to json(column.type),
    "primaryKey" to json(column.primaryKey),
)
