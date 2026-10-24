package de.familyplanner.android.feature.meals.data

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.familyplanner.android.feature.meals.PlanOpenRequests
import de.familyplanner.android.feature.meals.data.reminder.AndroidNotifier
import de.familyplanner.android.feature.meals.domain.reminder.DeadlineReminder
import de.familyplanner.android.feature.meals.domain.reminder.StatusNotice
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AndroidNotifierTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
    private val notifier = AndroidNotifier(app)
    private val deadline = Instant.parse("2026-10-15T14:00:00Z")
    private val tuesday = LocalDate.of(2026, 10, 20)

    private fun grant() = shadowOf(app).grantPermissions("android.permission.POST_NOTIFICATIONS")

    private fun texts(): Pair<String?, String?> {
        val extras = manager.allNotifications.single().extras
        return extras.getString("android.title") to extras.getCharSequence("android.text")?.toString()
    }

    @Test
    fun `REQ-MEAL-051 reminder names the deadline and the whatsapp way`() {
        grant()

        notifier.remind(DeadlineReminder(deadline, false))

        val (title, text) = texts()
        assertEquals("Dein Kochtag: Gericht einreichen", title)
        assertTrue(text.orEmpty().contains("Do 15.10., 16:00") && text.orEmpty().contains("WhatsApp"))
    }

    @Test
    fun `REQ-MEAL-050 reminder after a rejection asks to revise`() {
        grant()

        notifier.remind(DeadlineReminder(deadline, true))

        assertEquals("Dein Kochtag: Gericht überarbeiten", texts().first)
    }

    @Test
    fun `REQ-MEAL-052 rejection notice carries the reason`() {
        grant()

        notifier.announce(StatusNotice.Rejected("d-1", "Curry", tuesday, "kein Gemüse"))

        val (title, text) = texts()
        assertEquals("„Curry“ wurde abgelehnt", title)
        assertEquals("Begründung: kein Gemüse", text)
    }

    @Test
    fun `REQ-MEAL-052 approval and submission notices are shown`() {
        grant()

        notifier.announce(StatusNotice.Approved("d-1", "Curry", tuesday))
        notifier.announce(StatusNotice.Submitted("d-2", "Pizza", tuesday, "Ben"))

        assertEquals(
            setOf("„Curry“ ist freigegeben", "Ben hat ein Gericht eingereicht"),
            manager.allNotifications
                .map {
                    it.extras.getString("android.title")
                }.toSet(),
        )
    }

    @Test
    fun `REQ-MEAL-050 tapping a notification asks to open the meal plan`() {
        grant()
        notifier.remind(DeadlineReminder(deadline, false))

        val intent = shadowOf(manager.allNotifications.single().contentIntent).savedIntent

        assertTrue(intent.getBooleanExtra(PlanOpenRequests.EXTRA_OPEN_PLAN, false))
    }

    @Test
    fun `REQ-MEAL-050 without permission nothing is shown`() {
        shadowOf(app).denyPermissions("android.permission.POST_NOTIFICATIONS")

        notifier.remind(DeadlineReminder(deadline, false))

        assertEquals(0, manager.allNotifications.size)
    }
}
