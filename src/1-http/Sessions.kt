package dbest.http

import dbest.model.History
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/*
Uma aba aberta: um Canvas (estado + history) isolado, o arquivo ao qual esta ligada (nulo enquanto
nunca foi salva) e se tem mudancas nao gravadas. name eh vazio para uma aba nova ainda sem titulo —
a UI mostra um titulo localizado nesse caso.
*/
class Workspace(
    val id: String,
    val canvas: Canvas,
    var file: Path? = null,
    var name: String = "",
    var dirty: Boolean = false,
)

/*
Gerenciador das sessoes abertas (as "abas"). Mantem varios Canvas isolados, um por aba, guardados
por um id gerado. A engine nao roda duas queries ao mesmo tempo, entao o run-lock aqui eh global:
vale para TODAS as abas de uma vez (uma query rodando serializa qualquer outra, na mesma aba ou em
outra). Fechar o servidor fecha todos os Canvas.
*/
class Sessions : AutoCloseable {

    private val workspaces = ConcurrentHashMap<String, Workspace>()
    private val engine = ReentrantLock(true)

    fun create(history: History = History(), file: Path? = null, name: String = ""): Workspace {
        val id = UUID.randomUUID().toString()
        val workspace = Workspace(id, Canvas(history), file, name)
        workspaces[id] = workspace
        return workspace
    }

    fun get(id: String): Workspace? = workspaces[id]

    fun list(): List<Workspace> = workspaces.values.toList()

    fun close(id: String) {
        workspaces.remove(id)?.canvas?.close()
    }

    // roda uma execucao da engine sob o lock global ESPERANDO a vez, em ordem de chegada: uma query
    // nao tem como ser cancelada, entao segue rodando e segurando o lock mesmo depois do cliente
    // desistir dela. O 409 sobra so para o timeout, isto eh, uma query travada.
    fun <T> runExclusive(action: () -> T): T {
        if (!engine.tryLock(ENGINE_WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw EngineBusyException("ja existe uma query rodando; espere ela terminar")
        }
        try {
            return action()
        } finally {
            engine.unlock()
        }
    }

    override fun close() {
        for (workspace in workspaces.values) {
            workspace.canvas.close()
        }
        workspaces.clear()
    }
}

// teto de espera na fila da engine; passar disso significa query travada, nao fila normal
private const val ENGINE_WAIT_SECONDS = 60L

// lancada quando a espera pela engine estoura o teto — vira 409 no filtro de erro
class EngineBusyException(message: String) : RuntimeException(message)
