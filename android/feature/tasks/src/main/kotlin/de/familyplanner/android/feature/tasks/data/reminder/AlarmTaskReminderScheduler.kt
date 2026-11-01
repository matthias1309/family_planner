package de.familyplanner.android.feature.tasks.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.AlarmManagerCompat
import de.familyplanner.android.feature.tasks.domain.board.TaskReminderScheduler
import de.familyplanner.android.feature.tasks.domain.reminder.TaskReminder

internal const val EXTRA_CARD_ID = "cardId"
internal const val EXTRA_DUE_AT = "dueAt"

/**
 * Inexakte Alarme, die auch im Doze feuern (ADR-0013). Ein Alarm je Karte: Extras zählen nicht für die PendingIntent-Gleichheit,
 * daher unterscheidet eine eindeutige `data`-URI die Karten, und ein neuer Alarm mit gleicher URI ersetzt den alten.
 */
class AlarmTaskReminderScheduler(
    private val context: Context,
) : TaskReminderScheduler {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(reminders: List<TaskReminder>) {
        reminders.forEach {
            AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, it.at.toEpochMilli(), pendingIntent(it))
        }
    }

    private fun pendingIntent(reminder: TaskReminder): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, TaskReminderReceiver::class.java)
                .setData(Uri.parse("familyplanner://task-reminder/${reminder.cardId}"))
                .putExtra(EXTRA_CARD_ID, reminder.cardId)
                .putExtra(EXTRA_DUE_AT, reminder.dueAt.toEpochMilli()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
