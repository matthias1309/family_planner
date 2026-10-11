package de.familyplanner.core.auth

import de.familyplanner.core.device.DeviceService
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.bearer

const val AUTH_DEVICE = "device"

fun AuthenticationConfig.deviceAuth(service: DeviceService) {
    bearer(AUTH_DEVICE) {
        authenticate { credential -> service.authenticate(credential.token) }
    }
}
