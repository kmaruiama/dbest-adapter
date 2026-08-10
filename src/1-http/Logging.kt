package dbest.http

import org.http4k.core.Filter
import org.http4k.core.Response
import java.io.PrintStream

/*
  Logging concentrado na borda HTTP (o shell imperativo). Um UNICO sink (emit) escreve no processo,
  entao nenhum sysout vaza para o nucleo puro (adapter/model/json/export) — la nao ha log nenhum.
  Duas fontes chamam o sink, ambas em dbest.http:
    - accessLogFilter: uma linha por request (metodo, uri, status, tempo) no stdout.
    - errorFilter (Errors.kt): a excecao capturada no stderr, com stack trace nos 5xx.
  Trocar a saida (arquivo, framework) mexe so aqui.
*/

// unico ponto de saida de log do processo
private fun emit(stream: PrintStream, line: String) {
    stream.println(line)
}

internal fun logAccess(line: String) {
    emit(System.out, line)
}

internal fun logError(line: String, cause: Throwable? = null) {
    emit(System.err, line)
    cause?.printStackTrace(System.err)
}

// mede e registra cada request; roda por fora do errorFilter, entao ve o status ja mapeado
val accessLogFilter: Filter = Filter { next ->
    { request ->
        val start: Long = System.nanoTime()
        val response: Response = next(request)
        val elapsedMs: Long = (System.nanoTime() - start) / 1_000_000
        logAccess("${request.method} ${request.uri} ${response.status.code} ${elapsedMs}ms")
        response
    }
}
