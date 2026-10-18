package de.familyplanner.meals.web

import de.familyplanner.api.model.CancelCookingRequest
import de.familyplanner.api.model.CookingCancellationResponse
import de.familyplanner.api.model.RejectDishRequest
import de.familyplanner.api.model.WeekStatusResponse
import de.familyplanner.core.auth.AUTH_DEVICE
import de.familyplanner.core.auth.principalOrThrow
import de.familyplanner.core.auth.requireParent
import de.familyplanner.core.auth.uuidParameter
import de.familyplanner.core.member.MemberId
import de.familyplanner.meals.plan.CookingWeekService
import de.familyplanner.meals.plan.DishId
import de.familyplanner.meals.plan.DishReviewService
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

fun Route.cookingRoutes(
    reviews: DishReviewService,
    cooking: CookingWeekService,
) {
    authenticate(AUTH_DEVICE) {
        post("/api/v1/meals/dishes/{dishId}/approval") {
            call.requireParent()
            reviews.approve(DishId(call.uuidParameter("dishId")))
            call.respond(HttpStatusCode.NoContent)
        }
        post("/api/v1/meals/dishes/{dishId}/rejection") {
            call.requireParent()
            val id = DishId(call.uuidParameter("dishId"))
            reviews.reject(id, call.receive<RejectDishRequest>().reason)
            call.respond(HttpStatusCode.NoContent)
        }
        put("/api/v1/meals/weeks/{weekStart}/cancellations/{memberId}") {
            val actor = call.principalOrThrow().toActor()
            val request = call.receive<CancelCookingRequest>()
            val cancellation = cooking.cancel(actor, call.cookMemberId(), call.weekParameter(), request.reason)
            call.respond(CookingCancellationResponse(success = true, data = cancellation.toApi()))
        }
        delete("/api/v1/meals/weeks/{weekStart}/cancellations/{memberId}") {
            val actor = call.principalOrThrow().toActor()
            cooking.withdraw(actor, call.cookMemberId(), call.weekParameter())
            call.respond(HttpStatusCode.NoContent)
        }
        get("/api/v1/meals/weeks/{weekStart}/status") {
            call.principalOrThrow()
            call.respond(WeekStatusResponse(success = true, data = cooking.status(call.weekParameter()).toApi()))
        }
    }
}

private fun ApplicationCall.cookMemberId() = MemberId(uuidParameter("memberId"))
