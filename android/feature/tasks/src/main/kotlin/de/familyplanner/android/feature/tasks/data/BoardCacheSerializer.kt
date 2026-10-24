package de.familyplanner.android.feature.tasks.data

import android.util.Log
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "BoardCacheSerializer"

/** JSON-Datei; unlesbar oder unbekannte Version gilt als leer (ADR-0011). */
class BoardCacheSerializer(
    private val json: Json,
) : Serializer<StoredBoard> {
    override val defaultValue: StoredBoard = StoredBoard()

    override suspend fun readFrom(input: InputStream): StoredBoard {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            json.decodeFromString<StoredBoard>(bytes.decodeToString()).takeIf { it.version == CACHE_VERSION } ?: defaultValue
        } catch (e: SerializationException) {
            Log.w(TAG, "Task board cache is malformed, starting empty", e)
            defaultValue
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Task board cache is malformed, starting empty", e)
            defaultValue
        }
    }

    override suspend fun writeTo(
        t: StoredBoard,
        output: OutputStream,
    ) {
        output.write(json.encodeToString(StoredBoard.serializer(), t).encodeToByteArray())
    }
}
