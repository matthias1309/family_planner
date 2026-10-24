package de.familyplanner.android.feature.tasks.data

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
import de.familyplanner.android.feature.tasks.domain.board.TaskLimits
import de.familyplanner.android.feature.tasks.domain.board.TaskSync
import de.familyplanner.android.feature.tasks.domain.notice.SyncTaskNoticesUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

private const val PERIODIC_WORK = "task-sync-periodic"
private const val NOW_WORK = "task-sync-now"

/** Abgleich im Hintergrund (ADR-0013): periodisch und auf Zuruf, jeweils nur mit Netzwerk. */
class WorkManagerTaskSync(
    context: Context,
) : TaskSync {
    private val workManager = WorkManager.getInstance(context)
    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun requestNow() {
        workManager.enqueueUniqueWork(
            NOW_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<TaskSyncWorker>().setConstraints(connected).build(),
        )
    }

    override fun schedulePeriodic() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TaskSyncWorker>(TaskLimits.SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(connected)
                .build(),
        )
    }
}

class TaskSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val sync: SyncTaskNoticesUseCase by inject()

    // Nicht erreichbar ist kein Fehler: Der nächste Lauf versucht es erneut.
    override suspend fun doWork(): Result {
        sync()
        return Result.success()
    }
}
