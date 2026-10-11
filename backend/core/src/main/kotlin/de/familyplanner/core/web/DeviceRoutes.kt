package de.familyplanner.core.web

import de.familyplanner.api.model.DeviceListResponse
import de.familyplanner.core.auth.AUTH_DEVICE
import de.familyplanner.core.auth.requireParent
import de.familyplanner.core.auth.uuidParameter
import de.familyplanner.core.device.DeviceId
import de.familyplanner.core.device.DeviceService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get

fun Route.deviceRoutes(devices: DeviceService) {
    authenticate(AUTH_DEVICE) {
        get("/api/v1/devices") {
            call.requireParent()
            call.respond(DeviceListResponse(success = true, data = devices.list().map { it.toApi() }))
        }
        delete("/api/v1/devices/{deviceId}") {
            call.requireParent()
            devices.revoke(DeviceId(call.uuidParameter("deviceId")))
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
