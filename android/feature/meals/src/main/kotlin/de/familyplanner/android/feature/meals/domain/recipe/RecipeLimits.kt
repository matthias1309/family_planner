package de.familyplanner.android.feature.meals.domain.recipe

/** Grenzen wie im Backend (`Ingredient.kt`, `RecipeRules.kt`). */
object RecipeLimits {
    const val UNIT_MAX = 20
    const val QUANTITY_MIN = 0.001
    const val QUANTITY_MAX = 10_000.0
    const val QUANTITY_SCALE = 3
    const val SERVINGS_MIN = 1
    const val SERVINGS_MAX = 20
    const val SERVINGS_DEFAULT = 4
    const val STEP_MAX = 1000
    const val STEPS_MAX_COUNT = 30
    const val STEP_LINES_RAW_MAX = 100
    const val SOURCE_URL_MAX = 500
    const val QUERY_MAX = 60
}
