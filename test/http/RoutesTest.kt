package dbest.http

import dbest.adapter.intColumn
import dbest.adapter.stringColumn
import dbest.json.commandOf
import dbest.json.json
import dbest.json.jsonText
import dbest.json.parsedJson
import dbest.model.AddNode
import dbest.model.AddTable
import dbest.model.Command
import dbest.model.MemorySpec
import dbest.model.NodeId
import dbest.model.Position
import dbest.model.ScanNode
import dbest.model.TableId
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoutesTest {

    private fun app(): HttpHandler = router(Canvas())

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

    private fun Response.field(name: String) = parsedJson(bodyString()).jsonObject.getValue(name)
    private fun Response.revision() = field("revision").jsonPrimitive.int

    @Test
    fun `a mutation returns only the new revision, never the session`() {
        val app = app()
        val response = app.command(AddTable(TableId(0), users()))

        assertEquals(Status.OK, response.status)
        assertEquals(1, response.revision())
        assertEquals("1", response.header("ETag"))
        // payload minimo: o ack nao carrega a session
        assertFalse("session" in parsedJson(response.bodyString()).jsonObject)
    }

    @Test
    fun `revisions increment and GET session reflects applied commands`() {
        val app = app()
        assertEquals(1, app.command(AddTable(TableId(0), users())).revision())
        assertEquals(2, app.command(AddNode(NodeId(0), ScanNode(TableId(0), "u"), Position(0.0, 0.0))).revision())

        val session = app(Request(Method.GET, "/session"))
        val body = parsedJson(session.bodyString()).jsonObject
        assertEquals(2, body.getValue("revision").jsonPrimitive.int)
        assertTrue(body.getValue("canUndo").jsonPrimitive.content.toBoolean())
        val sessionObj = body.getValue("session").jsonObject
        assertTrue("tables" in sessionObj && "nodes" in sessionObj)
    }

    @Test
    fun `an invalid command is a 400 carrying the model message and does not change revision`() {
        val app = app()
        app.command(AddTable(TableId(0), users()))
        // escaneia uma tabela que nao existe -> require(...) em apply
        val bad = app.command(AddNode(NodeId(0), ScanNode(TableId(99), "u"), Position(0.0, 0.0)))

        assertEquals(Status.BAD_REQUEST, bad.status)
        assertTrue("nao existe" in bad.field("error").jsonPrimitive.content)
        assertEquals(1, app(Request(Method.GET, "/session")).field("revision").jsonPrimitive.int)
    }

    @Test
    fun `undo returns the applied inverse command and redo restores state`() {
        val app = app()
        app.command(AddTable(TableId(0), users()))
        app.command(AddNode(NodeId(0), ScanNode(TableId(0), "u"), Position(0.0, 0.0)))

        val undo = app(Request(Method.POST, "/undo"))
        assertEquals(3, undo.revision())
        // o inverso de AddNode eh um RemoveNode, devolvido para o cliente reaplicar no espelho
        val applied = commandOf(undo.field("applied"))
        assertTrue(applied is dbest.model.RemoveNode && applied.id == NodeId(0))
        assertTrue(undo.field("canRedo").jsonPrimitive.content.toBoolean())

        val redo = app(Request(Method.POST, "/redo"))
        assertEquals(4, redo.revision())
    }

    @Test
    fun `derived reads expose schema and rows from the engine`() {
        val app = app()
        app.command(AddTable(TableId(0), users()))
        app.command(AddNode(NodeId(0), ScanNode(TableId(0), "u"), Position(0.0, 0.0)))

        val schema = app(Request(Method.GET, "/nodes/0/schema"))
        assertEquals(Status.OK, schema.status)
        val columns = parsedJson(schema.bodyString()).jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content }
        assertEquals(listOf("id", "name", "age"), columns)

        val rows = app(Request(Method.GET, "/nodes/0/rows"))
        assertEquals(3, parsedJson(rows.bodyString()).jsonArray.size)
    }

    @Test
    fun `a read on an unknown node is a 404`() {
        val app = app()
        val response = app(Request(Method.GET, "/nodes/7/schema"))
        assertEquals(Status.NOT_FOUND, response.status)
    }
}
