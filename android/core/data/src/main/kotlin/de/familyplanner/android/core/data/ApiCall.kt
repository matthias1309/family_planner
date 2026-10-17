package de.familyplanner.android.core.data

import android.util.Log
import de.familyplanner.api.model.ErrorCode
import de.familyplanner.api.model.ErrorResponse
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import javax.net.ssl.SSLException

private const val TAG = "ApiCall"
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_CONFLICT = 409
private const val HTTP_TOO_MANY_REQUESTS = 429

suspend fun <E, T> apiCall(
    json: Json,
    call: suspend () -> Response<E>,
    unwrap: (E) -> T?,
): ApiResult<T> =
    guarded {
        val response = call()
        val data = response.body()?.let(unwrap)
        when {
            response.isSuccessful && data != null -> ApiResult.Success(data)
            response.isSuccessful -> ApiResult.Failure(AppError.Server)
            else -> ApiResult.Failure(response.toAppError(json))
        }
    }

suspend fun apiCallNoContent(
    json: Json,
    call: suspend () -> Response<Unit>,
): ApiResult<Unit> =
    guarded {
        val response = call()
        if (response.isSuccessful) ApiResult.Success(Unit) else ApiResult.Failure(response.toAppError(json))
    }

private suspend fun <T> guarded(block: suspend () -> ApiResult<T>): ApiResult<T> =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SSLException) {
        Log.w(TAG, "TLS failure", e)
        ApiResult.Failure(AppError.Certificate)
    } catch (e: IOException) {
        Log.w(TAG, "Network failure", e)
        ApiResult.Failure(AppError.Network)
    } catch (e: SerializationException) {
        Log.w(TAG, "Unreadable response", e)
        ApiResult.Failure(AppError.Server)
    }

private fun Response<*>.toAppError(json: Json): AppError {
    val parsed =
        runCatching { errorBody()?.string()?.let { json.decodeFromString<ErrorResponse>(it) } }
            .getOrNull()
    return parsed?.let { it.error.code.toAppError(it.error.message) } ?: code().toAppError()
}

private fun ErrorCode.toAppError(message: String): AppError =
    when (this) {
        ErrorCode.VALIDATION_FAILED -> AppError.Validation(message)
        ErrorCode.UNAUTHORIZED -> AppError.Unauthorized
        ErrorCode.INVALID_PAIRING_CODE -> AppError.InvalidPairingCode
        ErrorCode.FORBIDDEN -> AppError.Forbidden
        ErrorCode.NOT_FOUND -> AppError.NotFound
        ErrorCode.CONFLICT -> AppError.Conflict(message)
        ErrorCode.RATE_LIMITED -> AppError.RateLimited
        ErrorCode.SETUP_DISABLED -> AppError.SetupDisabled
        ErrorCode.INTERNAL -> AppError.Server
    }

private fun Int.toAppError(): AppError =
    when (this) {
        HTTP_UNAUTHORIZED -> AppError.Unauthorized
        HTTP_FORBIDDEN -> AppError.Forbidden
        HTTP_NOT_FOUND -> AppError.NotFound
        HTTP_CONFLICT -> AppError.Conflict("")
        HTTP_TOO_MANY_REQUESTS -> AppError.RateLimited
        else -> AppError.Server
    }
