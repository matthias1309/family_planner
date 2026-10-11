package de.familyplanner.app.plugins

import de.familyplanner.app.RateLimits
import de.familyplanner.core.web.PAIRING_RATE_LIMIT
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.minutes

fun Application.configureRateLimiting(limits: RateLimits) {
    install(RateLimit) {
        global {
            rateLimiter(limit = limits.globalPerMinute, refillPeriod = 1.minutes)
            requestKey { it.request.origin.remoteHost }
        }
        register(RateLimitName(PAIRING_RATE_LIMIT)) {
            rateLimiter(limit = limits.pairingPerMinute, refillPeriod = 1.minutes)
            requestKey { it.request.origin.remoteHost }
        }
    }
}
