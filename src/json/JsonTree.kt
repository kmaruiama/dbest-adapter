package dbest.json

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

// utilitarios genericos de arvore JSON, compartilhados por todos os codecs deste pacote.

internal fun obj(vararg fields: Pair<String, JsonElement?>): JsonObject {
    val map = LinkedHashMap<String, JsonElement>()
    for ((name, value) in fields) {
        if (value != null) {
            map.put(name, value)
        }
    }
    return JsonObject(map)
}

internal fun json(value: String): JsonElement = JsonPrimitive(value)
internal fun json(value: Int): JsonElement = JsonPrimitive(value)
internal fun json(value: Double): JsonElement = JsonPrimitive(value)
internal fun json(value: Boolean): JsonElement = JsonPrimitive(value)

internal fun wireError(message: String): Nothing = throw SerializationException(message)

internal fun objOf(element: JsonElement): JsonObject =
    element as? JsonObject ?: wireError("esperava um objeto, recebi $element")

internal fun elementsOf(element: JsonElement): JsonArray =
    element as? JsonArray ?: wireError("esperava um array, recebi $element")

internal fun stringOf(element: JsonElement): String {
    val primitive = element as? JsonPrimitive
    if (primitive == null || !primitive.isString) {
        wireError("esperava uma string, recebi $element")
    }
    return primitive.content
}

internal fun intOf(element: JsonElement): Int =
    (element as? JsonPrimitive)?.intOrNull ?: wireError("esperava um int, recebi $element")

internal fun doubleOf(element: JsonElement): Double =
    (element as? JsonPrimitive)?.doubleOrNull ?: wireError("esperava um numero, recebi $element")

internal fun booleanOf(element: JsonElement): Boolean =
    (element as? JsonPrimitive)?.booleanOrNull ?: wireError("esperava um boolean, recebi $element")

internal fun JsonObject.field(name: String): JsonElement =
    this[name] ?: wireError("campo '$name' ausente")

internal fun JsonObject.tag(): String = stringOf(field("@type"))

internal fun JsonObject.string(name: String): String = stringOf(field(name))

internal fun JsonObject.stringOrNull(name: String): String? {
    val value = field(name)
    return if (value is JsonNull) null else stringOf(value)
}

internal fun JsonObject.int(name: String): Int = intOf(field(name))

internal fun JsonObject.int(name: String, default: Int): Int {
    val value = this[name]
    return if (value == null) default else intOf(value)
}

internal fun JsonObject.double(name: String): Double = doubleOf(field(name))

internal fun JsonObject.boolean(name: String): Boolean = booleanOf(field(name))

internal fun JsonObject.boolean(name: String, default: Boolean): Boolean {
    val value = this[name]
    return if (value == null) default else booleanOf(value)
}

internal fun JsonObject.char(name: String, default: Char): Char {
    val value = this[name]
    if (value == null) return default
    return stringOf(value).singleOrNull() ?: wireError("esperava um unico caractere em '$name'")
}

internal inline fun <reified T : Enum<T>> JsonObject.enum(name: String): T {
    val value = string(name)
    for (constant in enumValues<T>()) {
        if (constant.name == value) {
            return constant
        }
    }
    wireError("${T::class.simpleName} desconhecido '$value'")
}

internal inline fun <reified T : Enum<T>> JsonObject.enum(name: String, default: T): T =
    if (name in this) enum<T>(name) else default
