package de.familyplanner.android.feature.meals.data.attendance

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.apiCall
import de.familyplanner.android.core.data.apiCallNoContent
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceException
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceProfile
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceRepository
import de.familyplanner.android.feature.meals.domain.attendance.DeadlineSettingsOverview
import de.familyplanner.android.feature.meals.domain.attendance.Presence
import de.familyplanner.android.feature.meals.domain.attendance.WeekAttendanceRaw
import de.familyplanner.api.client.MealsApi
import de.familyplanner.api.client.MembersApi
import de.familyplanner.api.model.CreateAttendanceExceptionRequest
import de.familyplanner.api.model.UpdateAttendanceProfileRequest
import de.familyplanner.api.model.UpdateDeadlineSettingsRequest
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class RemoteAttendanceRepository(
    private val meals: MealsApi,
    private val members: MembersApi,
    private val json: Json,
) : AttendanceRepository {
    override suspend fun memberNames(): ApiResult<Map<String, String>> =
        apiCall(json, { members.listMembers() }) { response -> response.data?.associate { it.id to it.name } }

    override suspend fun profiles(): ApiResult<List<AttendanceProfile>> =
        apiCall(json, { meals.listAttendanceProfiles() }) { response -> response.data?.mapAllOrNull { it.toDomain() } }

    override suspend fun saveProfile(profile: AttendanceProfile): ApiResult<AttendanceProfile> =
        apiCall(
            json,
            { meals.updateAttendanceProfile(profile.memberId, UpdateAttendanceProfileRequest(profile.cooks, profile.pattern.toApi())) },
        ) { it.data?.toDomain() }

    override suspend fun exceptions(memberId: String): ApiResult<List<AttendanceException>> =
        apiCall(json, { meals.listAttendanceExceptions(memberId) }) { response -> response.data?.mapAllOrNull { it.toDomain() } }

    override suspend fun addException(
        memberId: String,
        from: LocalDate,
        to: LocalDate,
        presence: Presence,
        note: String?,
    ): ApiResult<AttendanceException> =
        apiCall(
            json,
            {
                meals.createAttendanceException(
                    memberId,
                    CreateAttendanceExceptionRequest(from.toString(), to.toString(), presence.toApi(), note),
                )
            },
        ) { it.data?.toDomain() }

    override suspend fun deleteException(exceptionId: String): ApiResult<Unit> =
        apiCallNoContent(json) { meals.deleteAttendanceException(exceptionId) }

    override suspend fun deadlineSettings(): ApiResult<DeadlineSettingsOverview> =
        apiCall(json, { meals.getDeadlineSettings() }) { it.data?.toDomain() }

    override suspend fun saveDeadlineSettings(
        orderDay: DayOfWeek,
        orderTime: LocalTime,
        childTime: LocalTime,
    ): ApiResult<DeadlineSettingsOverview> =
        apiCall(
            json,
            { meals.updateDeadlineSettings(UpdateDeadlineSettingsRequest(orderDay.toApi(), orderTime.toApiTime(), childTime.toApiTime())) },
        ) { it.data?.toDomain() }

    override suspend fun week(weekStart: LocalDate): ApiResult<WeekAttendanceRaw> =
        apiCall(json, { meals.getWeekAttendance(weekStart.toString()) }) { it.data?.toDomain() }
}
