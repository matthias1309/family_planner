package de.familyplanner.android.core.data.session

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SessionSerializerTest {
    private lateinit var aead: Aead
    private val session = Session("secret-token-value", "m", "Lea", Role.CHILD, "d")

    @Before
    fun setUp() {
        AeadConfig.register()
        aead =
            KeysetHandle
                .generateNew(KeyTemplates.get("AES256_GCM"))
                .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private suspend fun encode(value: StoredSession): ByteArray =
        ByteArrayOutputStream().also { SessionSerializer(aead).writeTo(value, it) }.toByteArray()

    @Test
    fun `session roundtrips through encryption`() =
        runTest {
            val bytes = encode(StoredSession(session))
            assertEquals(session, SessionSerializer(aead).readFrom(ByteArrayInputStream(bytes)).session)
        }

    @Test
    fun `ciphertext does not contain the token`() =
        runTest {
            val bytes = encode(StoredSession(session))
            assertFalse(bytes.decodeToString().contains("secret-token-value"))
        }

    @Test
    fun `tampered bytes yield signed out without exception`() =
        runTest {
            val bytes = encode(StoredSession(session))
            bytes[bytes.lastIndex] = (bytes.last() + 1).toByte()
            assertNull(SessionSerializer(aead).readFrom(ByteArrayInputStream(bytes)).session)
        }

    @Test
    fun `empty input yields signed out`() =
        runTest {
            assertNull(SessionSerializer(aead).readFrom(ByteArrayInputStream(ByteArray(0))).session)
        }

    @Test
    fun `session toString hides token`() {
        assertFalse(session.toString().contains("secret-token-value"))
    }
}
