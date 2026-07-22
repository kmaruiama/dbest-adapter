package dbest.kernel.http

import dbest.features.sessions.Sessions
import dbest.features.sessions.closeAllSessions
import org.http4k.server.SunHttp
import org.http4k.server.asServer

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8000;
    val sessions = Sessions();
    Runtime.getRuntime().addShutdownHook(Thread { closeAllSessions(sessions) })

    val server = router(sessions).asServer(SunHttp(port)).start();
    println("PORTA: http://localhost:${server.port()}");
}
