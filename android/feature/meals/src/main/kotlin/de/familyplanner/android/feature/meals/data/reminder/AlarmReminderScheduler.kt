package de.familyplanner.android.feature.meals.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import de.familyplanner.android.feature.meals.domain.reminder.Reminder
import de.familyplanner.android.feature.meals.domain.reminder.ReminderLimits
import de.familyplanner.android.feature.meals.domain.reminder.ReminderScheduler

internal const val EXTRA_WEEK_START = "weekStart"

/** Inexakte Alarme, die auch im Doze feuern (ADR-0013); je Vorlauf (3/2/1 Tage) ein Alarm, daher als Request-Code. */
class AlarmReminderScheduler(
    private val context: Context,
) : ReminderScheduler {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun replace(reminders: List<Reminder>) {
        cancelAll()
        reminders.forEach {
            AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, it.at.toEpochMilli(), pendingIntent(it))
        }
    }

    override fun cancelAll() {
        ReminderLimits.DAYS_BEFORE.forEach { daysBefore ->
            existingIntent(daysBefore)?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
    }

    private fun pendingIntent(reminder: Reminder): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminder.daysBefore.toInt(),
            alarmIntent().putExtra(EXTRA_WEEK_START, reminder.weekStart.toString()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun existingIntent(daysBefore: Long): PendingIntent? =
        PendingIntent.getBroadcast(context, daysBefore.toInt(), alarmIntent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)

    private fun alarmIntent() = Intent(context, ReminderAlarmReceiver::class.java)
}
