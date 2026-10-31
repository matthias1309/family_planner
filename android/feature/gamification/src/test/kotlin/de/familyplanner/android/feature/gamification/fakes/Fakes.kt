package de.familyplanner.android.feature.gamification.fakes

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.feature.gamification.domain.Badge
import de.familyplanner.android.feature.gamification.domain.BadgeOverview
import de.familyplanner.android.feature.gamification.domain.BadgeProgress
import de.familyplanner.android.feature.gamification.domain.EntryKind
import de.familyplanner.android.feature.gamification.domain.LedgerEntry
import de.familyplanner.android.feature.gamification.domain.PointsAccount
import de.familyplanner.android.feature.gamification.domain.PointsCache
import de.familyplanner.android.feature.gamification.domain.PointsRepository
import de.familyplanner.android.feature.gamification.domain.PointsSnapshot
import de.familyplanner.android.feature.gamification.domain.RankedChild
import java.time.Instant

val parentSession = Session("tok", "p-1", "Anna", Role.PARENT, "d-1")
val childSession = Session("tok", "c-1", "Lea", Role.CHILD, "d-2")

val noon: Instant = Instant.parse("2026-10-25T10:00:00Z")

val networkDown = ApiResult.Failure(AppError.Network)
val serverDown = ApiResult.Failure(AppError.Server)

fun entry(
    id: String,
    amount: Int = 3,
    title: String = "Bad putzen",
    kind: EntryKind = EntryKind.TASK,
    note: String? = null,
) = LedgerEntry(id, kind, amount, title, note, noon)

fun account(
    memberId: String = "c-1",
    collected: Int = 42,
    balance: Int = 42,
    vararg entries: LedgerEntry,
) = PointsAccount(memberId, collected, balance, entries.toList())

val leaderboard =
    listOf(
        RankedChild("c-2", "Ben", 1, 57),
        RankedChild("c-1", "Lea", 2, 42),
        RankedChild("c-3", "Mia", 3, 12),
    )

@Suppress("LongParameterList")
fun badge(
    id: String,
    earned: Boolean = false,
    title: String? = "Fleißig",
    progress: BadgeProgress? = BadgeProgress(7, 10),
    awardedAt: Instant? = if (earned) noon else null,
    secret: Boolean = false,
    hint: String? = null,
    emoji: String? = if (title == null) null else "✅",
) = Badge(id, emoji, title, if (title == null) null else "10 Aufgaben erledigen", hint, secret, earned, awardedAt, progress)

fun overview(
    vararg items: Badge,
    memberId: String = "c-1",
) = BadgeOverview(memberId, items.count { it.earned }, 12, items.toList())

fun snapshot(
    account: PointsAccount? = account(entries = arrayOf(entry("e-1"))),
    leaderboard: List<RankedChild> = de.familyplanner.android.feature.gamification.fakes.leaderboard,
    syncedAt: Instant = noon,
    badges: BadgeOverview? = null,
) = PointsSnapshot(account, leaderboard, syncedAt, badges)

class FakePointsRepository : PointsRepository {
    var leaderboardResult: ApiResult<List<RankedChild>> = ApiResult.Success(leaderboard)
    var accountResult: ApiResult<PointsAccount> = ApiResult.Success(account(entries = arrayOf(entry("e-1"))))
    var badgesResult: ApiResult<BadgeOverview> = ApiResult.Success(overview(badge("first-task", earned = true), badge("tasks-10")))
    var bookResult: ApiResult<LedgerEntry> = ApiResult.Success(entry("e-new"))
    var cookingResult: ApiResult<Int> = ApiResult.Success(3)
    var updateResult: ApiResult<Int>? = null

    val accountCalls = mutableListOf<Pair<String, Int>>()
    val badgesCalls = mutableListOf<String>()
    val bookCalls = mutableListOf<Triple<String, Int, String>>()
    val updateCalls = mutableListOf<Int>()
    var leaderboardCalls = 0

    override suspend fun leaderboard(): ApiResult<List<RankedChild>> {
        leaderboardCalls++
        return leaderboardResult
    }

    override suspend fun account(
        memberId: String,
        limit: Int,
    ): ApiResult<PointsAccount> {
        accountCalls += memberId to limit
        return accountResult
    }

    override suspend fun badges(memberId: String): ApiResult<BadgeOverview> {
        badgesCalls += memberId
        return badgesResult
    }

    override suspend fun book(
        memberId: String,
        amount: Int,
        reason: String,
    ): ApiResult<LedgerEntry> {
        bookCalls += Triple(memberId, amount, reason)
        return bookResult
    }

    override suspend fun cookingPoints(): ApiResult<Int> = cookingResult

    override suspend fun updateCookingPoints(points: Int): ApiResult<Int> {
        updateCalls += points
        return updateResult ?: ApiResult.Success(points)
    }
}

class InMemoryPointsCache : PointsCache {
    var owner: String? = null
    var stored: PointsSnapshot? = null
    var writes = 0
    var cleared = 0

    override suspend fun read(memberId: String): PointsSnapshot? = stored.takeIf { owner == memberId }

    override suspend fun write(
        memberId: String,
        snapshot: PointsSnapshot,
    ) {
        writes++
        owner = memberId
        stored = snapshot
    }

    override suspend fun clear() {
        cleared++
        owner = null
        stored = null
    }
}
