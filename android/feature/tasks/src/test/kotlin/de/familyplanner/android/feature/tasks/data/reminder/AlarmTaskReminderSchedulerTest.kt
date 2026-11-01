package de.familyplanner.android.feature.tasks.data.reminder

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.familyplanner.android.feature.tasks.domain.reminder.TaskReminder
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class AlarmTaskReminderSchedulerTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val alarms = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
    private val scheduler = AlarmTaskReminderScheduler(app)
    private val due = Instant.parse("2026-11-01T17:00:00Z")

    private fun reminder(
        cardId: String,
        at: String,
    ) = TaskReminder(cardId, "Aufgabe", due, Instant.parse(at))

    @Test
    fun `REQ-TASK-031 two cards get two alarms`() {
        scheduler.schedule(listOf(reminder("a", "2026-11-01T15:00:00Z"), reminder("b", "2026-11-01T14:00:00Z")))

        assertEquals(2, alarms.scheduledAlarms.size)
    }

    @Test
    fun `REQ-TASK-031 same card reschedules a single alarm`() {
        scheduler.schedule(listOf(reminder("a", "2026-11-01T15:00:00Z")))
        scheduler.schedule(listOf(reminder("a", "2026-11-01T14:00:00Z")))

        assertEquals(Instant.parse("2026-11-01T14:00:00Z").toEpochMilli(), alarms.scheduledAlarms.single().triggerAtMs)
    }
}
