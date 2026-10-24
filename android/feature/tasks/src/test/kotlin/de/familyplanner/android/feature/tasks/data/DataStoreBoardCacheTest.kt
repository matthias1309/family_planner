package de.familyplanner.android.feature.tasks.data

import androidx.datastore.core.DataStoreFactory
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.feature.tasks.domain.board.BoardSnapshot
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.android.feature.tasks.fakes.card
import de.familyplanner.android.feature.tasks.fakes.lane
import de.familyplanner.android.feature.tasks.fakes.noon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataStoreBoardCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val json = createJson()
    private val scopes = mutableListOf<CoroutineScope>()
    private val file: File get() = folder.root.resolve("board.json")

    private fun newCache(): DataStoreBoardCache {
        val scope = CoroutineScope(Job()).also { scopes += it }
        return DataStoreBoardCache(DataStoreFactory.create(BoardCacheSerializer(json), scope = scope) { file })
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    private val board =
        BoardSnapshot(
            listOf(
                lane(
                    "c-1",
                    "Ben",
                    card("a", emoji = "🛁", reason = "Unter dem Bett"),
                    card("b", status = TaskStatus.REVIEW, reportedAt = noon),
                    card("c", status = TaskStatus.DONE, doneAt = noon.plusSeconds(60)),
                ),
            ),
            Instant.parse("2026-10-24T12:00:00Z"),
        )

    @Test
    fun `REQ-TASK-021 written board can be read back with sync time`() =
        runBlocking {
            val cache = newCache()

            cache.write("p-1", board)

            assertEquals(board, cache.read("p-1"))
        }

    @Test
    fun `REQ-TASK-021 board survives a new store instance`() =
        runBlocking {
            newCache().write("p-1", board)
            scopes.forEach { it.coroutineContext[Job]?.cancelAndJoin() }

            assertEquals(board, newCache().read("p-1"))
        }

    @Test
    fun `REQ-TASK-021 board of another member is not shown`() =
        runBlocking {
            val cache = newCache()
            cache.write("p-1", board)

            assertNull(cache.read("c-1"))
        }

    @Test
    fun `REQ-TASK-021 update changes only the board of the same member`() =
        runBlocking {
            val cache = newCache()
            cache.write("p-1", board)

            cache.update("c-1") { it.copy(lanes = emptyList()) }
            assertEquals(board, cache.read("p-1"))

            cache.update("p-1") { it.copy(lanes = emptyList()) }
            assertEquals(emptyList(), cache.read("p-1")?.lanes)
        }

    @Test
    fun `REQ-TASK-021 update without a stored board does nothing`() =
        runBlocking {
            val cache = newCache()

            cache.update("p-1") { it.copy(lanes = emptyList()) }

            assertNull(cache.read("p-1"))
        }

    @Test
    fun `REQ-TASK-021 clear removes the board`() =
        runBlocking {
            val cache = newCache()
            cache.write("p-1", board)

            cache.clear()

            assertNull(cache.read("p-1"))
            assertNull(cache.observe().first())
        }

    @Test
    fun `REQ-TASK-003 observe reports the owner and the board`() =
        runBlocking {
            val cache = newCache()
            assertNull(cache.observe().first())

            cache.write("p-1", board)

            val observed = cache.observe().first()
            assertEquals("p-1", observed?.memberId)
            assertEquals(board, observed?.board)
        }

    @Test
    fun `REQ-TASK-021 malformed or old files start empty`() =
        runBlocking {
            file.writeText("{ kaputt")
            assertNull(newCache().read("p-1"))
            scopes.forEach { it.coroutineContext[Job]?.cancelAndJoin() }

            file.writeText("""{"version":99,"memberId":"p-1","syncedAtMillis":1,"lanes":[]}""")
            val cache = newCache()
            assertNull(cache.read("p-1"))
            cache.write("p-1", board)
            assertEquals(board, cache.read("p-1"))
        }

    @Test
    fun `REQ-TASK-021 an unknown status makes the stored board unusable`() =
        runBlocking {
            file.writeText(
                """{"version":1,"memberId":"p-1","syncedAtMillis":1,"lanes":[{"memberId":"c-1","name":"Ben","cards":""" +
                    """[{"id":"a","memberId":"c-1","title":"T","points":1,"status":"WEIRD","assignedAtMillis":1}]}]}""",
            )

            assertNull(newCache().read("p-1"))
        }
}
