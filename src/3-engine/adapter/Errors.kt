package dbest.adapter

import ibd.exceptions.DataBaseException

sealed class EngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {

    class PlanError(message: String, cause: Throwable? = null) : EngineException(message, cause)

    class StorageError(message: String, cause: Throwable? = null) : EngineException(message, cause)

    class EngineFailure(message: String, cause: Throwable? = null) : EngineException(message, cause)
}

internal fun <T> gate(block: () -> T): T =
    try {
        block()
    } catch (e: EngineException) {
        throw e
    } catch (e: DataBaseException) {
        throw EngineException.StorageError(e.message ?: "erro de storage", e)
    } catch (e: Exception) {
        val message = e.message.orEmpty()
        when {
            "not found" in message -> throw EngineException.PlanError(message, e)
            else -> throw EngineException.EngineFailure(message.ifEmpty { e.javaClass.simpleName }, e)
        }
    }
