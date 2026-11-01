package de.familyplanner.android.feature.gamification.ui

import androidx.annotation.StringRes
import de.familyplanner.android.feature.gamification.R
import de.familyplanner.android.feature.gamification.domain.EntryKind
import de.familyplanner.android.feature.gamification.domain.RedemptionStatus
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarRubric
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarSlot
import de.familyplanner.android.feature.gamification.domain.avatar.ShopCategory

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

@StringRes
fun RedemptionStatus.labelRes(): Int =
    when (this) {
        RedemptionStatus.PENDING -> R.string.game_status_pending
        RedemptionStatus.FULFILLED -> R.string.game_status_fulfilled
        RedemptionStatus.REJECTED -> R.string.game_status_rejected
    }

/** „🎢 Freizeitpark“; ohne Symbol nur der Titel. */
fun rewardLabel(
    emoji: String?,
    title: String,
): String = if (emoji.isNullOrBlank()) title else "$emoji $title"

// Eine Zeile je Slot; Aufteilen würde die Zuordnung nur verstreuen.
@Suppress("CyclomaticComplexMethod")
@StringRes
fun AvatarRubric.labelRes(): Int =
    when (slot) {
        AvatarSlot.SKIN -> R.string.game_rubric_skin
        AvatarSlot.TOP -> if (category == ShopCategory.HEADWEAR) R.string.game_rubric_headwear else R.string.game_rubric_hair
        AvatarSlot.HAIR_COLOR -> R.string.game_rubric_hair_color
        AvatarSlot.HAT_COLOR -> R.string.game_rubric_hat_color
        AvatarSlot.EYES -> R.string.game_rubric_eyes
        AvatarSlot.EYEBROWS -> R.string.game_rubric_eyebrows
        AvatarSlot.MOUTH -> R.string.game_rubric_mouth
        AvatarSlot.CLOTHES -> R.string.game_rubric_clothes
        AvatarSlot.CLOTHES_COLOR -> R.string.game_rubric_clothes_color
        AvatarSlot.CLOTHES_GRAPHIC -> R.string.game_rubric_graphic
        AvatarSlot.ACCESSORIES -> R.string.game_rubric_accessories
        AvatarSlot.FACIAL_HAIR -> R.string.game_rubric_facial_hair
        AvatarSlot.BACKGROUND -> R.string.game_rubric_background
    }
