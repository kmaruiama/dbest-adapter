package dbest.json

import dbest.misc.isEmpty
import dbest.misc.mapCollection
import dbest.misc.transformOr
import dbest.misc.valueUnless
import dbest.model.History
import dbest.model.Session
import dbest.model.Step
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

fun json(step: Step): JsonElement = obj("redo" to json(step.redo), "undo" to json(step.undo))

fun stepOf(element: JsonElement): Step {
    val fields = objOf(element)
    return Step(commandOf(fields.field("redo")), commandOf(fields.field("undo")))
}

fun json(history: History): JsonElement = obj(
    "session" to valueUnless(json(history.session), history.session == Session()),
    "undoStack" to valueUnless(JsonArray(mapCollection(history.undoStack, ::json)), isEmpty(history.undoStack)),
    "redoStack" to valueUnless(JsonArray(mapCollection(history.redoStack, ::json)), isEmpty(history.redoStack)),
    "limit" to valueUnless(json(history.limit), history.limit == 200),
)

fun historyOf(element: JsonElement): History {
    val fields = objOf(element)
    return History(
        session = transformOr(fields["session"], ::sessionOf, Session()),
        undoStack = transformOr(fields["undoStack"], { mapCollection(elementsOf(it), ::stepOf) }, emptyList()),
        redoStack = transformOr(fields["redoStack"], { mapCollection(elementsOf(it), ::stepOf) }, emptyList()),
        limit = fields.int("limit", default = 200),
    )
}
