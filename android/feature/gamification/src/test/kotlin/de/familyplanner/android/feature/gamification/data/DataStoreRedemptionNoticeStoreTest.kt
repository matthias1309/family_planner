package de.familyplanner.android.feature.gamification.data

import androidx.datastore.core.DataStoreFactory
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.feature.gamification.domain.RedemptionStatus
import de.familyplanner.android.feature.gamification.domain.notice.RedemptionSnapshot
import de.familyplanner.android.feature.gamification.fakes.noon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataStoreRedemptionNoticeStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val json = createJson()
    private val scopes = mutableListOf<CoroutineScope>()
    private val file: File get() = folder.root.resolve("redemptions.json")
    private val snapshot = RedemptionSnapshot(mapOf("d-1" to RedemptionStatus.PENDING, "d-2" to RedemptionStatus.REJECTED), noon)

    private fun newStore(): DataStoreRedemptionNoticeStore {
        val scope = CoroutineScope(Job()).also { scopes += it }
        return DataStoreRedemptionNoticeStore(DataStoreFactory.create(RedemptionStoreSerializer(json), scope = scope) { file })
    }

    private suspend fun closeStores() = scopes.forEach { it.coroutineContext[Job]?.cancelAndJoin() }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    @Test
    fun `REQ-GAME-042 state survives a restart`() =
        runBlocking {
            newStore().write("p-1", snapshot)
            closeStores()

            assertEquals(snapshot, newStore().read("p-1"))
        }

    @Test
    fun `REQ-GAME-042 state of another member is not returned`() =
        runBlocking {
            val store = newStore()
            store.write("p-1", snapshot)

            assertNull(store.read("p-2"))
        }

    @Test
    fun `REQ-GAME-042 unknown status names are skipped`() =
        runBlocking {
            file.writeText("""{"version":1,"memberId":"p-1","syncedAtMillis":1,"statuses":{"d-1":"PENDING","d-2":"UFO"}}""")

            assertEquals(mapOf("d-1" to RedemptionStatus.PENDING), newStore().read("p-1")?.statuses)
        }

    @Test
    fun `REQ-GAME-042 malformed file counts as empty`() =
        runBlocking {
            file.writeText("{kaputt")

            assertNull(newStore().read("p-1"))
        }

    @Test
    fun `REQ-GAME-042 clear removes the state`() =
        runBlocking {
            val store = newStore()
            store.write("p-1", snapshot)
            store.clear()

            assertNull(store.read("p-1"))
        }
}
