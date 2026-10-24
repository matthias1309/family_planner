package de.familyplanner.android.feature.meals.data.reminder

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.familyplanner.android.feature.meals.domain.reminder.ReminderLimits
import de.familyplanner.android.feature.meals.domain.reminder.ReminderSync
import de.familyplanner.android.feature.meals.domain.reminder.SyncRemindersUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

private const val PERIODIC_WORK = "meal-reminder-sync-periodic"
private const val NOW_WORK = "meal-reminder-sync-now"

/** Abgleich im Hintergrund (ADR-0013): periodisch und auf Zuruf, jeweils nur mit Netzwerk. */
class WorkManagerReminderSync(
    context: Context,
) : ReminderSync {
    private val workManager = WorkManager.getInstance(context)
    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun requestNow() {
        workManager.enqueueUniqueWork(
            NOW_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderSyncWorker>().setConstraints(connected).build(),
        )
    }

    override fun schedulePeriodic() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderSyncWorker>(ReminderLimits.SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(connected)
                .build(),
        )
    }
}

class ReminderSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val sync: SyncRemindersUseCase by inject()

    // Nicht erreichbar ist kein Fehler: Der nächste Lauf versucht es erneut.
    override suspend fun doWork(): Result {
        sync()
        return Result.success()
    }
}
