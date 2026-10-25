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
    private val cache: PointsCache,
    private val sessions: SessionStore,
    private val clock: Clock = Clock.system(BerlinZone),
) {
    suspend operator fun invoke(): PointsLoad {
        val session = sessions.current ?: return PointsLoad.Failed(AppError.Unauthorized)
        val fetched = fetch(session.memberId, session.role == Role.CHILD)
        return when (fetched) {
            is ApiResult.Success -> {
                val snapshot = PointsSnapshot(fetched.data.first, fetched.data.second, clock.instant())
                cache.write(session.memberId, snapshot)
                PointsLoad.Fresh(snapshot)
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

    /** Konto (nur Kinder) und Rangliste parallel; bei einem Fehler zählt der erste (Konto vor Rangliste). */
    private suspend fun fetch(
        memberId: String,
        withAccount: Boolean,
    ): ApiResult<Pair<PointsAccount?, List<RankedChild>>> =
        coroutineScope {
            val account = if (withAccount) async { repository.account(memberId, PointsLimits.HISTORY_LIMIT) } else null
            val leaderboard = async { repository.leaderboard() }
            val accountResult = account?.await()
            val leaderboardResult = leaderboard.await()
            when {
                accountResult is ApiResult.Failure -> accountResult
                leaderboardResult is ApiResult.Failure -> leaderboardResult
                else -> {
                    val ranked = (leaderboardResult as ApiResult.Success).data
                    ApiResult.Success((accountResult as ApiResult.Success?)?.data to ranked)
                }
            }
        }
}

/** Konto eines Kindes für Eltern, ohne lokalen Stand. */
class LoadChildAccountUseCase(
    private val repository: PointsRepository,
) {
    suspend operator fun invoke(memberId: String): ApiResult<PointsAccount> = repository.account(memberId, PointsLimits.HISTORY_LIMIT)
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
