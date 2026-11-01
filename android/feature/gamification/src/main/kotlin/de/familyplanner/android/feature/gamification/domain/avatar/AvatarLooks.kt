package de.familyplanner.android.feature.gamification.domain.avatar

import de.familyplanner.android.core.ui.avatar.AvatarLook

/** Die getragene Teile-ID im [slot]; `null` bei einem leeren optionalen Slot. */
fun AvatarLook.itemIn(slot: AvatarSlot): String? =
    when (slot) {
        AvatarSlot.SKIN -> skin
        AvatarSlot.TOP -> top
        AvatarSlot.HAIR_COLOR -> hairColor
        AvatarSlot.HAT_COLOR -> hatColor
        AvatarSlot.EYES -> eyes
        AvatarSlot.EYEBROWS -> eyebrows
        AvatarSlot.MOUTH -> mouth
        AvatarSlot.CLOTHES -> clothes
        AvatarSlot.CLOTHES_COLOR -> clothesColor
        AvatarSlot.CLOTHES_GRAPHIC -> clothesGraphic
        AvatarSlot.ACCESSORIES -> accessories
        AvatarSlot.FACIAL_HAIR -> facialHair
        AvatarSlot.BACKGROUND -> background
    }

/** Kopie mit [itemId] im [slot]; `null` nur für optionale Slots (Pflicht-Slots verlangen eine ID). */
fun AvatarLook.wearing(
    slot: AvatarSlot,
    itemId: String?,
): AvatarLook =
    when (slot) {
        AvatarSlot.SKIN -> copy(skin = required(slot, itemId))
        AvatarSlot.TOP -> copy(top = required(slot, itemId))
        AvatarSlot.HAIR_COLOR -> copy(hairColor = required(slot, itemId))
        AvatarSlot.HAT_COLOR -> copy(hatColor = required(slot, itemId))
        AvatarSlot.EYES -> copy(eyes = required(slot, itemId))
        AvatarSlot.EYEBROWS -> copy(eyebrows = required(slot, itemId))
        AvatarSlot.MOUTH -> copy(mouth = required(slot, itemId))
        AvatarSlot.CLOTHES -> copy(clothes = required(slot, itemId))
        AvatarSlot.CLOTHES_COLOR -> copy(clothesColor = required(slot, itemId))
        AvatarSlot.CLOTHES_GRAPHIC -> copy(clothesGraphic = itemId)
        AvatarSlot.ACCESSORIES -> copy(accessories = itemId)
        AvatarSlot.FACIAL_HAIR -> copy(facialHair = itemId)
        AvatarSlot.BACKGROUND -> copy(background = required(slot, itemId))
    }

private fun required(
    slot: AvatarSlot,
    itemId: String?,
): String = requireNotNull(itemId) { "Slot ${slot.key} is not optional" }

private val OptionalSlots = setOf(AvatarSlot.CLOTHES_GRAPHIC, AvatarSlot.ACCESSORIES, AvatarSlot.FACIAL_HAIR)

private val RequiredSlots = AvatarSlot.entries.filter { it !in OptionalSlots }

/** Erstes kostenloses Teil je Pflicht-Slot, optionale Slots leer; `null`, wenn ein Pflicht-Slot kein kostenloses Teil hat. */
fun defaultLook(catalog: AvatarCatalog): AvatarLook? {
    val ids =
        RequiredSlots
            .mapNotNull { slot ->
                catalog.slots
                    .firstOrNull { it.slot == slot }
                    ?.items
                    ?.firstOrNull { it.isFree }
                    ?.let { slot to it.id }
            }.toMap()
    if (ids.size != RequiredSlots.size) return null
    return AvatarLook(
        skin = ids.getValue(AvatarSlot.SKIN),
        top = ids.getValue(AvatarSlot.TOP),
        hairColor = ids.getValue(AvatarSlot.HAIR_COLOR),
        hatColor = ids.getValue(AvatarSlot.HAT_COLOR),
        eyes = ids.getValue(AvatarSlot.EYES),
        eyebrows = ids.getValue(AvatarSlot.EYEBROWS),
        mouth = ids.getValue(AvatarSlot.MOUTH),
        clothes = ids.getValue(AvatarSlot.CLOTHES),
        clothesColor = ids.getValue(AvatarSlot.CLOTHES_COLOR),
        background = ids.getValue(AvatarSlot.BACKGROUND),
    )
}
