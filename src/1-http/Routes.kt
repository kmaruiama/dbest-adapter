package dbest.http

import dbest.export.exportFormatOf
import dbest.export.exportRows
import dbest.json.catalogJson
import dbest.json.commandOf
import dbest.json.field
import dbest.json.json
import dbest.json.obj
import dbest.json.objOf
import dbest.json.parsedJson
import dbest.json.rowsJson
import dbest.json.sessionOf
import dbest.json.string
import dbest.misc.mapCollection
import dbest.model.Library
import dbest.model.NodeId
import dbest.model.SavedQuery
import dbest.model.configuredDir
import dbest.model.load
import dbest.model.loadLibrary
import dbest.model.queryJson
import dbest.model.save
import dbest.model.saveLibrary
import dbest.model.setSessionsDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import org.http4k.core.HttpHandler
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes

// mapa de rotas do servidor. As rotas de workspace ficam sob /sessions/{sid}: cada aba eh um Canvas
// isolado, resolvido pelo {sid} da rota. /pick-file continua no topo (so devolve um caminho, nao
// depende de sessao).

fun router(sessions: Sessions): HttpHandler = accessLogFilter.then(errorFilter).then(
    routes(
        "/operators" bind GET to { jsonResponse(Status.OK, catalogJson()) },
        "/config" bind GET to { configResponse() },
        "/config/sessions-dir" bind POST to { setDirResponse() },
        "/files" bind GET to { filesResponse() },
        "/queries" bind GET to { queriesResponse() },
        "/queries" bind POST to { request -> saveQueryResponse(request) },
        "/queries/{qid}" bind DELETE to { request -> deleteQueryResponse(request) },
        "/sessions" bind GET to { sessionsResponse(sessions) },
        "/sessions" bind POST to { newSessionResponse(sessions) },
        "/sessions/open" bind POST to { request -> openSessionResponse(sessions, request) },
        "/sessions/{sid}/close" bind POST to { request -> closeSessionResponse(sessions, request) },
        "/sessions/{sid}/save" bind POST to { request -> saveSessionResponse(sessions, request) },
        "/sessions/{sid}/session" bind GET to { request -> viewResponse(workspaceOf(sessions, request)) },
        "/sessions/{sid}/commands" bind POST to { request ->
            editResponse(workspaceOf(sessions, request)) { it.edit(commandOf(parsedJson(request.bodyString()))) }
        },
        "/sessions/{sid}/undo" bind POST to { request -> editResponse(workspaceOf(sessions, request)) { it.undo() } },
        "/sessions/{sid}/redo" bind POST to { request -> editResponse(workspaceOf(sessions, request)) { it.redo() } },
        "/sessions/{sid}/roots" bind GET to { request ->
            val canvas = workspaceOf(sessions, request).canvas
            jsonResponse(Status.OK, JsonArray(mapCollection(canvas.roots(), { json(it) })))
        },
        "/sessions/{sid}/problems" bind GET to { request ->
            val canvas = workspaceOf(sessions, request).canvas
            jsonResponse(Status.OK, JsonArray(mapCollection(canvas.problems(), { json(it) })))
        },
        "/sessions/{sid}/nodes/{id}/rows" bind GET to { request ->
            // execucao real da engine: sob o run-lock global (uma query por vez em todas as abas)
            val canvas = workspaceOf(sessions, request).canvas
            sessions.runExclusive { rowsResponse(canvas, request) }
        },
        "/sessions/{sid}/nodes/{id}/schema" bind GET to { request ->
            val canvas = workspaceOf(sessions, request).canvas
            jsonResponse(Status.OK, JsonArray(mapCollection(canvas.schema(nodeId(canvas, request)), { json(it) })))
        },
        "/sessions/{sid}/nodes/{id}/exists" bind GET to { request ->
            val canvas = workspaceOf(sessions, request).canvas
            jsonResponse(Status.OK, obj("exists" to json(canvas.exists(nodeId(canvas, request)))))
        },
        "/sessions/{sid}/nodes/{id}/export" bind GET to { request ->
            val canvas = workspaceOf(sessions, request).canvas
            sessions.runExclusive { exportResponse(canvas, request) }
        },
        "/pick-file" bind POST to { pickFileResponse() },
    )
)

// resolve o workspace do {sid} da rota; 404 se a sessao nao existe
private fun workspaceOf(sessions: Sessions, request: Request): Workspace {
    val sid = request.path("sid") ?: throw NotFoundException("id de sessao ausente")
    return sessions.get(sid) ?: throw NotFoundException("a sessao '$sid' nao existe")
}

private fun workspaceJson(workspace: Workspace): JsonElement = obj(
    "sid" to json(workspace.id),
    "name" to json(workspace.name),
    "dirty" to json(workspace.dirty),
    "file" to workspace.file?.let { json(it.toString()) },
)

private fun sessionsResponse(sessions: Sessions): Response =
    jsonResponse(Status.OK, JsonArray(mapCollection(sessions.list(), { workspaceJson(it) })))

private fun newSessionResponse(sessions: Sessions): Response =
    jsonResponse(Status.OK, workspaceJson(sessions.create()))

private fun closeSessionResponse(sessions: Sessions, request: Request): Response {
    val sid = request.path("sid") ?: throw NotFoundException("id de sessao ausente")
    sessions.close(sid)
    return jsonResponse(Status.OK, obj("closed" to json(sid)))
}

// pasta das sessoes atual (ou vazio se ainda nao foi escolhida)
private fun configResponse(): Response {
    val dir = configuredDir()
    return jsonResponse(Status.OK, obj("sessionsDir" to dir?.let { json(it.toString()) }))
}

// abre o seletor nativo de pasta, persiste a escolha e devolve o caminho; obj vazio se cancelou
private fun setDirResponse(): Response {
    val picked = pickDirectory() ?: return jsonResponse(Status.OK, obj())
    return jsonResponse(Status.OK, obj("sessionsDir" to json(setSessionsDir(picked).toString())))
}

private fun requireDir(): Path =
    configuredDir() ?: throw IllegalArgumentException("a pasta das sessoes ainda nao foi configurada")

// lista os arquivos .dbest da pasta das sessoes (para o launcher oferecer "abrir salvo")
private fun filesResponse(): Response {
    val dir = requireDir()
    val files = mutableListOf<Path>()
    if (Files.isDirectory(dir)) {
        Files.newDirectoryStream(dir, "*.dbest").use { stream -> stream.forEach { files.add(it) } }
    }
    files.sort()
    val body = JsonArray(files.map { obj("name" to json(sessionName(it)), "path" to json(it.toString())) })
    return jsonResponse(Status.OK, body)
}

// carrega um arquivo .dbest numa aba nova
private fun openSessionResponse(sessions: Sessions, request: Request): Response {
    val path = Path.of(objOf(parsedJson(request.bodyString())).string("path"))
    val workspace = sessions.create(load(path.toString()), path, sessionName(path))
    return jsonResponse(Status.OK, workspaceJson(workspace))
}

// grava a aba em arquivo. body {name} opcional: com nome -> <pasta>/<nome>.dbest; sem nome usa o
// arquivo ja ligado, ou abre o dialogo nativo de salvar.
private fun saveSessionResponse(sessions: Sessions, request: Request): Response {
    val workspace = workspaceOf(sessions, request)
    val body = request.bodyString()
    val name = if (body.isBlank()) null else objOf(parsedJson(body)).let { if ("name" in it) it.string("name") else null }
    val target = saveTarget(workspace, name)
    save(workspace.canvas.history(), target.toString())
    workspace.file = target
    workspace.name = sessionName(target)
    workspace.dirty = false
    return jsonResponse(Status.OK, workspaceJson(workspace))
}

private fun saveTarget(workspace: Workspace, name: String?): Path {
    if (name != null) {
        return requireDir().resolve(fileSafe(name) + ".dbest")
    }
    workspace.file?.let { return it }
    return pickSaveFile(workspace.name.ifBlank { "sessao" } + ".dbest")
        ?: throw IllegalArgumentException("salvar cancelado")
}

// nome logico de uma sessao a partir do arquivo: o nome do arquivo sem a extensao
private fun sessionName(path: Path): String = path.fileName.toString().removeSuffix(".dbest")

// biblioteca global de queries salvas (subgrafos reusaveis entre sessoes)
private fun queriesResponse(): Response {
    val library = loadLibrary(requireDir())
    return jsonResponse(Status.OK, JsonArray(mapCollection(library.queries, { queryJson(it) })))
}

// salva um subgrafo (o node + o montante dele, com as tabelas referenciadas) com um nome
private fun saveQueryResponse(request: Request): Response {
    val dir = requireDir()
    val fields = objOf(parsedJson(request.bodyString()))
    val entry = SavedQuery(
        id = UUID.randomUUID().toString(),
        name = fields.string("name"),
        createdAt = Instant.now().toString(),
        subgraph = sessionOf(fields.field("subgraph")),
    )
    saveLibrary(dir, Library(loadLibrary(dir).queries + entry))
    return jsonResponse(Status.OK, queryJson(entry))
}

private fun deleteQueryResponse(request: Request): Response {
    val dir = requireDir()
    val qid = request.path("qid") ?: throw NotFoundException("id de query ausente")
    saveLibrary(dir, Library(loadLibrary(dir).queries.filter { it.id != qid }))
    return jsonResponse(Status.OK, obj("deleted" to json(qid)))
}

// aplica uma mutacao (comando/undo/redo), marca a aba como suja e devolve o Ack
private fun editResponse(workspace: Workspace, mutate: (Canvas) -> Ack): Response {
    val ack = mutate(workspace.canvas)
    workspace.dirty = true
    return ackResponse(ack)
}

private fun viewResponse(workspace: Workspace): Response {
    val (session, ack) = workspace.canvas.view()
    val body = obj(
        "revision" to json(ack.revision),
        "depth" to json(ack.depth),
        "session" to json(session),
        "canUndo" to json(ack.canUndo),
        "canRedo" to json(ack.canRedo),
    )
    return jsonResponse(Status.OK, body).header("ETag", ack.revision.toString())
}

private fun ackResponse(ack: Ack): Response =
    jsonResponse(Status.OK, ackJson(ack)).header("ETag", ack.revision.toString())

private fun ackJson(ack: Ack): JsonElement = obj(
    "revision" to json(ack.revision),
    "depth" to json(ack.depth),
    "canUndo" to json(ack.canUndo),
    "canRedo" to json(ack.canRedo),
    "applied" to ack.applied?.let { json(it) },
)

private fun rowsResponse(canvas: Canvas, request: Request): Response {
    val id = nodeId(canvas, request)
    val offset = request.query("offset")
    val limit = request.query("limit")
    // mede o processamento da engine (compilar + rodar + drenar); fica de fora a serializacao
    // JSON e a rede, para o cliente reportar um tempo fiel mesmo em loopback
    val start = System.nanoTime()
    val rows = if (offset != null && limit != null) {
        canvas.rows(id, intParam("offset", offset), intParam("limit", limit))
    } else {
        canvas.rows(id)
    }
    val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
    return jsonResponse(Status.OK, obj("rows" to rowsJson(rows), "elapsedMs" to json(elapsedMs)))
}

// exporta o resultado de um node como arquivo (nao eh JSON, entao vai fora dos codecs): le
// ?format= (csv por padrao) e ?table= (nome logico, "export" por padrao), monta o texto e devolve
// com Content-Disposition de download. Formato desconhecido -> IllegalArgumentException -> 400.
private fun exportResponse(canvas: Canvas, request: Request): Response {
    val id = nodeId(canvas, request)
    val format = exportFormatOf(request.query("format") ?: "csv")
    val table = request.query("table") ?: "export"
    val body = exportRows(format, table, canvas.schema(id), canvas.rows(id))
    return Response(Status.OK)
        .header("Content-Type", format.contentType)
        .header("Content-Disposition", "attachment; filename=\"${fileSafe(table)}.${format.extension}\"")
        .body(body)
}

// nome seguro para o header de download: so letras/digitos/._- ; o resto vira _ (evita quebrar
// o Content-Disposition ou injetar CRLF). O nome logico cru ainda vai pro SQL (la eh citado).
private fun fileSafe(name: String): String {
    val out = StringBuilder()
    for (c in name) {
        out.append(if (c.isLetterOrDigit() || c == '.' || c == '_' || c == '-') c else '_')
    }
    return if (out.isEmpty()) "export" else out.toString()
}

private fun nodeId(canvas: Canvas, request: Request): NodeId {
    val raw = request.path("id") ?: throw NotFoundException("id de node ausente")
    val value = raw.toIntOrNull() ?: throw IllegalArgumentException("id de node invalido: '$raw'")
    val id = NodeId(value)
    if (id !in canvas.session().nodes) {
        throw NotFoundException("O node #$value nao existe")
    }
    return id
}

private fun intParam(name: String, raw: String): Int =
    raw.toIntOrNull() ?: throw IllegalArgumentException("o parametro '$name' precisa ser um inteiro, recebi '$raw'")
