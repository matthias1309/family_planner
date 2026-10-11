package de.familyplanner.app

import de.familyplanner.api.model.HealthStatus
import de.familyplanner.core.persistence.dbQuery
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("de.familyplanner.app.HealthRoutes")

fun Route.healthRoutes(
    db: Database,
    version: String,
) {
    get("/health") {
        val result = runCatching { dbQuery(db) { exec("SELECT 1") } }
        result.exceptionOrNull()?.let { log.error("Health check failed: database not reachable", it) }
        if (result.isSuccess) {
            call.respond(HealthStatus(HealthStatus.Status.OK, version))
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, HealthStatus(HealthStatus.Status.ERROR, version))
        }
    }
}
