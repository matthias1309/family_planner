package de.familyplanner.meals.web

import de.familyplanner.api.model.CheckShoppingEntryRequest
import de.familyplanner.api.model.ShoppingListResponse
import de.familyplanner.core.auth.AUTH_DEVICE
import de.familyplanner.core.auth.principalOrThrow
import de.familyplanner.core.auth.requireParent
import de.familyplanner.core.auth.uuidParameter
import de.familyplanner.meals.shopping.ShoppingItemId
import de.familyplanner.meals.shopping.ShoppingListService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import de.familyplanner.api.model.Ingredient as ApiIngredient

fun Route.shoppingRoutes(shopping: ShoppingListService) {
    authenticate(AUTH_DEVICE) {
        get("/api/v1/meals/weeks/{weekStart}/shopping-list") {
            call.principalOrThrow()
            call.respond(ShoppingListResponse(success = true, data = shopping.list(call.weekParameter()).toApi()))
        }
        post("/api/v1/meals/weeks/{weekStart}/shopping-list/items") {
            call.requireParent()
            val week = call.weekParameter()
            val list = shopping.addItem(week, call.receive<ApiIngredient>().toDomain())
            call.respond(HttpStatusCode.Created, ShoppingListResponse(success = true, data = list.toApi()))
        }
        delete("/api/v1/meals/weeks/{weekStart}/shopping-list/items/{itemId}") {
            call.requireParent()
            shopping.deleteItem(call.weekParameter(), ShoppingItemId(call.uuidParameter("itemId")))
            call.respond(HttpStatusCode.NoContent)
        }
        put("/api/v1/meals/weeks/{weekStart}/shopping-list/checks") {
            call.requireParent()
            val request = call.receive<CheckShoppingEntryRequest>()
            val list = shopping.setChecked(call.weekParameter(), request.key, request.checked)
            call.respond(ShoppingListResponse(success = true, data = list.toApi()))
        }
    }
}
