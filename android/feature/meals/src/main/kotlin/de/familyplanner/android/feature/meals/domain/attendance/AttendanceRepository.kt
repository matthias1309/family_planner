package de.familyplanner.android.feature.meals.domain.attendance

import de.familyplanner.android.core.data.ApiResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

interface AttendanceRepository {
    suspend fun memberNames(): ApiResult<Map<String, String>>

    suspend fun profiles(): ApiResult<List<AttendanceProfile>>

    suspend fun saveProfile(profile: AttendanceProfile): ApiResult<AttendanceProfile>

    suspend fun exceptions(memberId: String): ApiResult<List<AttendanceException>>

    suspend fun addException(
        memberId: String,
        from: LocalDate,
        to: LocalDate,
        presence: Presence,
        note: String?,
    ): ApiResult<AttendanceException>

    suspend fun deleteException(exceptionId: String): ApiResult<Unit>

    suspend fun deadlineSettings(): ApiResult<DeadlineSettingsOverview>

    suspend fun saveDeadlineSettings(
        orderDay: DayOfWeek,
        orderTime: LocalTime,
        childTime: LocalTime,
    ): ApiResult<DeadlineSettingsOverview>

    suspend fun week(weekStart: LocalDate): ApiResult<WeekAttendanceRaw>
}
