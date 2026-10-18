package de.familyplanner.android.feature.meals.data.plan

import android.util.Log
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "PlanCacheSerializer"

/** JSON-Datei; unlesbar oder unbekannte Version gilt als leer (ADR-0011). */
class PlanCacheSerializer(
    private val json: Json,
) : Serializer<StoredPlanCache> {
    override val defaultValue: StoredPlanCache = StoredPlanCache()

    override suspend fun readFrom(input: InputStream): StoredPlanCache {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            json.decodeFromString<StoredPlanCache>(bytes.decodeToString()).takeIf { it.version == CACHE_VERSION } ?: defaultValue
        } catch (e: SerializationException) {
            Log.w(TAG, "Meal plan cache is malformed, starting empty", e)
            defaultValue
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Meal plan cache is malformed, starting empty", e)
            defaultValue
        }
    }

    override suspend fun writeTo(
        t: StoredPlanCache,
        output: OutputStream,
    ) {
        output.write(json.encodeToString(StoredPlanCache.serializer(), t).encodeToByteArray())
    }
}
