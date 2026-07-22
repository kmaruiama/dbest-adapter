package dbest.kernel.http

import org.http4k.core.Filter
import org.http4k.core.Response
import java.io.PrintStream

val accessLogFilter: Filter = Filter { next ->
    { request ->
        val start: Long = System.nanoTime()
        val response: Response = next(request)
        val elapsedMs: Long = (System.nanoTime() - start) / 1_000_000
        logAccess("${request.method} ${request.uri} ${response.status.code} ${elapsedMs}ms")
        response
    }
}

internal fun logAccess(line: String) {
    emit(System.out, line)
}

internal fun logError(line: String, cause: Throwable? = null) {
    emit(System.err, line)
    cause?.printStackTrace(System.err)
}

private fun emit(stream: PrintStream, line: String) {
    stream.println(line)
}
