package de.familyplanner.android.feature.meals.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.familyplanner.android.feature.meals.data.parseDate
import de.familyplanner.android.feature.meals.domain.reminder.ReminderSync
import de.familyplanner.android.feature.meals.domain.reminder.RestoreRemindersUseCase
import de.familyplanner.android.feature.meals.domain.reminder.ShowDueReminderUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private fun BroadcastReceiver.launchAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Ein Erinnerungsalarm ist fällig. */
class ReminderAlarmReceiver :
    BroadcastReceiver(),
    KoinComponent {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val weekStart = parseDate(intent.getStringExtra(EXTRA_WEEK_START)) ?: return
        launchAsync { get<ShowDueReminderUseCase>()(weekStart) }
    }
}

/** Alarme gehen bei Neustart und App-Update verloren: aus dem lokalen Stand neu einplanen, dann abgleichen. */
class RemindersRestoreReceiver :
    BroadcastReceiver(),
    KoinComponent {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        launchAsync {
            get<RestoreRemindersUseCase>()()
            get<ReminderSync>().requestNow()
        }
    }
}
