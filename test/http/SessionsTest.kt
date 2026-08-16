package dbest.http

import dbest.json.parsedJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SessionsTest {

    private fun app() = router(Sessions())

    private fun Response.field(name: String) = parsedJson(bodyString()).jsonObject.getValue(name)

    private fun createSid(app: (Request) -> Response): String =
        app(Request(Method.POST, "/sessions")).field("sid").jsonPrimitive.content

    @Test
    fun `creating a session returns a fresh sid and lists it`() {
        val app = app()
        val sid = createSid(app)
        assertTrue(sid.isNotBlank())

        val listed = parsedJson(app(Request(Method.GET, "/sessions")).bodyString()).jsonArray
        assertEquals(1, listed.size)
        assertEquals(sid, listed.first().jsonObject.getValue("sid").jsonPrimitive.content)
    }

    @Test
    fun `two sessions are independent tabs`() {
        val app = app()
        val first = createSid(app)
        val second = createSid(app)
        assertTrue(first != second)
        assertEquals(2, parsedJson(app(Request(Method.GET, "/sessions")).bodyString()).jsonArray.size)
    }

    @Test
    fun `a request against an unknown session is a 404`() {
        val app = app()
        val response = app(Request(Method.GET, "/sessions/nope/session"))
        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `runExclusive rejects a concurrent run and frees up afterwards`() {
        val sessions = Sessions()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        // segura o lock numa thread ate liberarmos, simulando uma query em andamento
        val worker = Thread {
            sessions.runExclusive {
                started.countDown()
                release.await()
            }
        }
        worker.start()
        started.await()

        assertFailsWith<EngineBusyException> { sessions.runExclusive { } }

        release.countDown()
        worker.join()
        // liberado o lock, uma nova execucao roda normalmente
        assertEquals(42, sessions.runExclusive { 42 })
    }

    @Test
    fun `closing a session drops it from the list`() {
        val app = app()
        val sid = createSid(app)
        assertEquals(Status.OK, app(Request(Method.POST, "/sessions/$sid/close")).status)
        assertTrue(parsedJson(app(Request(Method.GET, "/sessions")).bodyString()).jsonArray.isEmpty())
    }
}
