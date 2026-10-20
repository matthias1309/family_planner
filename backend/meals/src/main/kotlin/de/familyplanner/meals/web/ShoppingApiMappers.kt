package de.familyplanner.meals.web

import de.familyplanner.meals.shopping.EntrySource
import de.familyplanner.meals.shopping.ShoppingEntry
import de.familyplanner.meals.shopping.ShoppingList
import de.familyplanner.api.model.ShoppingEntry as ApiShoppingEntry
import de.familyplanner.api.model.ShoppingEntrySource as ApiShoppingEntrySource
import de.familyplanner.api.model.ShoppingList as ApiShoppingList

fun ShoppingList.toApi() = ApiShoppingList(weekStart = week.monday.toString(), propertyEntries = entries.map { it.toApi() })

fun ShoppingEntry.toApi() =
    ApiShoppingEntry(
        key = key,
        name = name,
        checked = checked,
        sources = sources.map { it.toApi() },
        quantity = quantity,
        unit = unit,
        itemId = itemId?.value,
    )

fun EntrySource.toApi() = ApiShoppingEntrySource(dishId = dishId.value, dishName = dishName, date = date.toString())
