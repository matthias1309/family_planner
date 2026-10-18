package de.familyplanner.meals.recipe

import de.familyplanner.core.error.AppException
import java.net.URI
import java.util.Locale

const val SERVINGS_MAX = 20
const val STEP_MAX = 1000
const val STEPS_MAX_COUNT = 30

/** Bounds the raw input, blank steps included, before any per-step work. */
const val STEPS_RAW_MAX = 100
const val SOURCE_URL_MAX = 500
const val RECIPE_QUERY_MAX = 60

fun validatedServings(value: Int): Int {
    if (value !in 1..SERVINGS_MAX) throw AppException.Validation("Portionen müssen zwischen 1 und $SERVINGS_MAX liegen")
    return value
}

fun validatedSteps(steps: List<String>): List<String> {
    val cleaned = if (steps.size > STEPS_RAW_MAX) null else steps.map { it.trim() }.filter { it.isNotEmpty() }
    if (cleaned == null || cleaned.size > STEPS_MAX_COUNT) throw AppException.Validation("Höchstens $STEPS_MAX_COUNT Schritte")
    if (cleaned.any { it.length > STEP_MAX }) throw AppException.Validation("Ein Schritt darf höchstens $STEP_MAX Zeichen lang sein")
    return cleaned
}

fun validatedSourceUrl(value: String?): String? {
    val url = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (url.length > SOURCE_URL_MAX) throw AppException.Validation(INVALID_SOURCE_URL)
    val uri = runCatching { URI(url) }.getOrNull()
    val valid = uri != null && uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && !uri.host.isNullOrEmpty()
    if (!valid) throw AppException.Validation(INVALID_SOURCE_URL)
    return url
}

fun nameKey(name: String): String = name.lowercase(Locale.ROOT)

/** Recipes whose name contains [query] (case-insensitive; null or blank = all), sorted by name, then id. */
fun matchingRecipes(
    all: List<RecipeSummary>,
    query: String?,
): List<RecipeSummary> {
    val needle = query?.trim().orEmpty()
    if (needle.length > RECIPE_QUERY_MAX) throw AppException.Validation("Suchbegriff darf höchstens $RECIPE_QUERY_MAX Zeichen lang sein")
    val key = nameKey(needle)
    return all
        .filter { nameKey(it.name).contains(key) }
        .sortedWith(compareBy({ nameKey(it.name) }, { it.id.value }))
}

private const val INVALID_SOURCE_URL = "Ungültige Quell-URL"
