package de.familyplanner.android.feature.meals.ui.plan

import de.familyplanner.android.feature.meals.ui.dish.DishEditArgs

/** Aktionen der Karten (hält die Parameterlisten kurz). */
class StatusActions(
    val onOpen: (DishEditArgs) -> Unit,
    val onCancel: (memberId: String) -> Unit,
    val onWithdraw: (memberId: String) -> Unit,
)
