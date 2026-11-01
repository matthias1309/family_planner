package de.familyplanner.android.feature.gamification.data

import android.util.Log
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "AvatarCacheSerializer"

/** JSON-Datei; unlesbar oder unbekannte Version gilt als leer (ADR-0011). */
class AvatarCacheSerializer(
    private val json: Json,
) : Serializer<StoredAvatars> {
    override val defaultValue: StoredAvatars = StoredAvatars()

    override suspend fun readFrom(input: InputStream): StoredAvatars {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            json.decodeFromString<StoredAvatars>(bytes.decodeToString()).takeIf { it.version == AVATAR_CACHE_VERSION } ?: defaultValue
        } catch (e: SerializationException) {
            Log.w(TAG, "Avatar cache is malformed, starting empty", e)
            defaultValue
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Avatar cache is malformed, starting empty", e)
            defaultValue
        }
    }

    override suspend fun writeTo(
        t: StoredAvatars,
        output: OutputStream,
    ) {
        output.write(json.encodeToString(StoredAvatars.serializer(), t).encodeToByteArray())
    }
}
