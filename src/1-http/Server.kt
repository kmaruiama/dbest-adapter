package dbest.http

import org.http4k.server.SunHttp
import org.http4k.server.asServer

/*
ponto de entrada. Sobe o SunHttp (que ja vem no http4k-core, sem dep extra de servidor) segurando um
unico Canvas. Um shutdown hook fecha o OpenTables ao encerrar.
*/

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8000;
    val canvas = Canvas();
    Runtime.getRuntime().addShutdownHook(Thread { canvas.close() })

    val server = router(canvas).asServer(SunHttp(port)).start();
    println("PORTA: http://localhost:${server.port()}");
}
