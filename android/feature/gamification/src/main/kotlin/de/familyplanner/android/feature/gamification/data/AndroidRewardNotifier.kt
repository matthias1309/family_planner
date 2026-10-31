package de.familyplanner.android.feature.gamification.data

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
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.RewardOpenRequests
import de.familyplanner.android.feature.gamification.domain.RewardNotifier
import de.familyplanner.android.feature.gamification.domain.notice.RewardNotice

internal const val CHANNEL_REWARDS = "rewards"
internal const val REWARD_ID_BASE = 200_000
private const val REWARD_ID_MASK = 0xFFFF
private const val OPEN_REWARDS_REQUEST_CODE = 2

/** Zeigt Belohnungs-Hinweise; ohne Benachrichtigungs-Berechtigung geschieht nichts. */
class AndroidRewardNotifier(
    private val context: Context,
) : RewardNotifier {
    private val manager = NotificationManagerCompat.from(context)

    override fun announce(notice: RewardNotice) {
        val (title, text) =
            when (notice) {
                is RewardNotice.Requested ->
                    context.getString(
                        R.string.game_notice_requested_title,
                        notice.childName ?: context.getString(R.string.game_notice_someone),
                        label(notice.emoji, notice.title),
                    ) to context.getString(R.string.game_notice_requested_text)
                is RewardNotice.Fulfilled ->
                    context.getString(R.string.game_notice_fulfilled_title, label(notice.emoji, notice.title)) to
                        context.getString(R.string.game_notice_fulfilled_text)
                is RewardNotice.Rejected ->
                    context.getString(R.string.game_notice_rejected_title, label(notice.emoji, notice.title)) to
                        (notice.reason ?: context.getString(R.string.game_notice_rejected_text))
            }
        show(REWARD_ID_BASE + (notice.redemptionId.hashCode() and REWARD_ID_MASK), title, text)
    }

    private fun label(
        emoji: String?,
        title: String,
    ) = if (emoji.isNullOrBlank()) title else "$emoji $title"

    private fun show(
        id: Int,
        title: String,
        text: String,
    ) {
        if (!canNotify()) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REWARDS, context.getString(R.string.game_channel_rewards), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_REWARDS)
                .setSmallIcon(R.drawable.ic_stat_rewards)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openRewardsIntent())
                .build()
        // Die Berechtigung ist oben geprüft (canNotify); Lint erkennt das über die Hilfsfunktion nicht.
        @Suppress("MissingPermission")
        manager.notify(id, notification)
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openRewardsIntent(): PendingIntent {
        val launch =
            (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
                .putExtra(RewardOpenRequests.EXTRA_OPEN_REWARDS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            OPEN_REWARDS_REQUEST_CODE,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
