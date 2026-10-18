package de.familyplanner.android.feature.meals.domain.recipe

private val LINK = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
private const val TRAILING = ".,;:!?)]}\"'»“”"

/** Erster http(s)-Link im geteilten oder eingefügten Text (REWE-App teilt „Titel https://…“), ohne Satzzeichen am Ende. */
fun extractRecipeUrl(text: String): String? =
    LINK
        .find(text)
        ?.value
        ?.trimEnd { it in TRAILING }
        ?.takeIf(::isHttpUrl)
