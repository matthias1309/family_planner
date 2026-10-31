package de.familyplanner.gamification.web

import de.familyplanner.api.model.BadgeOverviewResponse
import de.familyplanner.api.model.GameSettingsResponse
import de.familyplanner.api.model.Leaderboard
import de.familyplanner.api.model.LeaderboardResponse
import de.familyplanner.api.model.LedgerEntryResponse
import de.familyplanner.api.model.ManualEntryRequest
import de.familyplanner.api.model.PointsAccountResponse
import de.familyplanner.core.auth.AUTH_DEVICE
import de.familyplanner.core.auth.principalOrThrow
import de.familyplanner.core.auth.requireParent
import de.familyplanner.core.auth.uuidParameter
import de.familyplanner.core.error.AppException
import de.familyplanner.core.member.MemberId
import de.familyplanner.gamification.account.AccountService
import de.familyplanner.gamification.badges.BadgeService
import de.familyplanner.gamification.ledger.ENTRIES_LIMIT_DEFAULT
import de.familyplanner.gamification.settings.GameSettingsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import de.familyplanner.api.model.GameSettings as ApiGameSettings

fun Route.gamificationRoutes(
    accounts: AccountService,
    settings: GameSettingsService,
    badges: BadgeService,
) {
    authenticate(AUTH_DEVICE) {
        get("/api/v1/gamification/leaderboard") {
            call.principalOrThrow()
            val entries = accounts.leaderboard().map { it.toApi() }
            call.respond(LeaderboardResponse(success = true, data = Leaderboard(entries)))
        }
        get("/api/v1/gamification/members/{memberId}/account") {
            val actor = call.principalOrThrow().toActor()
            val account = accounts.account(actor, call.targetMemberId(), call.entriesLimit())
            call.respond(PointsAccountResponse(success = true, data = account.toApi()))
        }
        get("/api/v1/gamification/members/{memberId}/badges") {
            val actor = call.principalOrThrow().toActor()
            call.respond(BadgeOverviewResponse(success = true, data = badges.overview(actor, call.targetMemberId()).toApi()))
        }
        post("/api/v1/gamification/members/{memberId}/entries") {
            val parent = call.requireParent().toActor()
            val request = call.receive<ManualEntryRequest>()
            val entry = accounts.book(parent, call.targetMemberId(), request.amount, request.reason)
            call.respond(HttpStatusCode.Created, LedgerEntryResponse(success = true, data = entry.toApi()))
        }
        get("/api/v1/gamification/settings") {
            call.principalOrThrow()
            call.respond(GameSettingsResponse(success = true, data = settings.current().toApi()))
        }
        put("/api/v1/gamification/settings") {
            call.requireParent()
            val updated = settings.update(call.receive<ApiGameSettings>().cookingPoints)
            call.respond(GameSettingsResponse(success = true, data = updated.toApi()))
        }
    }
}

private fun ApplicationCall.targetMemberId() = MemberId(uuidParameter("memberId"))

private fun ApplicationCall.entriesLimit(): Int {
    val raw = request.queryParameters["limit"] ?: return ENTRIES_LIMIT_DEFAULT
    return raw.toIntOrNull() ?: throw AppException.Validation("Ungültiger Wert für 'limit'")
}
