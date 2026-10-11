package de.familyplanner.core.web

import de.familyplanner.api.model.PairDeviceRequest
import de.familyplanner.api.model.PairingResultResponse
import de.familyplanner.api.model.SetupRequest
import de.familyplanner.core.pairing.PairingService
import de.familyplanner.core.pairing.SetupService
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

const val PAIRING_RATE_LIMIT = "pairing"

fun Route.authRoutes(
    setup: SetupService,
    pairing: PairingService,
) {
    rateLimit(RateLimitName(PAIRING_RATE_LIMIT)) {
        post("/api/v1/setup") {
            val request = call.receive<SetupRequest>()
            val result = setup.setup(request.setupToken, request.parentName, request.deviceName)
            call.respond(HttpStatusCode.Created, PairingResultResponse(success = true, data = result.toApi()))
        }
        post("/api/v1/devices/pair") {
            val request = call.receive<PairDeviceRequest>()
            val result = pairing.pair(request.code, request.deviceName)
            call.respond(HttpStatusCode.Created, PairingResultResponse(success = true, data = result.toApi()))
        }
    }
}
