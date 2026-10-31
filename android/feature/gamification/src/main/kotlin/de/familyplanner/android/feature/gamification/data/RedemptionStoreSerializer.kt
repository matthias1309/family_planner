package de.familyplanner.android.feature.gamification.data

import android.util.Log
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "RedemptionStoreSerializer"

/** JSON-Datei; unlesbar oder unbekannte Version gilt als leer (ADR-0013). */
class RedemptionStoreSerializer(
    private val json: Json,
) : Serializer<StoredRedemptions> {
    override val defaultValue: StoredRedemptions = StoredRedemptions()

    override suspend fun readFrom(input: InputStream): StoredRedemptions {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            json.decodeFromString<StoredRedemptions>(bytes.decodeToString()).takeIf { it.version == REDEMPTION_STORE_VERSION }
                ?: defaultValue
        } catch (e: SerializationException) {
            Log.w(TAG, "Redemption store is malformed, starting empty", e)
            defaultValue
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Redemption store is malformed, starting empty", e)
            defaultValue
        }
    }

    override suspend fun writeTo(
        t: StoredRedemptions,
        output: OutputStream,
    ) {
        output.write(json.encodeToString(StoredRedemptions.serializer(), t).encodeToByteArray())
    }
}
