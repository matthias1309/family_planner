package de.familyplanner.android.feature.meals.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.familyplanner.android.core.ui.formatDate
import de.familyplanner.android.feature.meals.PlanOpenRequests
import de.familyplanner.android.feature.meals.R
import de.familyplanner.android.feature.meals.domain.reminder.DeadlineReminder
import de.familyplanner.android.feature.meals.domain.reminder.Notifier
import de.familyplanner.android.feature.meals.domain.reminder.StatusNotice
import de.familyplanner.android.feature.meals.ui.formatDeadline
import de.familyplanner.android.feature.meals.ui.weekdayShort
import java.time.LocalDate

internal const val CHANNEL_REMINDERS = "reminders"
internal const val CHANNEL_STATUS = "status"
internal const val REMINDER_NOTIFICATION_ID = 1
private const val STATUS_ID_BASE = 1_000
private const val STATUS_ID_MASK = 0xFFFF

/** Zeigt Erinnerungen und Statusmeldungen; ohne Benachrichtigungs-Berechtigung geschieht nichts. */
class AndroidNotifier(
    private val context: Context,
) : Notifier {
    private val manager = NotificationManagerCompat.from(context)

    override fun remind(reminder: DeadlineReminder) {
        val title = if (reminder.rejected) R.string.reminder_title_revise else R.string.reminder_title_submit
        val text = context.getString(R.string.reminder_text, formatDeadline(reminder.deadline))
        show(REMINDER_NOTIFICATION_ID, CHANNEL_REMINDERS, context.getString(title), text)
    }

    override fun announce(notice: StatusNotice) {
        val (title, text) =
            when (notice) {
                is StatusNotice.Approved ->
                    context.getString(R.string.notice_approved_title, notice.dishName) to
                        context.getString(R.string.notice_approved_text, dayLabel(notice.date))
                is StatusNotice.Rejected ->
                    context.getString(R.string.notice_rejected_title, notice.dishName) to
                        context.getString(R.string.notice_rejected_text, notice.reason.orEmpty())
                is StatusNotice.Submitted ->
                    context.getString(R.string.notice_submitted_title, notice.cookName) to
                        context.getString(R.string.notice_submitted_text, notice.dishName, dayLabel(notice.date))
            }
        show(STATUS_ID_BASE + (notice.dishId.hashCode() and STATUS_ID_MASK), CHANNEL_STATUS, title, text)
    }

    private fun dayLabel(date: LocalDate) = "${weekdayShort(date.dayOfWeek)} ${formatDate(date)}"

    private fun show(
        id: Int,
        channel: String,
        title: String,
        text: String,
    ) {
        if (!canNotify()) return
        createChannels()
        val notification =
            NotificationCompat
                .Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_meals)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openPlanIntent())
                .build()
        // Die Berechtigung ist oben geprüft (canNotify); Lint erkennt das über die Hilfsfunktion nicht.
        @Suppress("MissingPermission")
        manager.notify(id, notification)
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannels() {
        manager.createNotificationChannel(channel(CHANNEL_REMINDERS, R.string.reminder_channel, NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(channel(CHANNEL_STATUS, R.string.status_channel, NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun channel(
        id: String,
        name: Int,
        importance: Int,
    ) = NotificationChannel(id, context.getString(name), importance)

    private fun openPlanIntent(): PendingIntent {
        val launch =
            (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
                .putExtra(PlanOpenRequests.EXTRA_OPEN_PLAN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
