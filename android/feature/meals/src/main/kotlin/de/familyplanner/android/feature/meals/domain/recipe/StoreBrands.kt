package de.familyplanner.android.feature.meals.domain.recipe

/** Eigenmarken-Präfixe (ADR-0012), längster Treffer zuerst; erweiterbar. */
private val STORE_BRANDS = listOf("REWE Beste Wahl", "REWE Feine Welt", "REWE Bio", "ja!", "REWE").sortedByDescending { it.length }

/** Entfernt ein Eigenmarken-Präfix am Anfang des Namens, nur als ganzes Wort; bleibt nichts übrig, gilt der ursprüngliche Name. */
fun stripStoreBrand(name: String): String {
    val text = name.trim()
    val brand =
        STORE_BRANDS.firstOrNull {
            text.startsWith(it, ignoreCase = true) && (text.length == it.length || text[it.length].isWhitespace())
        } ?: return text
    return text.substring(brand.length).trim().ifEmpty { text }
}
