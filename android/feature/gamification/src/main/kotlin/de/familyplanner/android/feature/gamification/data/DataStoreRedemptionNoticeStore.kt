package de.familyplanner.android.feature.gamification.data

import android.util.Log
import androidx.datastore.core.DataStore
import de.familyplanner.android.feature.gamification.domain.RedemptionNoticeStore
import de.familyplanner.android.feature.gamification.domain.notice.RedemptionSnapshot
import kotlinx.coroutines.flow.first
import java.io.IOException

private const val TAG = "DataStoreRedemptionNoticeStore"

class DataStoreRedemptionNoticeStore(
    private val dataStore: DataStore<StoredRedemptions>,
) : RedemptionNoticeStore {
    override suspend fun read(memberId: String): RedemptionSnapshot? =
        try {
            dataStore.data
                .first()
                .toDomain()
                ?.takeIf { it.first == memberId }
                ?.second
        } catch (e: IOException) {
            Log.w(TAG, "Redemption store unreadable", e)
            null
        }

    override suspend fun write(
        memberId: String,
        snapshot: RedemptionSnapshot,
    ) {
        try {
            dataStore.updateData { snapshot.toStored(memberId) }
        } catch (e: IOException) {
            Log.w(TAG, "Redemption store not written", e)
        }
    }

    override suspend fun clear() {
        try {
            dataStore.updateData { StoredRedemptions() }
        } catch (e: IOException) {
            Log.w(TAG, "Redemption store not cleared", e)
        }
    }
}
