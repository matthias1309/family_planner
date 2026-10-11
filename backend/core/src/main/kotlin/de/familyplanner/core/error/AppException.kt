package de.familyplanner.core.error

enum class ErrorCode(
    val httpStatus: Int,
) {
    VALIDATION_FAILED(400),
    UNAUTHORIZED(401),
    INVALID_PAIRING_CODE(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    CONFLICT(409),
    RATE_LIMITED(429),
    SETUP_DISABLED(503),
    INTERNAL(500),
}

sealed class AppException(
    val code: ErrorCode,
    override val message: String,
) : RuntimeException(message) {
    class Validation(
        message: String,
    ) : AppException(ErrorCode.VALIDATION_FAILED, message)

    class Unauthorized(
        message: String = "Nicht angemeldet",
    ) : AppException(ErrorCode.UNAUTHORIZED, message)

    class InvalidPairingCode : AppException(ErrorCode.INVALID_PAIRING_CODE, "Kopplungscode ungültig oder abgelaufen")

    class Forbidden(
        message: String = "Nur für Eltern",
    ) : AppException(ErrorCode.FORBIDDEN, message)

    class NotFound(
        message: String,
    ) : AppException(ErrorCode.NOT_FOUND, message)

    class Conflict(
        message: String,
    ) : AppException(ErrorCode.CONFLICT, message)

    class SetupDisabled : AppException(ErrorCode.SETUP_DISABLED, "Einrichtung nicht verfügbar")
}
