package de.familyplanner.android.feature.meals.data.recipe

import de.familyplanner.android.feature.meals.domain.recipe.SourceRecipe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

// Die Eingabe ist unvertrauenswürdig: Verschachtelung begrenzen.
private const val JSON_LD_DEPTH_MAX = 4
private val DIGITS = Regex("""\d+""")
private val WHITESPACE = Regex("""\s+""")

private fun parseOrNull(text: String?): JsonElement? =
    text?.let {
        try {
            Json.parseToJsonElement(it)
        } catch (
            @Suppress("SwallowedException") e: SerializationException,
        ) {
            null
        }
    }

/** Ergebnis von `evaluateJavascript` (JSON-kodiertes String-Array) → Texte der Skripte; alles andere → leer. */
internal fun decodeScriptList(raw: String?): List<String> =
    (parseOrNull(raw) as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        .orEmpty()

/** Erstes `Recipe` mit Name und mindestens einer Zutat – direkt, in `@graph`, als `mainEntity` oder in einem Array (ADR-0012). */
internal fun readJsonLdRecipe(blocks: List<String>): SourceRecipe? =
    blocks
        .asSequence()
        .mapNotNull(::parseOrNull)
        .flatMap { candidates(it, 0) }
        .mapNotNull(::toSourceRecipe)
        .firstOrNull()

private fun candidates(
    element: JsonElement,
    depth: Int,
): Sequence<JsonObject> =
    when {
        depth > JSON_LD_DEPTH_MAX -> emptySequence()
        element is JsonArray -> element.asSequence().flatMap { candidates(it, depth + 1) }
        element is JsonObject ->
            sequenceOf(element) +
                listOfNotNull(element["@graph"], element["mainEntity"]).asSequence().flatMap { candidates(it, depth + 1) }
        else -> emptySequence()
    }

private fun isRecipe(obj: JsonObject): Boolean =
    when (val type = obj["@type"]) {
        is JsonPrimitive -> type.contentOrNull == "Recipe"
        is JsonArray -> type.any { (it as? JsonPrimitive)?.contentOrNull == "Recipe" }
        else -> false
    }

private fun toSourceRecipe(obj: JsonObject): SourceRecipe? {
    if (!isRecipe(obj)) return null
    val name =
        (obj["name"] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.trim()
            .orEmpty()
    val ingredients = strings(obj["recipeIngredient"])
    if (name.isEmpty() || ingredients.isEmpty()) return null
    return SourceRecipe(name, servings(obj["recipeYield"]), ingredients, steps(obj["recipeInstructions"], 0))
}

private fun strings(element: JsonElement?): List<String> =
    when (element) {
        is JsonPrimitive -> listOfNotNull(element.takeIf { it.isString }?.content)
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        else -> emptyList()
    }.map { it.trim() }.filter { it.isNotEmpty() }

private fun servings(element: JsonElement?): Int? =
    when (element) {
        is JsonPrimitive ->
            if (element.isString) {
                DIGITS.find(element.content)?.value?.toIntOrNull()
            } else {
                element.doubleOrNull?.toInt()
            }
        is JsonArray -> element.firstNotNullOfOrNull { servings(it) }
        else -> null
    }

private fun steps(
    element: JsonElement?,
    depth: Int,
): List<String> {
    if (depth > JSON_LD_DEPTH_MAX) return emptyList()
    val raw =
        when (element) {
            is JsonPrimitive -> if (element.isString) element.content.lines() else emptyList()
            is JsonArray -> element.flatMap { steps(it, depth + 1) }
            is JsonObject -> stepsOfObject(element, depth)
            else -> emptyList()
        }
    return raw.map { it.replace(WHITESPACE, " ").trim() }.filter { it.isNotEmpty() }
}

private fun stepsOfObject(
    obj: JsonObject,
    depth: Int,
): List<String> {
    val section = obj["itemListElement"]
    return when {
        section != null -> steps(section, depth + 1)
        else -> strings(obj["text"] ?: obj["name"])
    }
}
