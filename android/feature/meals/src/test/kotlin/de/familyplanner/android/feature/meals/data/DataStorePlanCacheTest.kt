package de.familyplanner.android.feature.meals.data

import androidx.datastore.core.DataStoreFactory
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.feature.meals.data.plan.DataStorePlanCache
import de.familyplanner.android.feature.meals.data.plan.PlanCacheSerializer
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.PlannedDay
import de.familyplanner.android.feature.meals.domain.plan.WeekPlanSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DataStorePlanCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val json = createJson()
    private val scopes = mutableListOf<CoroutineScope>()

    // Samstag, 17.10.2026 — Vorwoche = 5.10.
    private val today = LocalDate.of(2026, 10, 17)
    private val firstWeek = LocalDate.of(2026, 10, 12)

    private fun newCache(): DataStorePlanCache {
        val scope = CoroutineScope(Job()).also { scopes += it }
        return DataStorePlanCache(DataStoreFactory.create(PlanCacheSerializer(json), scope = scope) { folder.root.resolve("plan.json") })
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    private fun snapshot(
        start: LocalDate,
        syncedAt: Instant = Instant.parse("2026-10-17T10:00:00Z"),
    ) = WeekPlanSnapshot(
        start,
        (0L..6L).map { offset ->
            val date = start.plusDays(offset)
            PlannedDay(
                date,
                if (offset ==
                    0L
                ) {
                    Dish("d-$start", date, "p-1", "Spaghetti", listOf("Nudeln"))
                } else {
                    null
                },
                if (offset == 0L) "Anna" else null,
            )
        },
        syncedAt,
    )

    @Test
    fun `REQ-MEAL-002 written week can be read back with sync time`() =
        runBlocking {
            val cache = newCache()
            val week = snapshot(firstWeek)

            cache.write("p-1", week, today)

            assertEquals(week, cache.read("p-1", firstWeek))
            assertNull(cache.read("p-1", firstWeek.plusWeeks(1)))
        }

    @Test
    fun `REQ-MEAL-002 cache survives a new store instance`() =
        runBlocking {
            val week = snapshot(firstWeek)
            newCache().write("p-1", week, today)
            // Erste Instanz beenden: zwei aktive DataStores auf derselben Datei sind nicht erlaubt.
            scopes.forEach { it.cancel() }

            assertEquals(week, newCache().read("p-1", firstWeek))
        }

    @Test
    fun `REQ-MEAL-002 cache of another member is not shown`() =
        runBlocking {
            val cache = newCache()
            cache.write("p-1", snapshot(firstWeek), today)

            assertNull(cache.read("c-1", firstWeek))
        }

    @Test
    fun `REQ-MEAL-002 writing as another member replaces the cache`() =
        runBlocking {
            val cache = newCache()
            cache.write("p-1", snapshot(firstWeek), today)
            cache.write("c-1", snapshot(firstWeek.plusWeeks(1)), today)

            assertNull(cache.read("p-1", firstWeek))
            assertNotNull(cache.read("c-1", firstWeek.plusWeeks(1)))
            assertNull(cache.read("c-1", firstWeek))
        }

    @Test
    fun `REQ-MEAL-002 weeks before last week are dropped`() =
        runBlocking {
            val cache = newCache()
            cache.write("p-1", snapshot(LocalDate.of(2026, 9, 28)), today)
            cache.write("p-1", snapshot(LocalDate.of(2026, 10, 5)), today)

            assertNotNull(cache.read("p-1", LocalDate.of(2026, 10, 5)))
            assertNull(cache.read("p-1", LocalDate.of(2026, 9, 28)))
        }

    @Test
    fun `REQ-MEAL-002 at most eight weeks are kept`() =
        runBlocking {
            val cache = newCache()
            (0L..9L).forEach { index ->
                cache.write("p-1", snapshot(firstWeek.plusWeeks(index), Instant.parse("2026-10-17T10:00:00Z").plusSeconds(index)), today)
            }

            val kept = (0L..9L).count { cache.read("p-1", firstWeek.plusWeeks(it)) != null }
            assertEquals(8, kept)
            assertNotNull(cache.read("p-1", firstWeek.plusWeeks(9)))
            assertNull(cache.read("p-1", firstWeek))
        }

    @Test
    fun `REQ-MEAL-002 rewriting a week replaces it`() =
        runBlocking {
            val cache = newCache()
            cache.write("p-1", snapshot(firstWeek), today)
            val newer = snapshot(firstWeek, Instant.parse("2026-10-17T12:00:00Z"))

            cache.write("p-1", newer, today)

            assertEquals(newer, cache.read("p-1", firstWeek))
        }

    @Test
    fun `REQ-MEAL-002 malformed cache file reads as empty`() =
        runBlocking {
            folder.root.resolve("plan.json").writeText("{kaputt")

            assertNull(newCache().read("p-1", firstWeek))
        }

    @Test
    fun `REQ-MEAL-002 unknown cache version reads as empty`() =
        runBlocking {
            folder.root.resolve("plan.json").writeText("""{"version":99,"memberId":"p-1","weeks":[]}""")

            assertNull(newCache().read("p-1", firstWeek))
        }
}
