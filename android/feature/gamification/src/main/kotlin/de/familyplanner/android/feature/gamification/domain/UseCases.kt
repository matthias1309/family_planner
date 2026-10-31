package de.familyplanner.android.feature.gamification.domain

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.ui.BerlinZone
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Clock

/** Verbindungsprobleme, bei denen der gespeicherte Stand gezeigt wird (ADR-0011). */
private fun AppError.allowsCachedPoints() = this == AppError.Network || this == AppError.Certificate || this == AppError.Server

class LoadPointsUseCase(
    private val repository: PointsRepository,
    private val rewards: RewardRepository,
    private val cache: PointsCache,
    private val sessions: SessionStore,
    private val clock: Clock = Clock.system(BerlinZone),
) {
    suspend operator fun invoke(): PointsLoad {
        val session = sessions.current ?: return PointsLoad.Failed(AppError.Unauthorized)
        val fetched = fetch(session.memberId, session.role == Role.CHILD)
        return when (fetched) {
            is ApiResult.Success -> {
                val data = fetched.data
                val previous = cache.read(session.memberId)?.badges
                val snapshot = PointsSnapshot(data.account, data.leaderboard, clock.instant(), data.badges, data.savingsGoal)
                cache.write(session.memberId, snapshot)
                PointsLoad.Fresh(snapshot, data.badges?.newlyEarned(previous).orEmpty())
            }
            is ApiResult.Failure -> failed(session.memberId, fetched.error)
        }
    }

    private suspend fun failed(
        memberId: String,
        error: AppError,
    ): PointsLoad {
        if (error == AppError.Unauthorized) cache.clear()
        val cached = if (error.allowsCachedPoints()) cache.read(memberId) else null
        return if (cached != null) PointsLoad.Cached(cached, error) else PointsLoad.Failed(error)
    }

    /** Konto, Badges, Sparziel (nur Kinder) und Rangliste parallel; bei Fehlern zählt der erste in dieser Reihenfolge. */
    private suspend fun fetch(
        memberId: String,
        isChild: Boolean,
    ): ApiResult<Fetched> =
        coroutineScope {
            val account = if (isChild) async { repository.account(memberId, PointsLimits.HISTORY_LIMIT) } else null
            val leaderboard = async { repository.leaderboard() }
            val badges = if (isChild) async { repository.badges(memberId) } else null
            val goal = if (isChild) async { rewards.savingsGoal(memberId) } else null
            val accountResult = account?.await()
            val leaderboardResult = leaderboard.await()
            val badgesResult = badges?.await()
            val goalResult = goal?.await()
            val failure =
                listOfNotNull(
                    accountResult,
                    leaderboardResult,
                    badgesResult,
                    goalResult,
                ).filterIsInstance<ApiResult.Failure>().firstOrNull()
            if (failure != null) return@coroutineScope failure
            ApiResult.Success(
                Fetched(
                    account = (accountResult as ApiResult.Success?)?.data,
                    leaderboard = (leaderboardResult as ApiResult.Success).data,
                    badges = (badgesResult as ApiResult.Success?)?.data,
                    savingsGoal = (goalResult as ApiResult.Success?)?.data,
                ),
            )
        }

    private data class Fetched(
        val account: PointsAccount?,
        val leaderboard: List<RankedChild>,
        val badges: BadgeOverview?,
        val savingsGoal: SavingsGoal?,
    )
}

/** Konto eines Kindes für Eltern, ohne lokalen Stand. */
class LoadChildAccountUseCase(
    private val repository: PointsRepository,
) {
    suspend operator fun invoke(memberId: String): ApiResult<PointsAccount> = repository.account(memberId, PointsLimits.HISTORY_LIMIT)
}

/** Badges eines Mitglieds (Geschwister, Eltern-Sicht auf ein Kind), ohne lokalen Stand. */
class LoadBadgesUseCase(
    private val repository: PointsRepository,
) {
    suspend operator fun invoke(memberId: String): ApiResult<BadgeOverview> = repository.badges(memberId)
}

class BookPointsUseCase(
    private val repository: PointsRepository,
) {
    suspend operator fun invoke(
        memberId: String,
        amount: Int,
        reason: String,
    ): ApiResult<LedgerEntry> {
        validateManualEntry(amount, reason)?.let { return ApiResult.Failure(it) }
        return repository.book(memberId, amount, reason.trim())
    }
}

class CookingPointsUseCase(
    private val repository: PointsRepository,
) {
    suspend fun load(): ApiResult<Int> = repository.cookingPoints()

    suspend fun save(points: Int): ApiResult<Int> {
        validateCookingPoints(points)?.let { return ApiResult.Failure(it) }
        return repository.updateCookingPoints(points)
    }
}
