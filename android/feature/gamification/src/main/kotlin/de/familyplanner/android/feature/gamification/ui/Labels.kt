package de.familyplanner.android.feature.gamification.ui

import androidx.annotation.StringRes
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.domain.EntryKind

@StringRes
fun EntryKind.labelRes(): Int =
    when (this) {
        EntryKind.TASK -> R.string.game_kind_task
        EntryKind.COOKING -> R.string.game_kind_cooking
        EntryKind.MANUAL -> R.string.game_kind_manual
        EntryKind.SHOP -> R.string.game_kind_shop
        EntryKind.REWARD -> R.string.game_kind_reward
        EntryKind.REWARD_REFUND -> R.string.game_kind_reward_refund
    }

private const val MINUS = "−"

/** `+3` / `−2` (echtes Minuszeichen). */
fun formatAmount(amount: Int): String = if (amount < 0) "$MINUS${-amount}" else "+$amount"
