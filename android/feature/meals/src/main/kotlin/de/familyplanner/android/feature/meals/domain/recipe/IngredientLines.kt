package de.familyplanner.android.feature.meals.domain.recipe

import de.familyplanner.android.feature.meals.domain.plan.ingredientLines
import java.math.BigDecimal
import java.math.RoundingMode

/** Häufige Einheiten (Vergleich ohne Groß-/Kleinschreibung); dazu jedes Wort mit Pluralendung in Klammern, z. B. „Stange(n)“. */
private val KNOWN_UNITS =
    setOf(
        "g",
        "kg",
        "mg",
        "ml",
        "cl",
        "dl",
        "l",
        "liter",
        "el",
        "tl",
        "msp.",
        "prise",
        "prisen",
        "bund",
        "becher",
        "dose",
        "dosen",
        "glas",
        "gläser",
        "packung",
        "packungen",
        "pck.",
        "päckchen",
        "stück",
        "stk.",
        "scheibe",
        "scheiben",
        "zehe",
        "zehen",
        "stange",
        "stangen",
        "zweig",
        "zweige",
        "blatt",
        "blätter",
        "handvoll",
        "tasse",
        "tassen",
        "flasche",
        "flaschen",
        "würfel",
        "kopf",
        "köpfe",
        "knolle",
        "knollen",
        "beutel",
        "tube",
        "tuben",
        "schale",
        "schalen",
    )
private val PLURAL_UNIT = Regex("""^\p{L}+\.?\(\p{L}{1,3}\)$""")

// Zahl am Anfang, danach Leerzeichen oder direkt ein Buchstabe („500g Mehl“); Rest ist Einheit+Name oder Name.
private val LEADING_NUMBER = Regex("""^(\d+(?:[.,]\d+)*)(?:\s+|(?=\p{L}))(.+)$""")

// „1.000“, „12.500“: Punkt als Tausendertrenner (erste Gruppe ohne führende 0); „0.125“, „500.0“, „2.5“ bleiben Dezimalzahlen.
private val THOUSANDS = Regex("""^[1-9]\d{0,2}(\.\d{3})+$""")

/** Zerlegt eine Zeile in Menge/Einheit/Name; alles, was nicht sicher passt, wird ganz zum Namen (ADR-0012). */
fun parseIngredientLine(line: String): Ingredient {
    val text = line.trim()
    val match = LEADING_NUMBER.matchEntire(text) ?: return Ingredient(text)
    val quantity =
        parseQuantity(match.groupValues[1])?.takeIf { it in RecipeLimits.QUANTITY_MIN..RecipeLimits.QUANTITY_MAX }
            ?: return Ingredient(text)
    val rest = match.groupValues[2].trim()
    val first = rest.substringBefore(' ')
    val afterFirst = rest.substringAfter(' ', missingDelimiterValue = "").trim()
    return if (isUnit(first) && afterFirst.isNotEmpty()) {
        Ingredient(afterFirst, quantity, first)
    } else {
        Ingredient(rest, quantity)
    }
}

private fun isUnit(token: String) =
    token.length <= RecipeLimits.UNIT_MAX && (token.lowercase() in KNOWN_UNITS || PLURAL_UNIT.matches(token))

private fun parseQuantity(raw: String): Double? {
    val normalized = if (THOUSANDS.matches(raw)) raw.replace(".", "") else raw.replace(',', '.')
    return normalized.toDoubleOrNull()?.let(::roundedQuantity)
}

internal fun roundedQuantity(value: Double): Double =
    BigDecimal.valueOf(value).setScale(RecipeLimits.QUANTITY_SCALE, RoundingMode.HALF_UP).toDouble()

/** Zeilen des Zutatenfelds (getrimmt, ohne Leerzeilen, vgl. [ingredientLines]) als zerlegte Zutaten. */
fun ingredientsFromText(text: String): List<Ingredient> = ingredientLines(text).map(::parseIngredientLine)

/** Wie Backend `displayText()`: „500 g Vollkornspaghetti“, „2,5 Stange(n) Lauch“, „Salz“. */
fun Ingredient.displayText(): String = listOfNotNull(quantity?.let(::formatQuantity), unit, name).joinToString(" ")

fun formatQuantity(value: Double): String =
    BigDecimal
        .valueOf(value)
        .setScale(RecipeLimits.QUANTITY_SCALE, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
        .replace('.', ',')

/** Zutaten als Text fürs Eingabefeld, eine pro Zeile. */
fun ingredientsToText(ingredients: List<Ingredient>): String = ingredients.joinToString("\n") { it.displayText() }
