package de.familyplanner.android.feature.tasks.ui

import androidx.annotation.StringRes
import de.familyplanner.android.feature.tasks.R
import de.familyplanner.android.feature.tasks.domain.board.CardAction

@StringRes
fun CardAction.labelRes(): Int =
    when (this) {
        CardAction.REPORT -> R.string.tasks_action_report
        CardAction.WITHDRAW -> R.string.tasks_action_withdraw
        CardAction.ACCEPT -> R.string.tasks_action_accept
        CardAction.REJECT -> R.string.tasks_action_reject
        CardAction.ACCEPT_DIRECTLY -> R.string.tasks_action_accept_directly
        CardAction.REMOVE -> R.string.tasks_action_remove
    }

/** Aktionen im Überlaufmenü; alle anderen stehen als Buttons an der Karte. */
val CardAction.isSecondary: Boolean get() = this == CardAction.ACCEPT_DIRECTLY || this == CardAction.REMOVE
