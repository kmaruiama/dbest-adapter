package dbest.http

import dbest.adapter.EngineException
import kotlinx.serialization.SerializationException
import org.http4k.core.Filter
import org.http4k.core.Response
import org.http4k.core.Status

/*
  IllegalArgumentException  -> 400  (require de apply / construcao de Node)
  SerializationException    -> 400  (corpo malformado, @type desconhecido)
  NotFoundException         -> 404  (id de rota inexistente)
  EngineBusyException       -> 409  (a espera pela engine estourou o teto; a engine eh single-thread)
  EngineException.PlanError -> 422  (plano semanticamente invalido)
  EngineException.Storage   -> 502  (falha vinda do storage da engine)
  qualquer outra            -> 500
*/

// log na borda: 4xx viram uma linha (esperados, culpa do cliente); 5xx levam o stack trace junto
private fun fail(status: Status, e: Throwable, fallback: String, trace: Boolean): Response {
    val message: String = e.message ?: fallback
    logError("${status.code} ${e.javaClass.simpleName}: $message", if (trace) e else null)
    return errorResponse(status, message)
}

val errorFilter = Filter { next ->
    { request ->
        try {
            next(request)
        } catch (e: NotFoundException) {
            fail(Status.NOT_FOUND, e, "nao encontrado", trace = false)
        } catch (e: EngineBusyException) {
            fail(Status.CONFLICT, e, "engine ocupada", trace = false)
        } catch (e: IllegalArgumentException) {
            fail(Status.BAD_REQUEST, e, "requisicao invalida", trace = false)
        } catch (e: SerializationException) {
            fail(Status.BAD_REQUEST, e, "corpo malformado", trace = false)
        } catch (e: EngineException.PlanError) {
            fail(Status.UNPROCESSABLE_ENTITY, e, "plano invalido", trace = false)
        } catch (e: EngineException.StorageError) {
            fail(Status.BAD_GATEWAY, e, "erro de storage", trace = true)
        } catch (e: EngineException) {
            fail(Status.INTERNAL_SERVER_ERROR, e, "falha na engine", trace = true)
        }
    }
}
