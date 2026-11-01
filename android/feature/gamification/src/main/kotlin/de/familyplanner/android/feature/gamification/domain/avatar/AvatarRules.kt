package de.familyplanner.android.feature.gamification.domain.avatar

import de.familyplanner.android.core.ui.avatar.AvatarLook
import de.familyplanner.android.feature.gamification.domain.SavingsGoal

/** Je Slot in Katalogreihenfolge; Teile gruppiert nach Kategorie in Reihenfolge des ersten Auftretens. */
fun AvatarCatalog.rubrics(): List<AvatarRubric> =
    slots.flatMap { info ->
        info.items.groupBy { it.category }.map { (category, items) -> AvatarRubric(info.slot, category, info.optional, items) }
    }

fun AvatarItem.isWearable(owned: Set<String>): Boolean = isFree || id in owned

sealed interface PurchaseOption {
    /** Kostenlos oder schon gekauft: kann getragen werden. */
    data object Wearable : PurchaseOption

    data class Buyable(
        val price: Int,
    ) : PurchaseOption

    /** Zu wenig Guthaben: [points] fehlen. */
    data class Missing(
        val points: Int,
    ) : PurchaseOption
}

fun purchaseOption(
    item: AvatarItem,
    owned: Set<String>,
    balance: Int,
): PurchaseOption =
    when {
        item.isWearable(owned) -> PurchaseOption.Wearable
        balance >= item.price -> PurchaseOption.Buyable(item.price)
        else -> PurchaseOption.Missing(item.price - balance)
    }

/** Ein Kauf senkt den Fortschritt zum Sparziel, wenn danach weniger als der Zielpreis übrig bleibt (REQ-GAME-041). */
fun goalWarning(
    goal: SavingsGoal?,
    balance: Int,
    price: Int,
): Boolean {
    val reward = goal?.reward ?: return false
    return !reward.archived && balance - price < reward.price
}

/** Speichern geht, wenn der Entwurf nur bekannte, tragbare Teile enthält und sich vom Gespeicherten unterscheidet. */
fun canSave(
    draft: AvatarLook,
    saved: AvatarLook?,
    catalog: AvatarCatalog,
    owned: Set<String>,
): Boolean {
    if (saved != null && draft == saved) return false
    val itemsById = catalog.slots.flatMap { it.items }.associateBy { it.id }
    return AvatarSlot.entries.all { slot ->
        val id = draft.itemIn(slot) ?: return@all true
        itemsById[id]?.isWearable(owned) == true
    }
}
