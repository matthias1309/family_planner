package de.familyplanner.gamification.web

import de.familyplanner.api.model.AvatarCatalogResponse
import de.familyplanner.api.model.AvatarProfileResponse
import de.familyplanner.api.model.AvatarPurchaseResponse
import de.familyplanner.api.model.MemberAvatarListResponse
import de.familyplanner.core.auth.AUTH_DEVICE
import de.familyplanner.core.auth.principalOrThrow
import de.familyplanner.core.auth.uuidParameter
import de.familyplanner.core.error.AppException
import de.familyplanner.core.member.MemberId
import de.familyplanner.gamification.avatar.AvatarService
import de.familyplanner.gamification.avatar.ITEM_ID_PATTERN
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import de.familyplanner.api.model.AvatarConfig as ApiAvatarConfig

fun Route.avatarRoutes(avatars: AvatarService) {
    authenticate(AUTH_DEVICE) {
        get("/api/v1/gamification/avatar/catalog") {
            call.principalOrThrow()
            call.respond(AvatarCatalogResponse(success = true, data = avatars.catalog().toApiCatalog()))
        }
        get("/api/v1/gamification/avatars") {
            call.principalOrThrow()
            call.respond(MemberAvatarListResponse(success = true, data = avatars.all().map { it.toApi() }))
        }
        get("/api/v1/gamification/members/{memberId}/avatar") {
            val actor = call.principalOrThrow().toActor()
            call.respond(AvatarProfileResponse(success = true, data = avatars.profile(actor, call.targetMemberId()).toApi()))
        }
        put("/api/v1/gamification/members/{memberId}/avatar") {
            val actor = call.principalOrThrow().toActor()
            val config = call.receive<ApiAvatarConfig>().toDomain()
            call.respond(AvatarProfileResponse(success = true, data = avatars.save(actor, call.targetMemberId(), config).toApi()))
        }
        post("/api/v1/gamification/avatar/items/{itemId}/purchase") {
            val actor = call.principalOrThrow().toActor()
            val purchase = avatars.purchase(actor, call.itemIdParameter())
            call.respond(HttpStatusCode.Created, AvatarPurchaseResponse(success = true, data = purchase.toApi()))
        }
    }
}

private fun ApplicationCall.targetMemberId() = MemberId(uuidParameter("memberId"))

private fun ApplicationCall.itemIdParameter(): String =
    parameters["itemId"]?.takeIf { ITEM_ID_PATTERN.matches(it) } ?: throw AppException.Validation("Ungültige Kennung 'itemId'")
