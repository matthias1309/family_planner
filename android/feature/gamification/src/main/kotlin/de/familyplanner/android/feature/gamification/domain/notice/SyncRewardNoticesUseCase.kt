package de.familyplanner.android.feature.gamification.domain.notice

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.core.data.session.SessionState
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.feature.gamification.domain.PointsRepository
import de.familyplanner.android.feature.gamification.domain.Redemption
import de.familyplanner.android.feature.gamification.domain.RedemptionNoticeStore
import de.familyplanner.android.feature.gamification.domain.RedemptionStatus
import de.familyplanner.android.feature.gamification.domain.RewardLimits
import de.familyplanner.android.feature.gamification.domain.RewardNotifier
import de.familyplanner.android.feature.gamification.domain.RewardRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

enum class RewardSyncOutcome { SYNCED, UNREACHABLE, SIGNED_OUT }

/** Angemeldete Session, nachdem sie aus dem Speicher geladen ist (im Worker evtl. noch `Loading`). */
private suspend fun SessionStore.awaitSession(): Session? = (state.first { it !is SessionState.Loading } as? SessionState.SignedIn)?.session

/** Gleicht die Einlösungen mit dem Server ab: Hinweise und Abgleich-Stand (REQ-GAME-042, REQ-GAME-043). */
class SyncRewardNoticesUseCase(
    private val sessions: SessionStore,
    private val repository: RewardRepository,
    private val points: PointsRepository,
    private val store: RedemptionNoticeStore,
    private val notifier: RewardNotifier,
    private val clock: Clock = Clock.system(BerlinZone),
) {
    // Periodischer und sofortiger Abgleich können gleichzeitig laufen; sonst gäbe es doppelte Meldungen.
    private val mutex = Mutex()

    suspend operator fun invoke(): RewardSyncOutcome =
        mutex.withLock {
            val session = sessions.awaitSession() ?: return@withLock signOut()
            val isParent = session.role == Role.PARENT
            val current =
                when (val result = fetch(isParent)) {
                    is ApiResult.Success -> result.data
                    is ApiResult.Failure ->
                        return@withLock if (result.error == AppError.Unauthorized) signOut() else RewardSyncOutcome.UNREACHABLE
                }
            val previous = store.read(session.memberId)
            val names = if (isParent && previous != null) childNames() else emptyMap()
            rewardNotices(isParent, previous, current, names).forEach(notifier::announce)
            store.write(session.memberId, RedemptionSnapshot(current.associate { it.id to it.status }, clock.instant()))
            RewardSyncOutcome.SYNCED
        }

    private suspend fun fetch(isParent: Boolean): ApiResult<List<Redemption>> =
        if (isParent) {
            repository.redemptions(RedemptionStatus.PENDING, RewardLimits.PENDING_LIMIT)
        } else {
            repository.redemptions(null, RewardLimits.SYNC_HISTORY_LIMIT)
        }

    private suspend fun childNames(): Map<String, String> =
        (points.leaderboard() as? ApiResult.Success)?.data.orEmpty().associate { it.memberId to it.name }

    private suspend fun signOut(): RewardSyncOutcome {
        store.clear()
        return RewardSyncOutcome.SIGNED_OUT
    }
}
