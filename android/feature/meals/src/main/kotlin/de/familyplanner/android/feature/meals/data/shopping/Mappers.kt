package de.familyplanner.android.feature.meals.data.shopping

import de.familyplanner.android.feature.meals.data.mapAllOrNull
import de.familyplanner.android.feature.meals.data.parseDate
import de.familyplanner.android.feature.meals.domain.shopping.ShoppingEntry
import de.familyplanner.android.feature.meals.domain.shopping.ShoppingList
import de.familyplanner.android.feature.meals.domain.shopping.ShoppingSource
import de.familyplanner.api.model.ShoppingEntry as ApiShoppingEntry
import de.familyplanner.api.model.ShoppingEntrySource as ApiShoppingEntrySource
import de.familyplanner.api.model.ShoppingList as ApiShoppingList

internal fun ApiShoppingEntrySource.toDomain(): ShoppingSource? = parseDate(date)?.let { ShoppingSource(dishId, dishName, it) }

internal fun ApiShoppingEntry.toDomain(): ShoppingEntry? =
    sources.mapAllOrNull { it.toDomain() }?.let { ShoppingEntry(key, name, quantity, unit, checked, itemId, it) }

/** Der Generator nennt `entries` in `propertyEntries` um (Kotlin-Reservierung); JSON bleibt `entries`. */
internal fun ApiShoppingList.toDomain(): ShoppingList? {
    val start = parseDate(weekStart) ?: return null
    return propertyEntries.mapAllOrNull { it.toDomain() }?.let { ShoppingList(start, it) }
}
