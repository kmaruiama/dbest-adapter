package dbest.features.config

import dbest.features.sessions.Sessions
import dbest.kernel.http.router
import dbest.kernel.json.parsedJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigRoutesTest {

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

    private fun Response.field(name: String) = parsedJson(bodyString()).jsonObject.getValue(name)

    @Test
    fun `config reports the seeded sessions dir`() {
        val app = app()
        assertEquals(sessionsDir.toString(), app.send(Method.GET, "/config").field("sessionsDir").jsonPrimitive.content)
    }
}
