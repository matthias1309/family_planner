package de.familyplanner.android.feature.meals.domain.attendance

import de.familyplanner.android.core.data.ApiResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private const val UNKNOWN_NAME = "?"

class LoadWeekOverviewUseCase(
    private val repository: AttendanceRepository,
) {
    suspend operator fun invoke(weekStart: LocalDate): ApiResult<WeekOverview> =
        coroutineScope {
            val week = async { repository.week(weekStart) }
            val names = async { repository.memberNames() }
            val weekResult = week.await()
            val namesResult = names.await()
            if (weekResult !is ApiResult.Success) return@coroutineScope weekResult as ApiResult.Failure
            if (namesResult !is ApiResult.Success) return@coroutineScope namesResult as ApiResult.Failure
            val raw = weekResult.data
            // Ein evtl. gerade gelöschtes Mitglied ist kein Fehler: Name unbekannt, Anwesenheit trotzdem zeigen.
            val children =
                raw.children.map {
                    ChildWeek(it.memberId, namesResult.data[it.memberId] ?: UNKNOWN_NAME, it.cooks, it.days, it.submissionDeadline)
                }
            ApiResult.Success(WeekOverview(raw.weekStart, raw.orderDeadline, children))
        }
}

class LoadProfilesUseCase(
    private val repository: AttendanceRepository,
) {
    suspend operator fun invoke(): ApiResult<List<ChildProfile>> =
        coroutineScope {
            val profiles = async { repository.profiles() }
            val names = async { repository.memberNames() }
            val profilesResult = profiles.await()
            val namesResult = names.await()
            if (profilesResult !is ApiResult.Success) return@coroutineScope profilesResult as ApiResult.Failure
            if (namesResult !is ApiResult.Success) return@coroutineScope namesResult as ApiResult.Failure
            ApiResult.Success(profilesResult.data.map { ChildProfile(it.memberId, namesResult.data[it.memberId] ?: UNKNOWN_NAME, it) })
        }
}

class SaveProfileUseCase(
    private val repository: AttendanceRepository,
) {
    suspend operator fun invoke(profile: AttendanceProfile): ApiResult<AttendanceProfile> =
        validatePattern(profile.pattern) ?: repository.saveProfile(profile)
}

class LoadExceptionsUseCase(
    private val repository: AttendanceRepository,
) {
    suspend operator fun invoke(memberId: String): ApiResult<List<AttendanceException>> = repository.exceptions(memberId)
}

class AddExceptionUseCase(
    private val repository: AttendanceRepository,
) {
    suspend operator fun invoke(
        memberId: String,
        from: LocalDate,
        to: LocalDate,
        presence: Presence,
        note: String?,
    ): ApiResult<AttendanceException> =
        validateException(from, to, note) ?: repository.addException(memberId, from, to, presence, normalizeNote(note))
}

class DeleteExceptionUseCase(
    private val repository: AttendanceRepository,
) {
    suspend operator fun invoke(exceptionId: String): ApiResult<Unit> = repository.deleteException(exceptionId)
}

class LoadDeadlineSettingsUseCase(
    private val repository: AttendanceRepository,
) {
    suspend operator fun invoke(): ApiResult<DeadlineSettingsOverview> = repository.deadlineSettings()
}

class SaveDeadlineSettingsUseCase(
    private val repository: AttendanceRepository,
) {
    suspend operator fun invoke(
        orderDay: DayOfWeek,
        orderTime: LocalTime,
        childTime: LocalTime,
    ): ApiResult<DeadlineSettingsOverview> = repository.saveDeadlineSettings(orderDay, orderTime, childTime)
}

/** Bündelt die Ausnahmen-Use-Cases für das Profil-Formular (hält die ViewModel-Parameterliste kurz). */
class ExceptionUseCases(
    val load: LoadExceptionsUseCase,
    val add: AddExceptionUseCase,
    val delete: DeleteExceptionUseCase,
)
