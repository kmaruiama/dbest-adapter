package dbest.http

import dbest.adapter.EngineException
import kotlinx.serialization.SerializationException
import org.http4k.core.Filter
import org.http4k.core.Status

/*
  IllegalArgumentException  -> 400  (require de apply / construcao de Node)
  SerializationException    -> 400  (corpo malformado, @type desconhecido)
  NotFoundException         -> 404  (id de rota inexistente)
  EngineException.PlanError -> 422  (plano semanticamente invalido)
  EngineException.Storage   -> 502  (falha vinda do storage da engine)
  qualquer outra            -> 500
*/

val errorFilter = Filter { next ->
    { request ->
        try {
            next(request)
        } catch (e: NotFoundException) {
            errorResponse(Status.NOT_FOUND, e.message ?: "nao encontrado")
        } catch (e: IllegalArgumentException) {
            errorResponse(Status.BAD_REQUEST, e.message ?: "requisicao invalida")
        } catch (e: SerializationException) {
            errorResponse(Status.BAD_REQUEST, e.message ?: "corpo malformado")
        } catch (e: EngineException.PlanError) {
            errorResponse(Status.UNPROCESSABLE_ENTITY, e.message ?: "plano invalido")
        } catch (e: EngineException.StorageError) {
            errorResponse(Status.BAD_GATEWAY, e.message ?: "erro de storage")
        } catch (e: EngineException) {
            errorResponse(Status.INTERNAL_SERVER_ERROR, e.message ?: "falha na engine")
        }
    }
}
