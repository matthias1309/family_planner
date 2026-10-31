package de.familyplanner.gamification.web

import de.familyplanner.api.model.RedemptionListResponse
import de.familyplanner.api.model.RedemptionResponse
import de.familyplanner.api.model.RejectRedemptionRequest
import de.familyplanner.api.model.RewardListResponse
import de.familyplanner.api.model.RewardResponse
import de.familyplanner.api.model.SavingsGoalInput
import de.familyplanner.api.model.SavingsGoalResponse
import de.familyplanner.core.auth.AUTH_DEVICE
import de.familyplanner.core.auth.principalOrThrow
import de.familyplanner.core.auth.requireParent
import de.familyplanner.core.auth.uuidParameter
import de.familyplanner.core.error.AppException
import de.familyplanner.core.member.MemberId
import de.familyplanner.gamification.ledger.ENTRIES_LIMIT_DEFAULT
import de.familyplanner.gamification.rewards.RedemptionId
import de.familyplanner.gamification.rewards.RedemptionService
import de.familyplanner.gamification.rewards.RedemptionStatus
import de.familyplanner.gamification.rewards.RewardId
import de.familyplanner.gamification.rewards.RewardService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import de.familyplanner.api.model.RewardInput as ApiRewardInput

fun Route.rewardRoutes(
    rewards: RewardService,
    redemptions: RedemptionService,
) {
    authenticate(AUTH_DEVICE) {
        catalogRoutes(rewards)
        redemptionRoutes(redemptions)
        goalRoutes(rewards)
    }
}

private fun Route.catalogRoutes(rewards: RewardService) {
    get("/api/v1/gamification/rewards") {
        call.principalOrThrow()
        val items = rewards.list(call.includeArchived())
        call.respond(RewardListResponse(success = true, data = items.map { it.toApi() }))
    }
    post("/api/v1/gamification/rewards") {
        val parent = call.requireParent().toActor()
        val reward = rewards.create(parent, call.receive<ApiRewardInput>().toInput())
        call.respond(HttpStatusCode.Created, RewardResponse(success = true, data = reward.toApi()))
    }
    put("/api/v1/gamification/rewards/{rewardId}") {
        val parent = call.requireParent().toActor()
        val reward = rewards.update(parent, RewardId(call.uuidParameter("rewardId")), call.receive<ApiRewardInput>().toInput())
        call.respond(RewardResponse(success = true, data = reward.toApi()))
    }
    delete("/api/v1/gamification/rewards/{rewardId}") {
        val parent = call.requireParent().toActor()
        rewards.archive(parent, RewardId(call.uuidParameter("rewardId")))
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun Route.redemptionRoutes(redemptions: RedemptionService) {
    post("/api/v1/gamification/rewards/{rewardId}/redemptions") {
        val actor = call.principalOrThrow().toActor()
        val redemption = redemptions.redeem(actor, RewardId(call.uuidParameter("rewardId")))
        call.respond(HttpStatusCode.Created, RedemptionResponse(success = true, data = redemption.toApi()))
    }
    get("/api/v1/gamification/redemptions") {
        val actor = call.principalOrThrow().toActor()
        val items = redemptions.list(actor, call.redemptionStatus(), call.redemptionLimit())
        call.respond(RedemptionListResponse(success = true, data = items.map { it.toApi() }))
    }
    post("/api/v1/gamification/redemptions/{redemptionId}/fulfillment") {
        val parent = call.requireParent().toActor()
        val redemption = redemptions.fulfill(parent, RedemptionId(call.uuidParameter("redemptionId")))
        call.respond(RedemptionResponse(success = true, data = redemption.toApi()))
    }
    post("/api/v1/gamification/redemptions/{redemptionId}/rejection") {
        val parent = call.requireParent().toActor()
        val id = RedemptionId(call.uuidParameter("redemptionId"))
        val redemption = redemptions.reject(parent, id, call.receive<RejectRedemptionRequest>().reason)
        call.respond(RedemptionResponse(success = true, data = redemption.toApi()))
    }
}

private fun Route.goalRoutes(rewards: RewardService) {
    get("/api/v1/gamification/members/{memberId}/savings-goal") {
        val actor = call.principalOrThrow().toActor()
        call.respond(SavingsGoalResponse(success = true, data = rewards.goal(actor, call.targetMemberId()).toApi()))
    }
    put("/api/v1/gamification/members/{memberId}/savings-goal") {
        val actor = call.principalOrThrow().toActor()
        val input = call.receive<SavingsGoalInput>()
        val goal = rewards.setGoal(actor, call.targetMemberId(), RewardId(input.rewardId))
        call.respond(SavingsGoalResponse(success = true, data = goal.toApi()))
    }
    delete("/api/v1/gamification/members/{memberId}/savings-goal") {
        val actor = call.principalOrThrow().toActor()
        rewards.clearGoal(actor, call.targetMemberId())
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun ApplicationCall.targetMemberId() = MemberId(uuidParameter("memberId"))

private fun ApplicationCall.includeArchived(): Boolean {
    val raw = request.queryParameters["includeArchived"] ?: return false
    return raw.toBooleanStrictOrNull() ?: throw AppException.Validation("Ungültiger Wert für 'includeArchived'")
}

private fun ApplicationCall.redemptionStatus(): RedemptionStatus? {
    val raw = request.queryParameters["status"] ?: return null
    return RedemptionStatus.entries.firstOrNull { it.name == raw } ?: throw AppException.Validation("Ungültiger Wert für 'status'")
}

private fun ApplicationCall.redemptionLimit(): Int {
    val raw = request.queryParameters["limit"] ?: return ENTRIES_LIMIT_DEFAULT
    return raw.toIntOrNull() ?: throw AppException.Validation("Ungültiger Wert für 'limit'")
}
