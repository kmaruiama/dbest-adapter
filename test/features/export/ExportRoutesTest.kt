package dbest.features.export

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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportRoutesTest {

    private fun app(): HttpHandler {
        val handler = router(Sessions())
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
    fun `export downloads a CSV file for a runnable node`() {
        val app = app()
        app.command(AddTable(TableId(0), users()))
        app.command(AddNode(NodeId(0), ScanNode(TableId(0), "u"), Position(0.0, 0.0)))

        val response = app(Request(Method.GET, "/nodes/0/export?format=csv"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.header("Content-Type")!!.startsWith("text/csv"))
        assertEquals("attachment; filename=\"export.csv\"", response.header("Content-Disposition"))
        val lines = response.bodyString().trim().split("\r\n")
        assertEquals("id,name,age", lines.first())
        assertEquals(4, lines.size)
    }

    @Test
    fun `export honours format and table query params`() {
        val app = app()
        app.command(AddTable(TableId(0), users()))
        app.command(AddNode(NodeId(0), ScanNode(TableId(0), "u"), Position(0.0, 0.0)))

        val sql = app(Request(Method.GET, "/nodes/0/export?format=sql&table=people"))
        assertEquals(Status.OK, sql.status)
        assertTrue(sql.header("Content-Type")!!.startsWith("application/sql"))
        assertEquals("attachment; filename=\"people.sql\"", sql.header("Content-Disposition"))
        assertTrue("CREATE TABLE IF NOT EXISTS `people`" in sql.bodyString())
    }

    @Test
    fun `an unknown export format is a 400`() {
        val app = app()
        app.command(AddTable(TableId(0), users()))
        app.command(AddNode(NodeId(0), ScanNode(TableId(0), "u"), Position(0.0, 0.0)))

        val response = app(Request(Method.GET, "/nodes/0/export?format=pdf"))
        assertEquals(Status.BAD_REQUEST, response.status)
    }
}
