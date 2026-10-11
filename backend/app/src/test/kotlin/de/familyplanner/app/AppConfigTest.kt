package de.familyplanner.app

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppConfigTest {
    @Test
    fun `defaults are used for an empty environment`() {
        val config = AppConfig.fromEnv(emptyMap())

        assertEquals(8095, config.port)
        assertEquals(8096, config.healthPort)
        assertEquals(Paths.get("./data/family-planner.db"), config.dbPath)
        assertNull(config.setupToken)
        assertNull(config.tls)
        assertEquals("dev", config.version)
    }

    @Test
    fun `values are read from the environment`() {
        val config =
            AppConfig.fromEnv(
                mapOf(
                    "FP_PORT" to "9000",
                    "FP_SETUP_TOKEN" to "a-long-enough-setup-token",
                    "FP_TLS_ENABLED" to "true",
                    "FP_TLS_KEYSTORE_PASSWORD" to "secret",
                    "FP_VERSION" to "abc123",
                ),
            )

        assertEquals(9000, config.port)
        assertEquals("a-long-enough-setup-token", config.setupToken)
        assertNotNull(config.tls)
        assertEquals(Paths.get("/app/certs/planner.p12"), config.tls.keystorePath)
        assertEquals("abc123", config.version)
    }

    @Test
    fun `short setup token aborts start`() {
        assertFailsWith<ConfigException> { AppConfig.fromEnv(mapOf("FP_SETUP_TOKEN" to "short")) }
    }

    @Test
    fun `tls without keystore password aborts start`() {
        assertFailsWith<ConfigException> { AppConfig.fromEnv(mapOf("FP_TLS_ENABLED" to "true")) }
    }

    @Test
    fun `invalid port aborts start`() {
        assertFailsWith<ConfigException> { AppConfig.fromEnv(mapOf("FP_PORT" to "70000")) }
        assertFailsWith<ConfigException> { AppConfig.fromEnv(mapOf("FP_PORT" to "abc")) }
    }
}
