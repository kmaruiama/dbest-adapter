package dbest.features.canvas.history

import dbest.features.canvas.graph.Session
import dbest.kernel.util.collectionMinusLastItem
import dbest.kernel.util.collectionPlusItem
import dbest.kernel.util.lastInCollection
import dbest.kernel.util.takeLastItems

data class Step(val redo: Command, val undo: Command)

data class History(
    val session: Session = Session(),
    val undoStack: List<Step> = emptyList(),
    val redoStack: List<Step> = emptyList(),
    val limit: Int = 200,
)

fun edit(history: History, command: Command): History {
    val step = Step(command, invert(history.session, command))
    val undoStack = pushStep(history.undoStack, step)
    return History(
        session = apply(history.session, command),
        undoStack = takeLastItems(history.limit, undoStack),
        redoStack = emptyList(),
        limit = history.limit,
    )
}

fun undo(history: History): History = when (val step = lastInCollection(history.undoStack)) {
    null -> history
    else -> history.copy(
        session = apply(history.session, step.undo),
        undoStack = collectionMinusLastItem(history.undoStack),
        redoStack = collectionPlusItem(step, history.redoStack),
    )
}

fun redo(history: History): History = when (val step = lastInCollection(history.redoStack)) {
    null -> history
    else -> history.copy(
        session = apply(history.session, step.redo),
        undoStack = collectionPlusItem(step, history.undoStack),
        redoStack = collectionMinusLastItem(history.redoStack),
    )
}

private fun pushStep(stack: List<Step>, step: Step): List<Step> {
    val top = lastInCollection(stack)
    if (top == null || !movesSameNode(top.redo, step.redo)) {
        return collectionPlusItem(step, stack)
    }
    val merged = Step(step.redo, top.undo)
    return collectionPlusItem(merged, collectionMinusLastItem(stack))
}

private fun movesSameNode(a: Command, b: Command): Boolean {
    return a is Move && b is Move && a.id == b.id
}
