package de.familyplanner.android.feature.gamification.data

import android.util.Log
import androidx.datastore.core.DataStore
import de.familyplanner.android.core.ui.avatar.AvatarLook
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarCache
import kotlinx.coroutines.flow.first
import java.io.IOException

private const val TAG = "DataStoreAvatarCache"

class DataStoreAvatarCache(
    private val dataStore: DataStore<StoredAvatars>,
) : AvatarCache {
    override suspend fun read(): Map<String, AvatarLook>? =
        try {
            // Eine nie geschriebene Datei (leerer Standard) zählt als „kein Stand“.
            dataStore.data
                .first()
                .takeIf { it.looks.isNotEmpty() }
                ?.toDomain()
        } catch (e: IOException) {
            Log.w(TAG, "Avatar cache unreadable", e)
            null
        }

    override suspend fun write(looks: Map<String, AvatarLook>) {
        try {
            dataStore.updateData { looks.toStored() }
        } catch (e: IOException) {
            Log.w(TAG, "Avatar cache not written", e)
        }
    }

    override suspend fun clear() {
        try {
            dataStore.updateData { StoredAvatars() }
        } catch (e: IOException) {
            Log.w(TAG, "Avatar cache not cleared", e)
        }
    }
}
