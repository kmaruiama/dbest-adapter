package dbest.kernel.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private val prettyJson = Json(builderAction = { prettyPrint = true })

fun jsonText(element: JsonElement): String = prettyJson.encodeToString(JsonElement.serializer(), element)

fun compactJsonText(element: JsonElement): String = Json.encodeToString(JsonElement.serializer(), element)

fun parsedJson(text: String): JsonElement = Json.parseToJsonElement(text)
