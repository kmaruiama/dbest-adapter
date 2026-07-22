package dbest.features.sessions

import dbest.features.canvas.CanvasState
import dbest.features.canvas.history.History
import dbest.features.canvas.query.OpenTables
import dbest.features.canvas.query.closeTables
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class Workspace(
    val id: String,
    val canvas: AtomicReference<CanvasState>,
    val tables: OpenTables,
    var file: Path? = null,
    var name: String = "",
    var dirty: Boolean = false,
)

data class Sessions(
    val workspaces: ConcurrentHashMap<String, Workspace> = ConcurrentHashMap(),
    val engineLock: EngineLock = EngineLock(),
)

class EngineLock internal constructor(
    private val semaphore: Semaphore = Semaphore(1, true),
) {
    internal fun tryAcquire(timeout: Long, unit: TimeUnit): Boolean = semaphore.tryAcquire(timeout, unit)
    internal fun release(): Unit = semaphore.release()
}

internal class EngineLease(private val lock: EngineLock) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) lock.release()
    }
}

fun createSession(sessions: Sessions, history: History = History(), file: Path? = null, name: String = ""): Workspace {
    val id = UUID.randomUUID().toString()
    val workspace = Workspace(id, AtomicReference(CanvasState(history, 0)), OpenTables(), file, name)
    sessions.workspaces[id] = workspace
    return workspace
}

fun getSession(sessions: Sessions, id: String): Workspace? = sessions.workspaces[id]

fun listSessions(sessions: Sessions): List<Workspace> = sessions.workspaces.values.toList()

fun closeSession(sessions: Sessions, id: String): Unit {
    sessions.workspaces.remove(id)?.let { closeTables(it.tables) }
}

fun closeAllSessions(sessions: Sessions): Unit {
    for (workspace in sessions.workspaces.values) {
        closeTables(workspace.tables)
    }
    sessions.workspaces.clear()
}

internal fun acquireExclusive(lock: EngineLock): EngineLease {
    if (!lock.tryAcquire(ENGINE_WAIT_SECONDS, TimeUnit.SECONDS)) {
        throw EngineBusyException("ja existe uma query rodando; espere ela terminar")
    }
    return EngineLease(lock)
}

fun <T> runExclusive(lock: EngineLock, action: () -> T): T {
    val lease = acquireExclusive(lock)
    return lease.use { action() }
}

private const val ENGINE_WAIT_SECONDS = 120L

class EngineBusyException(message: String) : RuntimeException(message)
