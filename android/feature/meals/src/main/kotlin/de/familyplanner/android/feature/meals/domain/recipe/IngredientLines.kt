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

private const val NUMBER = """(\d+(?:[.,]\d+)*)"""

// Zahl, Leerzeichen, Rest („500 g Mehl“, „2 Eier“) – oder Zahl direkt vor einer Einheit („500g Mehl“, nur bei bekannter Einheit).
private val SPACED_NUMBER = Regex("""^$NUMBER\s+(.+)$""")
private val GLUED_NUMBER = Regex("""^$NUMBER(?<unit>\p{L}[\p{L}.()]*)\s+(?<name>.+)$""")

// „1.000 g“, „12.500 ml“: Punkt als Tausendertrenner (erste Gruppe ohne führende 0), aber nur bei g/mg/ml – bei „2.250 l“ oder
// „1.500 kg“ ist ein Dezimalpunkt gemeint. „0.125“, „500.0“, „2.5“ bleiben Dezimalzahlen.
private val THOUSANDS = Regex("""^[1-9]\d{0,2}(\.\d{3})+$""")
private val THOUSANDS_UNITS = setOf("g", "mg", "ml")

/** Zerlegt eine Zeile in Menge/Einheit/Name; alles, was nicht sicher passt, wird ganz zum Namen (ADR-0012). */
fun parseIngredientLine(line: String): Ingredient {
    val text = line.trim()
    val glued = GLUED_NUMBER.matchEntire(text)
    val gluedUnit = glued?.groups?.get("unit")?.value
    if (glued != null && gluedUnit != null && isUnit(gluedUnit)) {
        return withQuantity(
            text,
            glued.groupValues[1],
            gluedUnit,
            glued.groups["name"]
                ?.value
                .orEmpty()
                .trim(),
        )
    }
    val match = SPACED_NUMBER.matchEntire(text) ?: return Ingredient(text)
    val rest = match.groupValues[2].trim()
    val first = rest.substringBefore(' ')
    val afterFirst = rest.substringAfter(' ', missingDelimiterValue = "").trim()
    return if (isUnit(first) && afterFirst.isNotEmpty()) {
        withQuantity(text, match.groupValues[1], first, afterFirst)
    } else {
        withQuantity(text, match.groupValues[1], null, rest)
    }
}

private fun withQuantity(
    text: String,
    rawQuantity: String,
    unit: String?,
    name: String,
): Ingredient {
    val quantity =
        parseQuantity(rawQuantity, unit)?.takeIf { it in RecipeLimits.QUANTITY_MIN..RecipeLimits.QUANTITY_MAX }
            ?: return Ingredient(text)
    return Ingredient(name, quantity, unit)
}

private fun isUnit(token: String) =
    token.length <= RecipeLimits.UNIT_MAX && (token.lowercase() in KNOWN_UNITS || PLURAL_UNIT.matches(token))

private fun parseQuantity(
    raw: String,
    unit: String?,
): Double? {
    val thousands = THOUSANDS.matches(raw) && unit?.lowercase() in THOUSANDS_UNITS
    val normalized = if (thousands) raw.replace(".", "") else raw.replace(',', '.')
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
