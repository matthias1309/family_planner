package de.familyplanner.meals.web

import de.familyplanner.api.model.RecipeListResponse
import de.familyplanner.api.model.RecipeResponse
import de.familyplanner.api.model.SaveRecipeRequest
import de.familyplanner.core.auth.AUTH_DEVICE
import de.familyplanner.core.auth.principalOrThrow
import de.familyplanner.core.auth.requireParent
import de.familyplanner.core.auth.uuidParameter
import de.familyplanner.meals.recipe.RecipeId
import de.familyplanner.meals.recipe.RecipeInput
import de.familyplanner.meals.recipe.RecipeService
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

fun Route.recipeRoutes(recipes: RecipeService) {
    authenticate(AUTH_DEVICE) {
        get("/api/v1/meals/recipes") {
            call.principalOrThrow()
            val list = recipes.list(call.request.queryParameters["query"])
            call.respond(RecipeListResponse(success = true, data = list.map { it.toApi() }))
        }
        post("/api/v1/meals/recipes") {
            val principal = call.requireParent()
            val recipe = recipes.create(principal.memberId, call.receive<SaveRecipeRequest>().toInput())
            call.respond(HttpStatusCode.Created, RecipeResponse(success = true, data = recipe.toApi()))
        }
        get("/api/v1/meals/recipes/{recipeId}") {
            call.principalOrThrow()
            call.respond(RecipeResponse(success = true, data = recipes.get(call.recipeId()).toApi()))
        }
        put("/api/v1/meals/recipes/{recipeId}") {
            call.requireParent()
            val id = call.recipeId()
            val recipe = recipes.update(id, call.receive<SaveRecipeRequest>().toInput())
            call.respond(RecipeResponse(success = true, data = recipe.toApi()))
        }
        delete("/api/v1/meals/recipes/{recipeId}") {
            call.requireParent()
            recipes.delete(call.recipeId())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun ApplicationCall.recipeId() = RecipeId(uuidParameter("recipeId"))

private fun SaveRecipeRequest.toInput() = RecipeInput(name, servings, ingredients.map { it.toDomain() }, steps, sourceUrl)
