package de.familyplanner.meals.plan

import de.familyplanner.core.error.AppException
import de.familyplanner.core.error.validatedName
import java.math.BigDecimal

const val INGREDIENT_MAX = 80
const val INGREDIENTS_MAX_COUNT = 30
const val UNIT_MAX = 20
const val QUANTITY_MAX = 10_000.0

/** Bounds the raw input, blank lines included, before any per-line work. */
const val INGREDIENT_LINES_RAW_MAX = 100

private const val TOO_MANY_INGREDIENTS = "Höchstens $INGREDIENTS_MAX_COUNT Zutaten"

data class Ingredient(
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
)

/** Legacy free-text lines become name-only ingredients: raw limit, trim, drop blank lines. */
fun ingredientsFromLines(lines: List<String>): List<Ingredient> {
    if (lines.size > INGREDIENT_LINES_RAW_MAX) throw AppException.Validation(TOO_MANY_INGREDIENTS)
    return lines.map { it.trim() }.filter { it.isNotEmpty() }.map { Ingredient(it) }
}

/** Trims name and unit, blank unit becomes null, checks limits; shared by dishes and recipes. */
fun validatedIngredients(ingredients: List<Ingredient>): List<Ingredient> {
    if (ingredients.size > INGREDIENTS_MAX_COUNT) throw AppException.Validation(TOO_MANY_INGREDIENTS)
    return ingredients.map { it.validated() }
}

private fun Ingredient.validated(): Ingredient {
    val validName = validatedName(name, "Zutat", INGREDIENT_MAX)
    val validUnit = unit?.trim()?.takeIf { it.isNotEmpty() }
    if (validUnit != null && validUnit.length > UNIT_MAX) {
        throw AppException.Validation("Einheit darf höchstens $UNIT_MAX Zeichen lang sein")
    }
    if (quantity != null && !isValidQuantity(quantity)) {
        throw AppException.Validation("Menge muss größer als 0 und höchstens ${QUANTITY_MAX.toInt()} sein")
    }
    return Ingredient(validName, quantity, validUnit)
}

private fun isValidQuantity(quantity: Double) = quantity.isFinite() && quantity > 0 && quantity <= QUANTITY_MAX

/** Display line for clients that only read `ingredients: string[]`: "500 g Vollkornspaghetti", "2,5 Stange(n) Lauch", "Salz". */
fun Ingredient.displayText(): String = listOfNotNull(quantity?.let(::formatQuantity), unit, name).joinToString(" ")

private fun formatQuantity(value: Double): String =
    BigDecimal
        .valueOf(value)
        .stripTrailingZeros()
        .toPlainString()
        .replace('.', ',')
