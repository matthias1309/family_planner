package de.familyplanner.gamification.web

import de.familyplanner.api.model.BadgeProgress
import de.familyplanner.api.model.LedgerEntryKind
import de.familyplanner.core.auth.DevicePrincipal
import de.familyplanner.gamification.account.Account
import de.familyplanner.gamification.account.Actor
import de.familyplanner.gamification.account.LeaderboardEntry
import de.familyplanner.gamification.badges.BadgeOverview
import de.familyplanner.gamification.badges.BadgeView
import de.familyplanner.gamification.ledger.LedgerEntry
import de.familyplanner.gamification.settings.GameSettings
import de.familyplanner.api.model.Badge as ApiBadge
import de.familyplanner.api.model.BadgeOverview as ApiBadgeOverview
import de.familyplanner.api.model.GameSettings as ApiGameSettings
import de.familyplanner.api.model.LeaderboardEntry as ApiLeaderboardEntry
import de.familyplanner.api.model.LedgerEntry as ApiLedgerEntry
import de.familyplanner.api.model.PointsAccount as ApiPointsAccount

fun DevicePrincipal.toActor() = Actor(memberId, role)

fun LedgerEntry.toApi() =
    ApiLedgerEntry(
        id = id.value,
        kind = LedgerEntryKind.valueOf(kind.name),
        amount = amount,
        title = title,
        note = note,
        bookedAt = bookedAt.toString(),
    )

fun Account.toApi() =
    ApiPointsAccount(
        memberId = memberId.value,
        collected = totals.collected,
        balance = totals.balance,
        items = entries.map { it.toApi() },
    )

fun LeaderboardEntry.toApi() = ApiLeaderboardEntry(memberId = memberId.value, name = name, rank = rank, collected = collected)

fun BadgeView.toApi() =
    ApiBadge(
        id = badge.id,
        secret = badge.secret,
        earned = awardedAt != null,
        emoji = badge.emoji.takeUnless { masked },
        title = badge.title.takeUnless { masked },
        description = badge.description.takeUnless { masked },
        hint = badge.hint,
        awardedAt = awardedAt?.toString(),
        progress = progress?.let { BadgeProgress(current = it, target = badge.target) },
    )

fun BadgeOverview.toApi() =
    ApiBadgeOverview(memberId = memberId.value, earnedCount = earnedCount, totalCount = totalCount, items = items.map { it.toApi() })

fun GameSettings.toApi() = ApiGameSettings(cookingPoints = cookingPoints)
