package dbest.kernel.http

import dbest.features.sessions.Sessions
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShutdownTest {

    private fun app(): HttpHandler = router(Sessions())

    private fun HttpHandler.send(method: Method, path: String): Response = this(Request(method, path))

    @Test
    fun `shutdown responds before triggering the exit action`() {
        val app = app()
        val triggered = CountDownLatch(1)
        shutdownAction = { triggered.countDown() }
        try {
            val response = app.send(Method.POST, "/shutdown")
            assertEquals(Status.OK, response.status)
            assertTrue(triggered.await(2, TimeUnit.SECONDS))
        } finally {
            shutdownAction = { exitProcess(0) }
        }
    }
}
