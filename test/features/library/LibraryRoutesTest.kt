package dbest.features.library

import dbest.features.canvas.graph.MemorySpec
import dbest.features.canvas.graph.NodeId
import dbest.features.canvas.graph.Position
import dbest.features.canvas.graph.ScanNode
import dbest.features.canvas.graph.Session
import dbest.features.canvas.graph.TableId
import dbest.features.canvas.graph.json
import dbest.features.config.setSessionsDir
import dbest.features.sessions.Sessions
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

class LibraryRoutesTest {

    private lateinit var originalHome: String
    private lateinit var home: Path

    @BeforeTest
    fun redirectHome() {
        originalHome = System.getProperty("user.home")
        home = Files.createTempDirectory("dbest-home")
        System.setProperty("user.home", home.toString())
        setSessionsDir(home.resolve("sessions"))
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

    private fun Response.field(name: String) = parsedJson(bodyString()).jsonObject.getValue(name)

    private fun users() = MemorySpec(
        "users",
        listOf(intColumn("id", primaryKey = true), stringColumn("name")),
        listOf(mapOf("id" to 1, "name" to "Ana")),
    )

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
