package de.familyplanner.core.web

import de.familyplanner.api.model.CreateMemberRequest
import de.familyplanner.api.model.Me
import de.familyplanner.api.model.MeResponse
import de.familyplanner.api.model.MemberListResponse
import de.familyplanner.api.model.MemberResponse
import de.familyplanner.api.model.PairingCodeResponse
import de.familyplanner.core.auth.AUTH_DEVICE
import de.familyplanner.core.auth.principalOrThrow
import de.familyplanner.core.auth.requireParent
import de.familyplanner.core.auth.uuidParameter
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.MemberService
import de.familyplanner.core.pairing.PairingService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.memberRoutes(
    members: MemberService,
    pairing: PairingService,
) {
    authenticate(AUTH_DEVICE) {
        get("/api/v1/me") {
            val principal = call.principalOrThrow()
            val member = members.get(principal.memberId)
            call.respond(MeResponse(success = true, data = Me(member = member.toApi(), deviceId = principal.deviceId.value)))
        }
        get("/api/v1/members") {
            call.principalOrThrow()
            call.respond(MemberListResponse(success = true, data = members.list().map { it.toApi() }))
        }
        post("/api/v1/members") {
            call.requireParent()
            val request = call.receive<CreateMemberRequest>()
            val member = members.create(request.name, request.role.toDomain())
            call.respond(HttpStatusCode.Created, MemberResponse(success = true, data = member.toApi()))
        }
        post("/api/v1/members/{memberId}/pairing-codes") {
            call.requireParent()
            val memberId = MemberId(call.uuidParameter("memberId"))
            val issued = pairing.createCode(memberId)
            call.respond(HttpStatusCode.Created, PairingCodeResponse(success = true, data = issued.toApi()))
        }
    }
}
