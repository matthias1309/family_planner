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
        CardAction.SET_DUE_DATE -> R.string.tasks_action_set_due
        CardAction.REMOVE -> R.string.tasks_action_remove
        CardAction.PUT_BACK -> R.string.tasks_action_put_back
    }

/** Aktionen im Überlaufmenü; alle anderen stehen als Buttons an der Karte. */
val CardAction.isSecondary: Boolean get() =
    this != CardAction.REPORT &&
        this != CardAction.WITHDRAW &&
        this != CardAction.ACCEPT &&
        this != CardAction.REJECT
