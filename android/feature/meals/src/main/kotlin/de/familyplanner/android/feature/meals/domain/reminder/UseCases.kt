package de.familyplanner.android.feature.meals.domain.reminder

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.core.data.session.SessionState
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.feature.meals.domain.attendance.currentPlanWeek
import de.familyplanner.android.feature.meals.domain.cooking.CookState
import de.familyplanner.android.feature.meals.domain.cooking.CookStatus
import de.familyplanner.android.feature.meals.domain.cooking.CookingRepository
import de.familyplanner.android.feature.meals.domain.plan.MealPlanRepository
import de.familyplanner.android.feature.meals.domain.plan.WeekPlan
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate

/** Angemeldete Session, nachdem sie aus dem Speicher geladen ist (im Worker/Receiver evtl. noch `Loading`). */
private suspend fun SessionStore.awaitSession(): Session? = (state.first { it !is SessionState.Loading } as? SessionState.SignedIn)?.session

/** Was der Abgleich vom Server holt; Kinder lesen ihren Status, Eltern die Namen der Mitglieder. */
private data class Remote(
    val plan: WeekPlan,
    val cook: CookStatus?,
    val names: Map<String, String>,
)

/** Gleicht die Planwoche mit dem Server ab: Statusmeldungen, Erinnerungen, lokaler Stand (REQ-MEAL-050/052/053). */
class SyncRemindersUseCase(
    private val sessions: SessionStore,
    private val cooking: CookingRepository,
    private val plan: MealPlanRepository,
    private val store: ReminderStore,
    private val scheduler: ReminderScheduler,
    private val notifier: Notifier,
    private val clock: Clock = Clock.system(BerlinZone),
) {
    // Periodischer und sofortiger Abgleich können gleichzeitig laufen; sonst gäbe es doppelte Meldungen.
    private val mutex = Mutex()

    suspend operator fun invoke(): SyncOutcome =
        mutex.withLock {
            val session = sessions.awaitSession() ?: return@withLock signOut()
            val now = clock.instant()
            val week = currentPlanWeek(now)
            val remote =
                when (val result = load(session, week)) {
                    is ApiResult.Success -> result.data
                    is ApiResult.Failure ->
                        return@withLock if (result.error == AppError.Unauthorized) signOut() else SyncOutcome.UNREACHABLE
                }
            apply(session, week, remote)
            SyncOutcome.SYNCED
        }

    private suspend fun load(
        session: Session,
        week: LocalDate,
    ): ApiResult<Remote> = if (session.role == Role.PARENT) loadParent(week) else loadChild(session.memberId, week)

    private suspend fun loadChild(
        memberId: String,
        week: LocalDate,
    ): ApiResult<Remote> =
        coroutineScope {
            val status = async { cooking.status(week) }
            val weekPlan = async { plan.week(week) }
            val statusResult = status.await()
            val planResult = weekPlan.await()
            if (statusResult !is ApiResult.Success) return@coroutineScope statusResult as ApiResult.Failure
            if (planResult !is ApiResult.Success) return@coroutineScope planResult as ApiResult.Failure
            ApiResult.Success(Remote(planResult.data, statusResult.data.cooks.firstOrNull { it.memberId == memberId }, emptyMap()))
        }

    private suspend fun loadParent(week: LocalDate): ApiResult<Remote> =
        coroutineScope {
            val weekPlan = async { plan.week(week) }
            val members = async { plan.members() }
            val planResult = weekPlan.await()
            val membersResult = members.await()
            if (planResult !is ApiResult.Success) return@coroutineScope planResult as ApiResult.Failure
            if (membersResult !is ApiResult.Success) return@coroutineScope membersResult as ApiResult.Failure
            ApiResult.Success(Remote(planResult.data, null, membersResult.data.associate { it.id to it.name }))
        }

    private suspend fun apply(
        session: Session,
        week: LocalDate,
        remote: Remote,
    ) {
        val dishes = remote.plan.days.mapNotNull { it.dish }
        val previous = store.read()?.takeIf { it.memberId == session.memberId && it.weekStart == week }?.dishes
        statusNotices(session.role == Role.PARENT, session.memberId, previous, dishes, remote.names).forEach(notifier::announce)
        val deadline = remote.cook?.submissionDeadline
        val reminders =
            if (deadline != null && needsReminder(remote.cook?.state)) reminderTimes(week, deadline, clock.instant()) else emptyList()
        store.write(
            ReminderSnapshot(session.memberId, week, deadline, remote.cook?.state, reminders, dishes.associate { it.id to it.known() }),
        )
        scheduler.replace(reminders)
    }

    private suspend fun signOut(): SyncOutcome {
        scheduler.cancelAll()
        store.clear()
        return SyncOutcome.SIGNED_OUT
    }
}

/** Alarm ist fällig: erinnern, solange laut lokalem Stand noch nichts eingereicht ist (REQ-MEAL-050). */
class ShowDueReminderUseCase(
    private val sessions: SessionStore,
    private val store: ReminderStore,
    private val notifier: Notifier,
    private val clock: Clock = Clock.system(BerlinZone),
) {
    suspend operator fun invoke(weekStart: LocalDate) {
        val memberId = sessions.awaitSession()?.memberId ?: return
        val snapshot = store.read() ?: return
        val deadline = snapshot.deadline ?: return
        val due = snapshot.memberId == memberId && snapshot.weekStart == weekStart && needsReminder(snapshot.cookState)
        if (due && deadline > clock.instant()) notifier.remind(DeadlineReminder(deadline, snapshot.cookState == CookState.REJECTED))
    }
}

/** Nach Neustart oder App-Update: künftige Erinnerungen aus dem lokalen Stand neu einplanen. */
class RestoreRemindersUseCase(
    private val store: ReminderStore,
    private val scheduler: ReminderScheduler,
    private val clock: Clock = Clock.system(BerlinZone),
) {
    suspend operator fun invoke() {
        val now = clock.instant()
        scheduler.replace(
            store
                .read()
                ?.reminders
                .orEmpty()
                .filter { it.at > now },
        )
    }
}

/** Offene Einreichung laut lokalem Stand — auch nach Fristablauf, Einreichen bleibt möglich (REQ-MEAL-051). */
class LoadPendingCookingUseCase(
    private val sessions: SessionStore,
    private val store: ReminderStore,
    private val clock: Clock = Clock.system(BerlinZone),
) {
    suspend operator fun invoke(): PendingCooking? {
        val memberId = sessions.awaitSession()?.memberId ?: return null
        val snapshot = store.read() ?: return null
        val deadline = snapshot.deadline ?: return null
        val pending =
            snapshot.memberId == memberId && snapshot.weekStart == currentPlanWeek(clock.instant()) && needsReminder(snapshot.cookState)
        return if (pending) PendingCooking(deadline, snapshot.cookState == CookState.REJECTED) else null
    }
}
