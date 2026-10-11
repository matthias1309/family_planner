package de.familyplanner.core.web

import de.familyplanner.api.model.ApiError
import de.familyplanner.api.model.ErrorResponse
import de.familyplanner.core.error.ErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import de.familyplanner.api.model.ErrorCode as ApiErrorCode

suspend fun ApplicationCall.respondError(
    code: ErrorCode,
    message: String,
) = respond(
    HttpStatusCode.fromValue(code.httpStatus),
    ErrorResponse(success = false, error = ApiError(code = ApiErrorCode.valueOf(code.name), message = message)),
)
