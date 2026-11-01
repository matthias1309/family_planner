package de.familyplanner.android.feature.tasks.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.familyplanner.android.feature.tasks.domain.board.TaskSync
import de.familyplanner.android.feature.tasks.domain.reminder.RestoreTaskRemindersUseCase
import de.familyplanner.android.feature.tasks.domain.reminder.ShowTaskReminderUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.time.Instant

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

/** Ein Fristerinnerungs-Alarm ist fällig; veraltete Alarme verwirft der Use Case. */
class TaskReminderReceiver :
    BroadcastReceiver(),
    KoinComponent {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val cardId = intent.getStringExtra(EXTRA_CARD_ID) ?: return
        if (!intent.hasExtra(EXTRA_DUE_AT)) return
        val dueAt = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_DUE_AT, 0))
        launchAsync { get<ShowTaskReminderUseCase>()(cardId, dueAt) }
    }
}

/** Alarme gehen bei Neustart und App-Update verloren: aus dem lokalen Stand neu einplanen, dann abgleichen. */
class TaskRemindersRestoreReceiver :
    BroadcastReceiver(),
    KoinComponent {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        launchAsync {
            get<RestoreTaskRemindersUseCase>()()
            get<TaskSync>().requestNow()
        }
    }
}
