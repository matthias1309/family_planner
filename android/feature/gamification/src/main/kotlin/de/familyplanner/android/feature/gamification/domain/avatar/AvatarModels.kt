package de.familyplanner.android.feature.gamification.domain.avatar

import de.familyplanner.android.core.ui.avatar.AvatarLook

/** Reihenfolge = Editor; [key] = Wert im Vertrag. */
enum class AvatarSlot(
    val key: String,
    val isColor: Boolean,
) {
    SKIN("skin", true),
    TOP("top", false),
    HAIR_COLOR("hairColor", true),
    HAT_COLOR("hatColor", true),
    EYES("eyes", false),
    EYEBROWS("eyebrows", false),
    MOUTH("mouth", false),
    CLOTHES("clothes", false),
    CLOTHES_COLOR("clothesColor", true),
    CLOTHES_GRAPHIC("clothesGraphic", false),
    ACCESSORIES("accessories", false),
    FACIAL_HAIR("facialHair", false),
    BACKGROUND("background", true),
}

enum class ShopCategory { HAIR, HEADWEAR, FACE, CLOTHES, ACCESSORIES, COLORS }

data class AvatarItem(
    val id: String,
    val slot: AvatarSlot,
    val category: ShopCategory,
    val title: String,
    val price: Int,
) {
    val isFree: Boolean get() = price == 0
}

data class AvatarSlotInfo(
    val slot: AvatarSlot,
    val optional: Boolean,
    val items: List<AvatarItem>,
)

data class AvatarCatalog(
    val slots: List<AvatarSlotInfo>,
)

/** [look] ist `null`, bis das Kind einmal gespeichert hat; [owned] = gekaufte Teile (ohne Grundsortiment). */
data class AvatarProfile(
    val memberId: String,
    val look: AvatarLook?,
    val owned: Set<String>,
)

data class AvatarPurchase(
    val item: AvatarItem,
    val balance: Int,
)

/** Rubrik im Editor: ein Slot, beim Slot `top` getrennt nach Kategorie (Frisur/Kopfbedeckung). */
data class AvatarRubric(
    val slot: AvatarSlot,
    val category: ShopCategory,
    val optional: Boolean,
    val items: List<AvatarItem>,
)
