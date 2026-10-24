package de.familyplanner.android.feature.tasks.data

import android.util.Log
import androidx.datastore.core.DataStore
import de.familyplanner.android.feature.tasks.domain.board.BoardCache
import de.familyplanner.android.feature.tasks.domain.board.BoardSnapshot
import de.familyplanner.android.feature.tasks.domain.board.CachedBoard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val TAG = "DataStoreBoardCache"

class DataStoreBoardCache(
    private val dataStore: DataStore<StoredBoard>,
) : BoardCache {
    override suspend fun read(memberId: String): BoardSnapshot? =
        try {
            dataStore.data
                .first()
                .toDomain()
                ?.takeIf { it.memberId == memberId }
                ?.board
        } catch (e: IOException) {
            Log.w(TAG, "Task board cache unreadable", e)
            null
        }

    override suspend fun write(
        memberId: String,
        board: BoardSnapshot,
    ) {
        try {
            dataStore.updateData { CachedBoard(memberId, board).toStored() }
        } catch (e: IOException) {
            Log.w(TAG, "Task board cache not written", e)
        }
    }

    override suspend fun update(
        memberId: String,
        change: (BoardSnapshot) -> BoardSnapshot,
    ) {
        try {
            dataStore.updateData { stored ->
                val cached = stored.toDomain()
                if (cached?.memberId == memberId) CachedBoard(memberId, change(cached.board)).toStored() else stored
            }
        } catch (e: IOException) {
            Log.w(TAG, "Task board cache not updated", e)
        }
    }

    override suspend fun clear() {
        try {
            dataStore.updateData { StoredBoard() }
        } catch (e: IOException) {
            Log.w(TAG, "Task board cache not cleared", e)
        }
    }

    override fun observe(): Flow<CachedBoard?> =
        dataStore.data
            .map { it.toDomain() }
            .catch { error ->
                if (error !is IOException) throw error
                Log.w(TAG, "Task board cache unreadable", error)
                emit(null)
            }
}
