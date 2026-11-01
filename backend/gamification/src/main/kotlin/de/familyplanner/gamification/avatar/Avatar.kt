package de.familyplanner.gamification.avatar

import de.familyplanner.core.error.AppException
import de.familyplanner.core.member.MemberId
import java.time.Instant

/** Item id per slot; optional slots that are not worn are absent. */
data class AvatarConfig(
    val items: Map<AvatarSlot, String>,
)

data class MemberAvatar(
    val memberId: MemberId,
    val config: AvatarConfig,
    val updatedAt: Instant,
)

/** [avatar] is null until the child saves once; [ownedItemIds] are purchased items only (not the basic range). */
data class AvatarProfile(
    val memberId: MemberId,
    val avatar: AvatarConfig?,
    val ownedItemIds: List<String>,
)

/** Purchase result: the item and the balance after paying. */
data class AvatarPurchase(
    val item: AvatarItem,
    val balance: Int,
)

sealed interface PurchaseOutcome {
    data class Purchased(
        val balance: Int,
    ) : PurchaseOutcome

    data object AlreadyOwned : PurchaseOutcome

    data class InsufficientBalance(
        val balance: Int,
        val price: Int,
    ) : PurchaseOutcome
}

/** A stored config read back: [repaired] = something unknown was dropped or replaced (catalog changed). */
data class SanitizedAvatar(
    val config: AvatarConfig,
    val repaired: Boolean,
)

/** Checks a config a child wants to save; error order: missing slot / unknown / wrong slot (400), not owned (409). */
fun validated(
    config: AvatarConfig,
    owned: Set<String>,
): AvatarConfig {
    val missing = AvatarSlot.entries.firstOrNull { !it.optional && it !in config.items }
    if (missing != null) throw AppException.Validation("Pflicht-Teil fehlt: ${missing.key}")
    val worn = config.items.map { (slot, id) -> knownItem(slot, id) }
    worn.firstOrNull { !it.isFree && it.id !in owned }?.let { throw AppException.Conflict("Noch nicht gekauft: ${it.title}") }
    return config
}

private fun knownItem(
    slot: AvatarSlot,
    id: String,
): AvatarItem {
    val item = AvatarCatalog.find(id) ?: throw AppException.Validation("Unbekanntes Teil: ${id.take(AVATAR_ITEM_ID_MAX)}")
    if (item.slot != slot) throw AppException.Validation("Teil passt nicht zu ${slot.key}: ${item.id}")
    return item
}

/** Reads stored slot rows defensively: unknown slots/items are dropped, missing required slots get the free default. */
fun sanitized(stored: Map<String, String>): SanitizedAvatar {
    val known =
        stored
            .mapNotNull { (key, id) ->
                val slot = AvatarSlot.fromKey(key) ?: return@mapNotNull null
                val item = AvatarCatalog.find(id)?.takeIf { it.slot == slot } ?: return@mapNotNull null
                slot to item.id
            }.toMap()
    val defaults = AvatarSlot.entries.filter { !it.optional && it !in known }.associateWith { AvatarCatalog.defaultOf(it).id }
    return SanitizedAvatar(AvatarConfig(known + defaults), repaired = known.size != stored.size || defaults.isNotEmpty())
}
