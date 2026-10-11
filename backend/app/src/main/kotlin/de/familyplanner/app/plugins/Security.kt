package de.familyplanner.app.plugins

import de.familyplanner.core.auth.deviceAuth
import de.familyplanner.core.device.DeviceService
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication

fun Application.configureSecurity(devices: DeviceService) {
    install(Authentication) {
        deviceAuth(devices)
    }
}
