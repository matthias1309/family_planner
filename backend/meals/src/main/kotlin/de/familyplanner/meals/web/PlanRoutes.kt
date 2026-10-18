package de.familyplanner.meals.web

import de.familyplanner.api.model.DishResponse
import de.familyplanner.api.model.SaveDishRequest
import de.familyplanner.api.model.WeekPlanResponse
import de.familyplanner.core.auth.AUTH_DEVICE
import de.familyplanner.core.auth.principalOrThrow
import de.familyplanner.core.auth.requireParent
import de.familyplanner.core.auth.uuidParameter
import de.familyplanner.core.member.MemberId
import de.familyplanner.meals.plan.DishId
import de.familyplanner.meals.plan.MealPlanService
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

fun Route.planRoutes(plan: MealPlanService) {
    authenticate(AUTH_DEVICE) {
        get("/api/v1/meals/weeks/{weekStart}/plan") {
            call.principalOrThrow()
            call.respond(WeekPlanResponse(success = true, data = plan.week(call.weekParameter()).toApi()))
        }
        post("/api/v1/meals/dishes") {
            val actor = call.principalOrThrow().toActor()
            val request = call.receive<SaveDishRequest>()
            val dish = plan.create(actor, parseDate(request.date, "date"), request.cook(), request.name, request.ingredients)
            call.respond(HttpStatusCode.Created, DishResponse(success = true, data = dish.toApi()))
        }
        put("/api/v1/meals/dishes/{dishId}") {
            val actor = call.principalOrThrow().toActor()
            val id = call.dishId()
            val request = call.receive<SaveDishRequest>()
            val dish = plan.update(actor, id, parseDate(request.date, "date"), request.cook(), request.name, request.ingredients)
            call.respond(DishResponse(success = true, data = dish.toApi()))
        }
        delete("/api/v1/meals/dishes/{dishId}") {
            call.requireParent()
            plan.delete(call.dishId())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun ApplicationCall.dishId() = DishId(uuidParameter("dishId"))

private fun SaveDishRequest.cook() = MemberId(parseUuid(cookId, "cookId"))
