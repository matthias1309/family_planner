package de.familyplanner.android.feature.meals.data.reminder

import android.util.Log
import androidx.datastore.core.DataStore
import de.familyplanner.android.feature.meals.domain.reminder.ReminderSnapshot
import de.familyplanner.android.feature.meals.domain.reminder.ReminderStore
import kotlinx.coroutines.flow.first
import java.io.IOException

private const val TAG = "DataStoreReminderStore"

class DataStoreReminderStore(
    private val dataStore: DataStore<StoredReminders>,
) : ReminderStore {
    override suspend fun read(): ReminderSnapshot? =
        try {
            dataStore.data
                .first()
                .snapshot
                ?.toDomain()
        } catch (e: IOException) {
            Log.w(TAG, "Reminder store unreadable", e)
            null
        }

    override suspend fun write(snapshot: ReminderSnapshot) = update(StoredReminders(snapshot = snapshot.toStored()))

    override suspend fun clear() = update(StoredReminders())

    private suspend fun update(value: StoredReminders) {
        try {
            dataStore.updateData { value }
        } catch (e: IOException) {
            Log.w(TAG, "Reminder store not written", e)
        }
    }
}
