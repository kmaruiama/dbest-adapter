package dbest.features.canvas

import dbest.features.canvas.graph.MemorySpec
import dbest.features.canvas.graph.NodeId
import dbest.features.canvas.graph.Position
import dbest.features.canvas.graph.ScanNode
import dbest.features.canvas.graph.TableId
import dbest.features.canvas.history.AddNode
import dbest.features.canvas.history.AddTable
import dbest.features.canvas.history.Command
import dbest.features.canvas.history.json
import dbest.features.sessions.Sessions
import dbest.kernel.adapter.intColumn
import dbest.kernel.adapter.stringColumn
import dbest.kernel.http.router
import dbest.kernel.json.json
import dbest.kernel.json.jsonText
import dbest.kernel.json.parsedJson
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeRoutesTest {

    private fun app(sessions: Sessions = Sessions()): HttpHandler {
        val handler = router(sessions)
        val created = handler(Request(Method.POST, "/sessions"))
        val sid = parsedJson(created.bodyString()).jsonObject.getValue("sid").jsonPrimitive.content
        return { request ->
            handler(request.uri(request.uri.copy(path = "/sessions/$sid" + request.uri.path)))
        }
    }

    private fun users() = MemorySpec(
        "users",
        listOf(intColumn("id", primaryKey = true), stringColumn("name"), intColumn("age")),
        listOf(
            mapOf("id" to 1, "name" to "Ana", "age" to 22),
            mapOf("id" to 2, "name" to "Bruno", "age" to 17),
            mapOf("id" to 3, "name" to "Carla", "age" to 34),
        ),
    )

    private fun HttpHandler.command(command: Command): Response =
        this(Request(Method.POST, "/commands").body(jsonText(json(command))))

    @Test
    fun `derived reads expose schema and rows from the engine`() {
        val app = app()
        app.command(AddTable(TableId(0), users()))
        app.command(AddNode(NodeId(0), ScanNode(TableId(0), "u"), Position(0.0, 0.0)))

        val schema = app(Request(Method.GET, "/nodes/0/schema"))
        assertEquals(Status.OK, schema.status)
        val columns = parsedJson(schema.bodyString()).jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content }
        assertEquals(listOf("id", "name", "age"), columns)

        val paged = app(Request(Method.GET, "/nodes/0/rows?offset=0&limit=200"))
        val pagedBody = parsedJson(paged.bodyString()).jsonObject
        assertEquals(3, pagedBody.getValue("rows").jsonArray.size)
        assertTrue(pagedBody.getValue("elapsedMs").jsonPrimitive.double >= 0.0)
        assertEquals(3, pagedBody.getValue("rows").jsonArray[0].jsonArray.size)

        val all = app(Request(Method.GET, "/nodes/0/rows"))
        assertEquals(Status.OK, all.status)
        assertEquals("application/x-ndjson; charset=utf-8", all.header("Content-Type"))
        val lines = all.bodyString().split("\n").filter { it.isNotBlank() }
        assertEquals(3, lines.size)
        assertEquals(listOf("1", "Ana", "22"), parsedJson(lines[0]).jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `closing an unpaged response early releases its engine lease from any thread`() {
        val sessions = Sessions()
        val app = app(sessions)
        app.command(AddTable(TableId(0), users()))
        app.command(AddNode(NodeId(0), ScanNode(TableId(0), "u"), Position(0.0, 0.0)))

        val response = app(Request(Method.GET, "/nodes/0/rows"))
        assertFalse(sessions.engineLock.tryAcquire(0, TimeUnit.MILLISECONDS))

        Thread { response.close() }.apply { start(); join() }

        assertTrue(sessions.engineLock.tryAcquire(0, TimeUnit.MILLISECONDS))
        sessions.engineLock.release()
    }

    @Test
    fun `a read on an unknown node is a 404`() {
        val app = app()
        val response = app(Request(Method.GET, "/nodes/7/schema"))
        assertEquals(Status.NOT_FOUND, response.status)
    }
}
