package de.familyplanner.android.feature.tasks.data

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.familyplanner.android.feature.tasks.TaskOpenRequests
import de.familyplanner.android.feature.tasks.domain.notice.TaskNotice
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AndroidTaskNotifierTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
    private val notifier = AndroidTaskNotifier(app)

    private fun grant() = shadowOf(app).grantPermissions("android.permission.POST_NOTIFICATIONS")

    private fun texts(): Pair<String?, String?> {
        val extras = manager.allNotifications.single().extras
        return extras.getString("android.title") to extras.getCharSequence("android.text")?.toString()
    }

    @Test
    fun `REQ-TASK-030 reported notice names the child and the task`() {
        grant()

        notifier.announce(TaskNotice.Reported("t-1", "Bad putzen", "Lea"))

        assertEquals("Lea hat „Bad putzen“ erledigt" to "Bitte abnehmen.", texts())
    }

    @Test
    fun `REQ-TASK-030 assigned notice invites the child`() {
        grant()

        notifier.announce(TaskNotice.Assigned("t-1", "Bad putzen"))

        assertEquals("Neue Aufgabe: Bad putzen" to "Erledige sie, wann es passt.", texts())
    }

    @Test
    fun `REQ-TASK-030 accepted notice shows the points`() {
        grant()

        notifier.announce(TaskNotice.Accepted("t-1", "Bad putzen", 3))
        notifier.announce(TaskNotice.Accepted("t-2", "Müll", 1))

        val texts = manager.allNotifications.map { it.extras.getCharSequence("android.text")?.toString() }.toSet()
        assertEquals(setOf("+3 Punkte", "+1 Punkt"), texts)
    }

    @Test
    fun `REQ-TASK-030 rejected notice carries the reason or a default`() {
        grant()

        notifier.announce(TaskNotice.Rejected("t-1", "Zimmer", "Unter dem Bett"))
        notifier.announce(TaskNotice.Rejected("t-2", "Bad", null))

        val texts = manager.allNotifications.map { it.extras.getCharSequence("android.text")?.toString() }.toSet()
        assertEquals(setOf("Unter dem Bett", "Bitte noch einmal ansehen."), texts)
    }

    @Test
    fun `REQ-TASK-030 notice uses its own channel, id range and opens tasks`() {
        grant()

        notifier.announce(TaskNotice.Assigned("t-1", "Bad"))

        val notification = manager.allNotifications.single()
        assertEquals(CHANNEL_TASKS, notification.channelId)
        assertEquals(
            "Aufgaben",
            (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).getNotificationChannel(CHANNEL_TASKS).name,
        )
        assertTrue(manager.size() == 1)
        val pending = shadowOf(notification.contentIntent)
        assertEquals(true, pending.savedIntent.getBooleanExtra(TaskOpenRequests.EXTRA_OPEN_TASKS, false))
        assertTrue(manager.getNotification(manager.allNotifications.size.let { TASK_ID_BASE + ("t-1".hashCode() and 0xFFFF) }) != null)
    }

    @Test
    fun `REQ-TASK-030 without the notification permission nothing is shown`() {
        notifier.announce(TaskNotice.Assigned("t-1", "Bad"))

        assertTrue(manager.allNotifications.isEmpty())
    }
}
