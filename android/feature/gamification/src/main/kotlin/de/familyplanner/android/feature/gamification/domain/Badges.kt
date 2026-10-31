package de.familyplanner.android.feature.gamification.domain

import java.time.Instant

data class BadgeProgress(
    val current: Int,
    val target: Int,
)

data class Badge(
    val id: String,
    val emoji: String?,
    val title: String?,
    val description: String?,
    val hint: String?,
    val secret: Boolean,
    val earned: Boolean,
    val awardedAt: Instant?,
    val progress: BadgeProgress?,
) {
    /** Geheim und noch nicht erhalten: Der Server liefert weder Titel noch Bedingung. */
    val masked: Boolean get() = title == null
}

data class BadgeOverview(
    val memberId: String,
    val earnedCount: Int,
    val totalCount: Int,
    val items: List<Badge>,
)

/** Badges, die hier erhalten sind, in [previous] aber nicht; ohne [previous] (Basislinie) leer. */
fun BadgeOverview.newlyEarned(previous: BadgeOverview?): List<Badge> {
    if (previous == null) return emptyList()
    val before =
        previous.items
            .filter { it.earned }
            .map { it.id }
            .toSet()
    return items.filter { it.earned && it.id !in before }
}
