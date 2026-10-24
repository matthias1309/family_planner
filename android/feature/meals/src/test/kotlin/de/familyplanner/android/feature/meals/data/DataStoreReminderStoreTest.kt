package de.familyplanner.android.feature.meals.data

import androidx.datastore.core.DataStoreFactory
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.feature.meals.data.reminder.DataStoreReminderStore
import de.familyplanner.android.feature.meals.data.reminder.ReminderStoreSerializer
import de.familyplanner.android.feature.meals.domain.cooking.CookState
import de.familyplanner.android.feature.meals.domain.plan.DishStatus
import de.familyplanner.android.feature.meals.domain.reminder.KnownDish
import de.familyplanner.android.feature.meals.domain.reminder.Reminder
import de.familyplanner.android.feature.meals.domain.reminder.ReminderSnapshot
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
import kotlin.test.assertNull

class DataStoreReminderStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()
    private val week = LocalDate.of(2026, 10, 19)

    private val snapshot =
        ReminderSnapshot(
            "c-1",
            week,
            Instant.parse("2026-10-15T14:00:00Z"),
            CookState.REJECTED,
            listOf(Reminder(week, 1, Instant.parse("2026-10-14T14:00:00Z"))),
            mapOf("d-1" to KnownDish(DishStatus.REJECTED, "kein Gemüse")),
        )

    private fun newStore(): DataStoreReminderStore {
        val scope = CoroutineScope(Job()).also { scopes += it }
        return DataStoreReminderStore(
            DataStoreFactory.create(ReminderStoreSerializer(createJson()), scope = scope) { folder.root.resolve("reminders.json") },
        )
    }

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    @Test
    fun `REQ-MEAL-050 snapshot survives a round trip`() =
        runBlocking {
            val store = newStore()
            store.write(snapshot)

            assertEquals(snapshot, store.read())
        }

    @Test
    fun `REQ-MEAL-050 empty store reads nothing and clear removes the snapshot`() =
        runBlocking {
            val store = newStore()
            assertNull(store.read())
            store.write(snapshot)
            store.clear()

            assertNull(store.read())
        }

    @Test
    fun `REQ-MEAL-050 malformed file reads as empty`() =
        runBlocking {
            folder.root.resolve("reminders.json").writeText("{ nope")

            assertNull(newStore().read())
        }

    @Test
    fun `REQ-MEAL-050 unknown version reads as empty`() =
        runBlocking {
            folder.root.resolve("reminders.json").writeText("""{"version":99,"snapshot":{"memberId":"c-1","weekStart":"2026-10-19"}}""")

            assertNull(newStore().read())
        }

    @Test
    fun `REQ-MEAL-050 unreadable values read as empty`() =
        runBlocking {
            folder.root
                .resolve("reminders.json")
                .writeText("""{"version":1,"snapshot":{"memberId":"c-1","weekStart":"2026-10-19","cookState":"WAT"}}""")

            assertNull(newStore().read())
        }
}
