package dbest.features.sessions

import dbest.features.canvas.graph.MemorySpec
import dbest.features.canvas.graph.NodeId
import dbest.features.canvas.graph.Position
import dbest.features.canvas.graph.ScanNode
import dbest.features.canvas.graph.TableId
import dbest.features.canvas.history.AddNode
import dbest.features.canvas.history.AddTable
import dbest.features.canvas.history.Command
import dbest.features.canvas.history.json
import dbest.features.config.setSessionsDir
import dbest.kernel.adapter.intColumn
import dbest.kernel.adapter.stringColumn
import dbest.kernel.http.router
import dbest.kernel.json.json
import dbest.kernel.json.jsonText
import dbest.kernel.json.obj
import dbest.kernel.json.parsedJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionRoutesTest {

    private lateinit var originalHome: String
    private lateinit var home: Path
    private lateinit var sessionsDir: Path

    @BeforeTest
    fun redirectHome() {
        originalHome = System.getProperty("user.home")
        home = Files.createTempDirectory("dbest-home")
        System.setProperty("user.home", home.toString())
        sessionsDir = home.resolve("sessions")
        setSessionsDir(sessionsDir)
    }

    @AfterTest
    fun restoreHome() {
        System.setProperty("user.home", originalHome)
    }

    private fun app(): HttpHandler = router(Sessions())

    private fun HttpHandler.send(method: Method, path: String, body: String? = null): Response {
        val request = Request(method, path).let { if (body != null) it.body(body) else it }
        return this(request)
    }

    private fun HttpHandler.command(sid: String, command: Command): Response =
        send(Method.POST, "/sessions/$sid/commands", jsonText(json(command)))

    private fun Response.field(name: String) = parsedJson(bodyString()).jsonObject.getValue(name)

    private fun users() = MemorySpec(
        "users",
        listOf(intColumn("id", primaryKey = true), stringColumn("name")),
        listOf(mapOf("id" to 1, "name" to "Ana")),
    )

    private fun seededSession(app: HttpHandler): String {
        val sid = app.send(Method.POST, "/sessions").field("sid").jsonPrimitive.content
        app.command(sid, AddTable(TableId(1), users()))
        app.command(sid, AddNode(NodeId(1), ScanNode(TableId(1), "u"), Position(0.0, 0.0)))
        return sid
    }

    @Test
    fun `save writes a dbest file that files lists and open reloads`() {
        val app = app()
        val sid = seededSession(app)

        val saved = app.send(Method.POST, "/sessions/$sid/save", jsonText(obj("name" to json("smoke"))))
        assertEquals(Status.OK, saved.status)
        val path = sessionsDir.resolve("smoke.dbest")
        assertTrue(Files.exists(path))

        val files = parsedJson(app.send(Method.GET, "/files").bodyString()).jsonArray
        assertTrue(files.any { it.jsonObject.getValue("name").jsonPrimitive.content == "smoke" })

        val opened = app.send(Method.POST, "/sessions/open", jsonText(obj("path" to json(path.toString()))))
        val newSid = opened.field("sid").jsonPrimitive.content
        val session = parsedJson(app.send(Method.GET, "/sessions/$newSid/session").bodyString()).jsonObject
        assertTrue("1" in session.getValue("session").jsonObject.getValue("nodes").jsonObject)
    }

    @Test
    fun `rename before saving only updates the in-memory name`() {
        val app = app()
        val sid = seededSession(app)

        val renamed = app.send(Method.POST, "/sessions/$sid/rename", jsonText(obj("name" to json("nova aba"))))
        assertEquals(Status.OK, renamed.status)
        assertEquals("nova aba", renamed.field("name").jsonPrimitive.content)
        assertTrue("file" !in parsedJson(renamed.bodyString()).jsonObject)
    }

    @Test
    fun `rename after saving moves the file instead of leaving an orphan`() {
        val app = app()
        val sid = seededSession(app)
        app.send(Method.POST, "/sessions/$sid/save", jsonText(obj("name" to json("original"))))

        val renamed = app.send(Method.POST, "/sessions/$sid/rename", jsonText(obj("name" to json("renomeada"))))
        assertEquals(Status.OK, renamed.status)
        assertEquals("renomeada", renamed.field("name").jsonPrimitive.content)

        assertTrue(Files.exists(sessionsDir.resolve("renomeada.dbest")))
        assertTrue(Files.notExists(sessionsDir.resolve("original.dbest")))
    }

    @Test
    fun `rename rejects a blank name`() {
        val app = app()
        val sid = seededSession(app)

        assertEquals(Status.BAD_REQUEST, app.send(Method.POST, "/sessions/$sid/rename", jsonText(obj("name" to json(" ")))).status)
    }
}
