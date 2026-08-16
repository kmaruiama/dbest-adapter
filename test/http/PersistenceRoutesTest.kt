package dbest.http

import dbest.adapter.intColumn
import dbest.adapter.stringColumn
import dbest.json.json
import dbest.json.jsonText
import dbest.json.obj
import dbest.json.parsedJson
import dbest.model.AddNode
import dbest.model.AddTable
import dbest.model.Command
import dbest.model.MemorySpec
import dbest.model.NodeId
import dbest.model.Position
import dbest.model.ScanNode
import dbest.model.Session
import dbest.model.TableId
import dbest.model.setSessionsDir
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

/*
Integração das rotas de persistência (config/files/save/open) e da biblioteca de queries, sem GUI:
redireciona user.home para uma pasta temporária (então nunca toca no ~/.dbest real) e semeia a pasta
de sessões chamando o setSessionsDir do model direto — o dialogo nativo fica de fora.
*/
class PersistenceRoutesTest {

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

    // cria uma aba, adiciona uma tabela e um scan, e devolve o sid
    private fun seededSession(app: HttpHandler): String {
        val sid = app.send(Method.POST, "/sessions").field("sid").jsonPrimitive.content
        app.command(sid, AddTable(TableId(1), users()))
        app.command(sid, AddNode(NodeId(1), ScanNode(TableId(1), "u"), Position(0.0, 0.0)))
        return sid
    }

    @Test
    fun `config reports the seeded sessions dir`() {
        val app = app()
        assertEquals(sessionsDir.toString(), app.send(Method.GET, "/config").field("sessionsDir").jsonPrimitive.content)
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
    fun `queries library saves, lists and deletes a subgraph`() {
        val app = app()
        val subgraph = Session(
            tables = mapOf(TableId(1) to users()),
            nodes = mapOf(NodeId(1) to ScanNode(TableId(1), "u")),
            layout = mapOf(NodeId(1) to Position(0.0, 0.0)),
        )
        val body = jsonText(obj("name" to json("myquery"), "subgraph" to json(subgraph)))

        val saved = app.send(Method.POST, "/queries", body)
        assertEquals(Status.OK, saved.status)
        val id = saved.field("id").jsonPrimitive.content

        val listed = parsedJson(app.send(Method.GET, "/queries").bodyString()).jsonArray
        assertEquals(1, listed.size)
        assertEquals("myquery", listed.first().jsonObject.getValue("name").jsonPrimitive.content)

        assertEquals(Status.OK, app.send(Method.DELETE, "/queries/$id").status)
        assertTrue(parsedJson(app.send(Method.GET, "/queries").bodyString()).jsonArray.isEmpty())
    }
}
