package de.familyplanner.android.feature.meals.domain.plan

/** Grenzen wie im Backend (`MealPlanService`). */
object PlanLimits {
    const val NAME_MAX = 60
    const val INGREDIENT_MAX = 80
    const val INGREDIENTS_MAX_COUNT = 30
    const val INGREDIENT_LINES_RAW_MAX = 100

    /** Begründung für Absage und Ablehnung. */
    const val REASON_MAX = 200

    /** Höchstens so viele Wochen bleiben im Offline-Cache (ADR-0011). */
    const val CACHED_WEEKS_MAX = 8
}
