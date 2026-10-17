package de.familyplanner.android.core.data

sealed interface AppError {
    data object Network : AppError

    data object Certificate : AppError

    data object Unauthorized : AppError

    data object InvalidPairingCode : AppError

    data object Forbidden : AppError

    data object NotFound : AppError

    data object RateLimited : AppError

    data object SetupDisabled : AppError

    data class Validation(
        val message: String,
    ) : AppError

    data class Conflict(
        val message: String,
    ) : AppError

    /** Die Sitzung konnte nicht lokal gespeichert werden. */
    data object Storage : AppError

    data object Server : AppError
}

sealed interface ApiResult<out T> {
    data class Success<T>(
        val data: T,
    ) : ApiResult<T>

    data class Failure(
        val error: AppError,
    ) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> =
    when (this) {
        is ApiResult.Success -> ApiResult.Success(transform(data))
        is ApiResult.Failure -> this
    }

fun validationFailure(message: String): ApiResult.Failure = ApiResult.Failure(AppError.Validation(message))
