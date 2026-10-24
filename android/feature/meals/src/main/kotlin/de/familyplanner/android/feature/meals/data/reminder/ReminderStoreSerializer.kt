package de.familyplanner.android.feature.meals.data.reminder

import android.util.Log
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "ReminderStoreSerializer"

/** JSON-Datei; unlesbar oder unbekannte Version gilt als leer (ADR-0013). */
class ReminderStoreSerializer(
    private val json: Json,
) : Serializer<StoredReminders> {
    override val defaultValue: StoredReminders = StoredReminders()

    override suspend fun readFrom(input: InputStream): StoredReminders {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            json.decodeFromString<StoredReminders>(bytes.decodeToString()).takeIf { it.version == REMINDER_STORE_VERSION } ?: defaultValue
        } catch (e: SerializationException) {
            Log.w(TAG, "Reminder store is malformed, starting empty", e)
            defaultValue
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Reminder store is malformed, starting empty", e)
            defaultValue
        }
    }

    override suspend fun writeTo(
        t: StoredReminders,
        output: OutputStream,
    ) {
        output.write(json.encodeToString(StoredReminders.serializer(), t).encodeToByteArray())
    }
}
