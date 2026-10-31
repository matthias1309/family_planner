package de.familyplanner.android.feature.gamification.data

import androidx.datastore.core.DataStoreFactory
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.feature.gamification.fakes.badge
import de.familyplanner.android.feature.gamification.fakes.overview
import de.familyplanner.android.feature.gamification.fakes.snapshot
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

class DataStorePointsCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val json = createJson()
    private val scopes = mutableListOf<CoroutineScope>()
    private val file: File get() = folder.root.resolve("points.json")

    private fun newCache(): DataStorePointsCache {
        val scope = CoroutineScope(Job()).also { scopes += it }
        return DataStorePointsCache(DataStoreFactory.create(PointsCacheSerializer(json), scope = scope) { file })
    }

    private suspend fun closeStores() = scopes.forEach { it.coroutineContext[Job]?.cancelAndJoin() }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    @Test
    fun `REQ-GAME-012 child snapshot with account survives a restart`() =
        runBlocking {
            newCache().write("c-1", snapshot())
            closeStores()

            assertEquals(snapshot(), newCache().read("c-1"))
        }

    @Test
    fun `REQ-GAME-014 parent snapshot without account is stored`() =
        runBlocking {
            newCache().write("p-1", snapshot(account = null))
            closeStores()

            assertEquals(snapshot(account = null), newCache().read("p-1"))
        }

    @Test
    fun `REQ-GAME-012 snapshot of another member is not returned`() =
        runBlocking {
            val cache = newCache()
            cache.write("c-1", snapshot())

            assertNull(cache.read("c-2"))
        }

    @Test
    fun `REQ-GAME-012 malformed or old file counts as empty`() =
        runBlocking {
            file.writeText("{kaputt")
            assertNull(newCache().read("c-1"))
            closeStores()

            file.writeText("""{"version":99,"memberId":"c-1"}""")
            assertNull(newCache().read("c-1"))
        }

    @Test
    fun `REQ-GAME-012 unknown entry kind counts as not stored`() =
        runBlocking {
            file.writeText(
                """{"version":2,"memberId":"c-1","account":{"memberId":"c-1","collected":1,"balance":1,""" +
                    """"entries":[{"id":"e","kind":"UFO","amount":1,"title":"t","bookedAtMillis":1}]}}""",
            )

            assertNull(newCache().read("c-1"))
        }

    @Test
    fun `REQ-GAME-012 clear removes the snapshot`() =
        runBlocking {
            val cache = newCache()
            cache.write("c-1", snapshot())
            cache.clear()

            assertNull(cache.read("c-1"))
        }

    @Test
    fun `REQ-GAME-021 badges survive a restart`() =
        runBlocking {
            val badges =
                overview(
                    badge("first-task", earned = true, title = "Erste Aufgabe", progress = null),
                    badge("tasks-10"),
                    badge("secret-1", secret = true, title = null, progress = null, hint = "Bleib dran"),
                )
            newCache().write("c-1", snapshot(badges = badges))
            closeStores()

            assertEquals(snapshot(badges = badges), newCache().read("c-1"))
        }

    @Test
    fun `REQ-GAME-021 cache of version 1 counts as empty`() =
        runBlocking {
            file.writeText("""{"version":1,"memberId":"c-1","syncedAtMillis":1}""")

            assertNull(newCache().read("c-1"))
        }
}
