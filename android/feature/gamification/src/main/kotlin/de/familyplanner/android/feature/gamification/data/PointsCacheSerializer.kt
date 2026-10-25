package de.familyplanner.android.feature.gamification.data

import android.util.Log
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "PointsCacheSerializer"

/** JSON-Datei; unlesbar oder unbekannte Version gilt als leer (ADR-0011). */
class PointsCacheSerializer(
    private val json: Json,
) : Serializer<StoredPoints> {
    override val defaultValue: StoredPoints = StoredPoints()

    override suspend fun readFrom(input: InputStream): StoredPoints {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            json.decodeFromString<StoredPoints>(bytes.decodeToString()).takeIf { it.version == CACHE_VERSION } ?: defaultValue
        } catch (e: SerializationException) {
            Log.w(TAG, "Points cache is malformed, starting empty", e)
            defaultValue
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Points cache is malformed, starting empty", e)
            defaultValue
        }
    }

    override suspend fun writeTo(
        t: StoredPoints,
        output: OutputStream,
    ) {
        output.write(json.encodeToString(StoredPoints.serializer(), t).encodeToByteArray())
    }
}
