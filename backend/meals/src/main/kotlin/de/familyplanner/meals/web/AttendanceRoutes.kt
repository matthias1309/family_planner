package de.familyplanner.meals.web

import de.familyplanner.api.model.AttendanceExceptionListResponse
import de.familyplanner.api.model.AttendanceExceptionResponse
import de.familyplanner.api.model.AttendanceProfileListResponse
import de.familyplanner.api.model.AttendanceProfileResponse
import de.familyplanner.api.model.CreateAttendanceExceptionRequest
import de.familyplanner.api.model.DeadlineSettingsOverviewResponse
import de.familyplanner.api.model.UpdateAttendanceProfileRequest
import de.familyplanner.api.model.UpdateDeadlineSettingsRequest
import de.familyplanner.api.model.WeekAttendanceResponse
import de.familyplanner.core.auth.AUTH_DEVICE
import de.familyplanner.core.auth.principalOrThrow
import de.familyplanner.core.auth.requireParent
import de.familyplanner.core.auth.uuidParameter
import de.familyplanner.core.member.MemberId
import de.familyplanner.meals.attendance.AttendanceService
import de.familyplanner.meals.attendance.DeadlineSettingsService
import de.familyplanner.meals.attendance.ExceptionId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

fun Route.attendanceRoutes(
    attendance: AttendanceService,
    settings: DeadlineSettingsService,
) {
    authenticate(AUTH_DEVICE) {
        profileRoutes(attendance)
        exceptionRoutes(attendance)
        settingsRoutes(settings)
        get("/api/v1/meals/weeks/{weekStart}/attendance") {
            call.principalOrThrow()
            val week = attendance.week(call.weekParameter())
            call.respond(WeekAttendanceResponse(success = true, data = week.toApi()))
        }
    }
}

private fun Route.profileRoutes(attendance: AttendanceService) {
    get("/api/v1/meals/profiles") {
        call.principalOrThrow()
        call.respond(AttendanceProfileListResponse(success = true, data = attendance.profiles().map { it.toApi() }))
    }
    put("/api/v1/meals/profiles/{memberId}") {
        call.requireParent()
        val memberId = MemberId(call.uuidParameter("memberId"))
        val request = call.receive<UpdateAttendanceProfileRequest>()
        val profile = attendance.updateProfile(memberId, request.cooks, request.pattern.toDomain())
        call.respond(AttendanceProfileResponse(success = true, data = profile.toApi()))
    }
}

private fun Route.exceptionRoutes(attendance: AttendanceService) {
    get("/api/v1/meals/profiles/{memberId}/exceptions") {
        call.principalOrThrow()
        val memberId = MemberId(call.uuidParameter("memberId"))
        call.respond(AttendanceExceptionListResponse(success = true, data = attendance.exceptions(memberId).map { it.toApi() }))
    }
    post("/api/v1/meals/profiles/{memberId}/exceptions") {
        call.requireParent()
        val memberId = MemberId(call.uuidParameter("memberId"))
        val request = call.receive<CreateAttendanceExceptionRequest>()
        val exception =
            attendance.addException(
                memberId = memberId,
                from = parseDate(request.from, "from"),
                to = parseDate(request.to, "to"),
                presence = request.presence.toDomain(),
                note = request.note,
            )
        call.respond(HttpStatusCode.Created, AttendanceExceptionResponse(success = true, data = exception.toApi()))
    }
    delete("/api/v1/meals/exceptions/{exceptionId}") {
        call.requireParent()
        attendance.removeException(ExceptionId(call.uuidParameter("exceptionId")))
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun Route.settingsRoutes(settings: DeadlineSettingsService) {
    get("/api/v1/meals/settings") {
        call.principalOrThrow()
        call.respond(DeadlineSettingsOverviewResponse(success = true, data = settings.overview().toApi()))
    }
    put("/api/v1/meals/settings") {
        call.requireParent()
        val request = call.receive<UpdateDeadlineSettingsRequest>()
        val overview =
            settings.update(
                orderDeadlineDay = request.orderDeadlineDay.toDomain(),
                orderDeadlineTime = parseTime(request.orderDeadlineTime, "orderDeadlineTime"),
                childDeadlineTime = parseTime(request.childDeadlineTime, "childDeadlineTime"),
            )
        call.respond(DeadlineSettingsOverviewResponse(success = true, data = overview.toApi()))
    }
}
