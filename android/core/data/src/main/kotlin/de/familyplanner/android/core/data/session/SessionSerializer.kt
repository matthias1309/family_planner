package de.familyplanner.android.core.data.session

import android.util.Log
import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException

private const val TAG = "SessionSerializer"
private val ASSOCIATED_DATA = "family-planner-session".encodeToByteArray()

@Serializable
data class StoredSession(
    val session: Session? = null,
)

/** Verschlüsselt die Session mit AES-GCM (Tink); unlesbare Daten gelten als „abgemeldet“. */
class SessionSerializer(
    private val aead: Aead,
) : Serializer<StoredSession> {
    override val defaultValue: StoredSession = StoredSession()

    override suspend fun readFrom(input: InputStream): StoredSession {
        val encrypted = input.readBytes()
        if (encrypted.isEmpty()) return defaultValue
        return try {
            Json.decodeFromString<StoredSession>(aead.decrypt(encrypted, ASSOCIATED_DATA).decodeToString())
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "Stored session cannot be decrypted, treating as signed out", e)
            defaultValue
        } catch (e: SerializationException) {
            Log.w(TAG, "Stored session is malformed, treating as signed out", e)
            defaultValue
        }
    }

    override suspend fun writeTo(
        t: StoredSession,
        output: OutputStream,
    ) {
        output.write(aead.encrypt(Json.encodeToString(t).encodeToByteArray(), ASSOCIATED_DATA))
    }
}
