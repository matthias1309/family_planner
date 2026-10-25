package de.familyplanner.android.feature.gamification.data

import android.util.Log
import androidx.datastore.core.DataStore
import de.familyplanner.android.feature.gamification.domain.PointsCache
import de.familyplanner.android.feature.gamification.domain.PointsSnapshot
import kotlinx.coroutines.flow.first
import java.io.IOException

private const val TAG = "DataStorePointsCache"

class DataStorePointsCache(
    private val dataStore: DataStore<StoredPoints>,
) : PointsCache {
    override suspend fun read(memberId: String): PointsSnapshot? =
        try {
            dataStore.data
                .first()
                .toDomain()
                ?.takeIf { it.first == memberId }
                ?.second
        } catch (e: IOException) {
            Log.w(TAG, "Points cache unreadable", e)
            null
        }

    override suspend fun write(
        memberId: String,
        snapshot: PointsSnapshot,
    ) {
        try {
            dataStore.updateData { snapshot.toStored(memberId) }
        } catch (e: IOException) {
            Log.w(TAG, "Points cache not written", e)
        }
    }

    override suspend fun clear() {
        try {
            dataStore.updateData { StoredPoints() }
        } catch (e: IOException) {
            Log.w(TAG, "Points cache not cleared", e)
        }
    }
}
