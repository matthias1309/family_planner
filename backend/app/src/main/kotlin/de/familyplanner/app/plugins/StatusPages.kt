package de.familyplanner.app.plugins

import de.familyplanner.core.error.AppException
import de.familyplanner.core.error.ErrorCode
import de.familyplanner.core.web.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import kotlinx.serialization.SerializationException

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<AppException> { call, e -> call.respondError(e.code, e.message) }
        exception<BadRequestException> { call, _ -> call.respondError(ErrorCode.VALIDATION_FAILED, "Ungültige Anfrage") }
        // A missing or unreadable request body is the client's fault, not a server error.
        exception<ContentTransformationException> { call, _ -> call.respondError(ErrorCode.VALIDATION_FAILED, "Ungültige Anfrage") }
        exception<SerializationException> { call, _ -> call.respondError(ErrorCode.VALIDATION_FAILED, "Ungültige Anfrage") }
        exception<Throwable> { call, e ->
            call.application.log.error("Unhandled error on {} {}", call.request.httpMethod.value, call.request.path(), e)
            call.respondError(ErrorCode.INTERNAL, "Interner Fehler")
        }
        status(HttpStatusCode.Unauthorized) { call, _ -> call.respondError(ErrorCode.UNAUTHORIZED, "Nicht angemeldet") }
        status(HttpStatusCode.TooManyRequests) { call, _ -> call.respondError(ErrorCode.RATE_LIMITED, "Zu viele Anfragen") }
    }
}
