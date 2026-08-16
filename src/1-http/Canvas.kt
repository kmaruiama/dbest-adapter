package dbest.http

import dbest.adapter.SchemaColumn
import dbest.model.Command
import dbest.model.History
import dbest.model.NodeId
import dbest.model.OpenTables
import dbest.model.Problem
import dbest.model.Session
import dbest.model.edit as applyEdit
import dbest.model.exists as existsAt
import dbest.model.execute as executeAt
import dbest.model.problems as problemsOf
import dbest.model.redo as redoHistory
import dbest.model.roots as rootsOf
import dbest.model.schema as schemaAt
import dbest.model.undo as undoHistory

data class Ack(
    // revision: contador monotonico (versao/ETag), sobe a cada mutacao inclusive undo/redo.
    // depth: tamanho da pilha de undo (quantas acoes da pra desfazer) — cresce com edicoes,
    // encolhe no undo, volta a crescer no redo. Eh o numero que a view mostra pro usuario.
    val revision: Int,
    val depth: Int,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val applied: Command? = null,
)

class Canvas(initial: History = History()) : AutoCloseable {

    private val lock = Any()
    private var history = initial
    private var revision = 0
    private val tables = OpenTables()

    // snapshot imutavel para leitura fora do lock (Session eh imutavel; OpenTables eh concorrente)
    private fun current(): Pair<Int, History> = synchronized(lock) { revision to history }

    fun revision(): Int = synchronized(lock) { revision }

    fun session(): Session = current().second.session

    // history completo (session + pilhas de undo/redo) para persistir a aba em arquivo
    fun history(): History = current().second

    fun snapshot(): Ack = synchronized(lock) { ack() }

    // session + Ack sob o mesmo lock, para GET /session ser consistente
    fun view(): Pair<Session, Ack> = synchronized(lock) { history.session to ack() }

    fun edit(command: Command): Ack = synchronized(lock) {
        history = applyEdit(history, command)
        revision++
        ack()
    }

    fun undo(): Ack = synchronized(lock) {
        val step = history.undoStack.lastOrNull()
        if (step == null) return ack()
        history = undoHistory(history)
        revision++
        ack(applied = step.undo)
    }

    fun redo(): Ack = synchronized(lock) {
        val step = history.redoStack.lastOrNull()
        if (step == null) return ack()
        history = redoHistory(history)
        revision++
        ack(applied = step.redo)
    }

    // leituras derivadas: tiram um snapshot da session sob lock e rodam a engine fora dele
    fun roots(): List<NodeId> = rootsOf(session())

    fun problems(): List<Problem> = current().second.session.let { problemsOf(it, tables) }

    fun rows(root: NodeId): List<Map<String, Any?>> = executeAt(session(), root, tables)

    fun rows(root: NodeId, offset: Int, limit: Int): List<Map<String, Any?>> =
        executeAt(session(), root, tables, offset, limit)

    fun schema(root: NodeId): List<SchemaColumn> = schemaAt(session(), root, tables)

    fun exists(root: NodeId): Boolean = existsAt(session(), root, tables)

    private fun ack(applied: Command? = null): Ack =
        Ack(revision, history.undoStack.size, history.undoStack.isNotEmpty(), history.redoStack.isNotEmpty(), applied)

    override fun close() = tables.close()
}
