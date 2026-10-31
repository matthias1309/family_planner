package de.familyplanner.gamification.rewards

import de.familyplanner.core.error.AppException
import de.familyplanner.core.error.validatedName

const val REWARD_TITLE_MAX = 40
const val REWARD_EMOJI_MAX = 16
const val REWARD_PRICE_MIN = 1
const val REWARD_PRICE_MAX = 10_000

@JvmInline
value class RewardId(
    val value: String,
)

/** A real reward parents offer for points (REQ-GAME-040); [archived] = deactivated, no longer redeemable. */
data class Reward(
    val id: RewardId,
    val title: String,
    val emoji: String?,
    val price: Int,
    val archived: Boolean,
)

/** What a caller wants to store; validated by [validated]. */
data class RewardInput(
    val title: String,
    val emoji: String?,
    val price: Int,
)

/** Trims the title, turns a blank emoji into `null` and checks all limits. */
fun validated(input: RewardInput): RewardInput {
    val title = validatedName(input.title, "Titel", REWARD_TITLE_MAX)
    val emoji = input.emoji?.trim()?.takeIf { it.isNotEmpty() }
    if (emoji != null && emoji.length > REWARD_EMOJI_MAX) throw AppException.Validation("Symbol ist zu lang")
    if (input.price !in REWARD_PRICE_MIN..REWARD_PRICE_MAX) {
        throw AppException.Validation("Preis muss zwischen $REWARD_PRICE_MIN und $REWARD_PRICE_MAX liegen")
    }
    return RewardInput(title, emoji, input.price)
}
