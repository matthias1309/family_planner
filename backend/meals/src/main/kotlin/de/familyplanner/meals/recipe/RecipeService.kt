package de.familyplanner.meals.recipe

import de.familyplanner.core.error.AppException
import de.familyplanner.core.error.validatedName
import de.familyplanner.core.member.MemberId
import de.familyplanner.meals.plan.DISH_NAME_MAX
import de.familyplanner.meals.plan.Ingredient
import de.familyplanner.meals.plan.validatedIngredients
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/** What a caller wants to store; validated and completed by [RecipeService]. */
data class RecipeInput(
    val name: String,
    val servings: Int,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val sourceUrl: String?,
)

class RecipeService(
    private val recipes: RecipeRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(RecipeService::class.java)

    suspend fun list(query: String?): List<RecipeSummary> = matchingRecipes(recipes.findAllSummaries(), query)

    suspend fun get(id: RecipeId): Recipe = recipes.findById(id) ?: throw AppException.NotFound(RECIPE_NOT_FOUND)

    suspend fun create(
        createdBy: MemberId,
        input: RecipeInput,
    ): Recipe {
        val recipe = validated(RecipeId(newId()), createdBy, input)
        requireSaved(recipes.createIfNameFree(recipe, clock.instant()))
        log.info("Recipe created: {}", recipe.id.value)
        return recipe
    }

    suspend fun update(
        id: RecipeId,
        input: RecipeInput,
    ): Recipe {
        val existing = get(id)
        val recipe = validated(id, existing.createdBy, input)
        requireSaved(recipes.updateIfNameFree(recipe, clock.instant()))
        log.info("Recipe updated: {}", id.value)
        return recipe
    }

    suspend fun delete(id: RecipeId) {
        if (!recipes.delete(id)) throw AppException.NotFound(RECIPE_NOT_FOUND)
        log.info("Recipe deleted: {}", id.value)
    }

    private fun validated(
        id: RecipeId,
        createdBy: MemberId,
        input: RecipeInput,
    ): Recipe {
        val name = validatedName(input.name, "Name", DISH_NAME_MAX)
        val servings = validatedServings(input.servings)
        val ingredients = validatedIngredients(input.ingredients)
        if (ingredients.isEmpty()) throw AppException.Validation("Bitte mindestens eine Zutat angeben")
        val steps = validatedSteps(input.steps)
        return Recipe(id, name, servings, ingredients, steps, validatedSourceUrl(input.sourceUrl), createdBy)
    }

    private fun requireSaved(outcome: RecipeSaveOutcome) {
        val error =
            when (outcome) {
                RecipeSaveOutcome.SAVED -> return
                RecipeSaveOutcome.NAME_TAKEN -> AppException.Conflict("Ein Rezept mit diesem Namen gibt es schon")
                RecipeSaveOutcome.NOT_FOUND -> AppException.NotFound(RECIPE_NOT_FOUND)
            }
        throw error
    }

    private companion object {
        const val RECIPE_NOT_FOUND = "Rezept nicht gefunden"
    }
}
