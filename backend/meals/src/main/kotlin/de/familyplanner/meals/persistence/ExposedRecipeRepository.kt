package de.familyplanner.meals.persistence

import de.familyplanner.core.member.MemberId
import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.meals.plan.Ingredient
import de.familyplanner.meals.recipe.Recipe
import de.familyplanner.meals.recipe.RecipeId
import de.familyplanner.meals.recipe.RecipeRepository
import de.familyplanner.meals.recipe.RecipeSaveOutcome
import de.familyplanner.meals.recipe.RecipeSummary
import de.familyplanner.meals.recipe.nameKey
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

class ExposedRecipeRepository(
    private val db: Database,
) : RecipeRepository {
    override suspend fun findAllSummaries(): List<RecipeSummary> =
        dbQuery(db) {
            MealRecipesTable
                .selectAll()
                .map { RecipeSummary(RecipeId(it[MealRecipesTable.id]), it[MealRecipesTable.name], it[MealRecipesTable.servings]) }
        }

    override suspend fun findById(id: RecipeId): Recipe? =
        dbQuery(db) {
            val row = MealRecipesTable.selectAll().where { MealRecipesTable.id eq id.value }.firstOrNull()
            row?.let { recipe(it) }
        }

    override suspend fun exists(id: RecipeId): Boolean =
        dbQuery(db) { MealRecipesTable.selectAll().where { MealRecipesTable.id eq id.value }.any() }

    override suspend fun createIfNameFree(
        recipe: Recipe,
        at: Instant,
    ): RecipeSaveOutcome =
        dbQuery(db) {
            if (nameTaken(recipe)) return@dbQuery RecipeSaveOutcome.NAME_TAKEN
            MealRecipesTable.insert {
                it[id] = recipe.id.value
                it[name] = recipe.name
                it[nameKey] = nameKey(recipe.name)
                it[servings] = recipe.servings
                it[sourceUrl] = recipe.sourceUrl
                it[createdBy] = recipe.createdBy.value
                it[createdAt] = at.toEpochMilli()
                it[updatedAt] = at.toEpochMilli()
            }
            insertContent(recipe)
            RecipeSaveOutcome.SAVED
        }

    override suspend fun updateIfNameFree(
        recipe: Recipe,
        at: Instant,
    ): RecipeSaveOutcome =
        dbQuery(db) {
            val exists = MealRecipesTable.selectAll().where { MealRecipesTable.id eq recipe.id.value }.any()
            if (!exists) return@dbQuery RecipeSaveOutcome.NOT_FOUND
            if (nameTaken(recipe)) return@dbQuery RecipeSaveOutcome.NAME_TAKEN
            MealRecipesTable.update({ MealRecipesTable.id eq recipe.id.value }) {
                it[name] = recipe.name
                it[nameKey] = nameKey(recipe.name)
                it[servings] = recipe.servings
                it[sourceUrl] = recipe.sourceUrl
                it[updatedAt] = at.toEpochMilli()
            }
            deleteContent(recipe.id)
            insertContent(recipe)
            RecipeSaveOutcome.SAVED
        }

    override suspend fun delete(id: RecipeId): Boolean =
        dbQuery(db) {
            MealDishesTable.update({ MealDishesTable.recipeId eq id.value }) { it[recipeId] = null }
            deleteContent(id)
            MealRecipesTable.deleteWhere { MealRecipesTable.id eq id.value } > 0
        }

    private fun JdbcTransaction.nameTaken(recipe: Recipe): Boolean =
        MealRecipesTable
            .selectAll()
            .where { (MealRecipesTable.nameKey eq nameKey(recipe.name)) and (MealRecipesTable.id neq recipe.id.value) }
            .any()

    private fun JdbcTransaction.insertContent(recipe: Recipe) {
        MealRecipeIngredientsTable.batchInsert(recipe.ingredients.withIndex()) { (index, ingredient) ->
            this[MealRecipeIngredientsTable.recipeId] = recipe.id.value
            this[MealRecipeIngredientsTable.position] = index
            this[MealRecipeIngredientsTable.name] = ingredient.name
            this[MealRecipeIngredientsTable.quantity] = ingredient.quantity
            this[MealRecipeIngredientsTable.unit] = ingredient.unit
        }
        MealRecipeStepsTable.batchInsert(recipe.steps.withIndex()) { (index, step) ->
            this[MealRecipeStepsTable.recipeId] = recipe.id.value
            this[MealRecipeStepsTable.position] = index
            this[MealRecipeStepsTable.text] = step
        }
    }

    private fun JdbcTransaction.deleteContent(id: RecipeId) {
        MealRecipeStepsTable.deleteWhere { MealRecipeStepsTable.recipeId eq id.value }
        MealRecipeIngredientsTable.deleteWhere { MealRecipeIngredientsTable.recipeId eq id.value }
    }

    private fun JdbcTransaction.recipe(row: ResultRow): Recipe {
        val id = row[MealRecipesTable.id]
        val ingredients =
            MealRecipeIngredientsTable
                .selectAll()
                .where { MealRecipeIngredientsTable.recipeId eq id }
                .orderBy(MealRecipeIngredientsTable.position)
                .map {
                    Ingredient(
                        it[MealRecipeIngredientsTable.name],
                        it[MealRecipeIngredientsTable.quantity],
                        it[MealRecipeIngredientsTable.unit],
                    )
                }
        val steps =
            MealRecipeStepsTable
                .selectAll()
                .where { MealRecipeStepsTable.recipeId eq id }
                .orderBy(MealRecipeStepsTable.position)
                .map { it[MealRecipeStepsTable.text] }
        return Recipe(
            id = RecipeId(id),
            name = row[MealRecipesTable.name],
            servings = row[MealRecipesTable.servings],
            ingredients = ingredients,
            steps = steps,
            sourceUrl = row[MealRecipesTable.sourceUrl],
            createdBy = MemberId(row[MealRecipesTable.createdBy]),
        )
    }
}
