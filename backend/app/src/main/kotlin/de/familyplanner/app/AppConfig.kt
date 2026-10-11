package de.familyplanner.app

import java.nio.file.Path
import java.nio.file.Paths

class ConfigException(
    message: String,
) : IllegalArgumentException(message)

data class RateLimits(
    val globalPerMinute: Int = DEFAULT_GLOBAL_PER_MINUTE,
    val pairingPerMinute: Int = DEFAULT_PAIRING_PER_MINUTE,
) {
    companion object {
        const val DEFAULT_GLOBAL_PER_MINUTE = 120
        const val DEFAULT_PAIRING_PER_MINUTE = 5
    }
}

data class TlsConfig(
    val keystorePath: Path,
    val keystorePassword: String,
    val keyAlias: String = DEFAULT_KEY_ALIAS,
) {
    companion object {
        const val DEFAULT_KEY_ALIAS = "planner"
    }
}

data class AppConfig(
    val port: Int = DEFAULT_PORT,
    val healthPort: Int = DEFAULT_HEALTH_PORT,
    val dbPath: Path = Paths.get(DEFAULT_DB_PATH),
    val setupToken: String? = null,
    val tls: TlsConfig? = null,
    val version: String = DEFAULT_VERSION,
    val rateLimits: RateLimits = RateLimits(),
) {
    companion object {
        const val DEFAULT_PORT = 8095
        const val DEFAULT_HEALTH_PORT = 8096
        const val DEFAULT_DB_PATH = "./data/family-planner.db"
        const val DEFAULT_KEYSTORE = "/app/certs/planner.p12"
        const val DEFAULT_VERSION = "dev"
        const val MIN_SETUP_TOKEN_LENGTH = 16
        private const val MAX_PORT = 65535

        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            val setupToken = env["FP_SETUP_TOKEN"]?.takeIf { it.isNotEmpty() }
            if (setupToken != null && setupToken.length < MIN_SETUP_TOKEN_LENGTH) {
                throw ConfigException("FP_SETUP_TOKEN muss mindestens $MIN_SETUP_TOKEN_LENGTH Zeichen lang sein")
            }
            return AppConfig(
                port = env.port("FP_PORT", DEFAULT_PORT),
                healthPort = env.port("FP_HEALTH_PORT", DEFAULT_HEALTH_PORT),
                dbPath = Paths.get(env["FP_DB_PATH"] ?: DEFAULT_DB_PATH),
                setupToken = setupToken,
                tls = tlsFrom(env),
                version = env["FP_VERSION"] ?: DEFAULT_VERSION,
            )
        }

        private fun tlsFrom(env: Map<String, String>): TlsConfig? {
            val enabled = env["FP_TLS_ENABLED"]?.lowercase()
            if (enabled != "true") return null
            val password =
                env["FP_TLS_KEYSTORE_PASSWORD"]?.takeIf { it.isNotEmpty() }
                    ?: throw ConfigException("FP_TLS_KEYSTORE_PASSWORD ist Pflicht, wenn FP_TLS_ENABLED=true")
            return TlsConfig(Paths.get(env["FP_TLS_KEYSTORE"] ?: DEFAULT_KEYSTORE), password)
        }

        private fun Map<String, String>.port(
            name: String,
            default: Int,
        ): Int {
            val raw = this[name] ?: return default
            val value = raw.toIntOrNull()
            if (value == null || value !in 1..MAX_PORT) throw ConfigException("$name muss ein Port zwischen 1 und $MAX_PORT sein")
            return value
        }
    }
}
