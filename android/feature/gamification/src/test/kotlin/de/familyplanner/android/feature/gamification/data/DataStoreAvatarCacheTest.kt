package de.familyplanner.android.feature.gamification.data

import androidx.datastore.core.DataStoreFactory
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.feature.gamification.fakes.freeLook
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

class DataStoreAvatarCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val json = createJson()
    private val scopes = mutableListOf<CoroutineScope>()
    private val file: File get() = folder.root.resolve("avatars.json")
    private val looks =
        mapOf(
            "c-1" to freeLook,
            "c-2" to freeLook.copy(accessories = "accessories.wayfarers", facialHair = "facialHair.beardLight"),
        )

    private fun newCache(): DataStoreAvatarCache {
        val scope = CoroutineScope(Job()).also { scopes += it }
        return DataStoreAvatarCache(DataStoreFactory.create(AvatarCacheSerializer(json), scope = scope) { file })
    }

    private suspend fun closeStores() = scopes.forEach { it.coroutineContext[Job]?.cancelAndJoin() }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    @Test
    fun `REQ-GAME-014 never written cache reads as null`() = runBlocking { assertNull(newCache().read()) }

    @Test
    fun `REQ-GAME-014 looks survive a restart`() =
        runBlocking {
            newCache().write(looks)
            closeStores()

            assertEquals(looks, newCache().read())
        }

    @Test
    fun `REQ-GAME-014 other version reads as null`() =
        runBlocking {
            file.writeText("""{"version":99,"looks":[]}""")

            assertNull(newCache().read())
        }

    @Test
    fun `REQ-GAME-014 malformed file reads as null`() =
        runBlocking {
            file.writeText("{not json")

            assertNull(newCache().read())
        }

    @Test
    fun `REQ-GAME-014 clear empties the cache`() =
        runBlocking {
            val cache = newCache()
            cache.write(looks)

            cache.clear()

            assertNull(cache.read())
        }
}
