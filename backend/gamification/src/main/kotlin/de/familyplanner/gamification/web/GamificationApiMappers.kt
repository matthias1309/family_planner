package de.familyplanner.gamification.web

import de.familyplanner.api.model.LedgerEntryKind
import de.familyplanner.core.auth.DevicePrincipal
import de.familyplanner.gamification.account.Account
import de.familyplanner.gamification.account.Actor
import de.familyplanner.gamification.account.LeaderboardEntry
import de.familyplanner.gamification.ledger.LedgerEntry
import de.familyplanner.gamification.settings.GameSettings
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

fun GameSettings.toApi() = ApiGameSettings(cookingPoints = cookingPoints)
