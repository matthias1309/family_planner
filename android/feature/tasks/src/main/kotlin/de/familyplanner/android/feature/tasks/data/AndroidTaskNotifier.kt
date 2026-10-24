package de.familyplanner.android.feature.tasks.data

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
import de.familyplanner.android.feature.tasks.R
import de.familyplanner.android.feature.tasks.TaskOpenRequests
import de.familyplanner.android.feature.tasks.domain.board.TaskNotifier
import de.familyplanner.android.feature.tasks.domain.notice.TaskNotice

internal const val CHANNEL_TASKS = "tasks"
internal const val TASK_ID_BASE = 100_000
private const val TASK_ID_MASK = 0xFFFF
private const val OPEN_TASKS_REQUEST_CODE = 1

/** Zeigt Aufgaben-Hinweise; ohne Benachrichtigungs-Berechtigung geschieht nichts. */
class AndroidTaskNotifier(
    private val context: Context,
) : TaskNotifier {
    private val manager = NotificationManagerCompat.from(context)

    override fun announce(notice: TaskNotice) {
        val (title, text) =
            when (notice) {
                is TaskNotice.Reported ->
                    context.getString(R.string.tasks_notice_reported_title, notice.childName, notice.title) to
                        context.getString(R.string.tasks_notice_reported_text)
                is TaskNotice.Assigned ->
                    context.getString(R.string.tasks_notice_assigned_title, notice.title) to
                        context.getString(R.string.tasks_notice_assigned_text)
                is TaskNotice.Accepted ->
                    context.getString(R.string.tasks_notice_accepted_title, notice.title) to
                        context.resources.getQuantityString(R.plurals.tasks_points, notice.points, notice.points)
                is TaskNotice.Rejected ->
                    context.getString(R.string.tasks_notice_rejected_title, notice.title) to
                        (notice.reason ?: context.getString(R.string.tasks_notice_rejected_text))
            }
        show(TASK_ID_BASE + (notice.cardId.hashCode() and TASK_ID_MASK), title, text)
    }

    private fun show(
        id: Int,
        title: String,
        text: String,
    ) {
        if (!canNotify()) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TASKS, context.getString(R.string.tasks_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_TASKS)
                .setSmallIcon(R.drawable.ic_stat_tasks)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openTasksIntent())
                .build()
        // Die Berechtigung ist oben geprüft (canNotify); Lint erkennt das über die Hilfsfunktion nicht.
        @Suppress("MissingPermission")
        manager.notify(id, notification)
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openTasksIntent(): PendingIntent {
        val launch =
            (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
                .putExtra(TaskOpenRequests.EXTRA_OPEN_TASKS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            OPEN_TASKS_REQUEST_CODE,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
