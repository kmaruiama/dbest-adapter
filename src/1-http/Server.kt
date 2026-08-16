package dbest.http

import org.http4k.server.SunHttp
import org.http4k.server.asServer

/*
ponto de entrada. Sobe o SunHttp (que ja vem no http4k-core, sem dep extra de servidor) segurando o
gerenciador de Sessions (as abas abertas, cada uma com seu Canvas). Um shutdown hook fecha todos os
OpenTables ao encerrar.
*/

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8000;
    val sessions = Sessions();
    Runtime.getRuntime().addShutdownHook(Thread { sessions.close() })

    val server = router(sessions).asServer(SunHttp(port)).start();
    println("PORTA: http://localhost:${server.port()}");
}
