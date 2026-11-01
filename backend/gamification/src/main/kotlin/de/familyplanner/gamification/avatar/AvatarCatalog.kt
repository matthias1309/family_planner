package de.familyplanner.gamification.avatar

const val AVATAR_ITEM_TITLE_MAX = 40
const val AVATAR_ITEM_ID_MAX = 64
const val AVATAR_PRICE_MAX = 1000

/** `<slot>.<value>`; the value is a component variant or a lower case hex color without `#`. */
val ITEM_ID_PATTERN = Regex("^[a-zA-Z]+\\.[a-zA-Z0-9]+$")

/** Slots in editor order; [key] is the API/DB value and the prefix of every item id. */
enum class AvatarSlot(
    val key: String,
    val optional: Boolean,
) {
    SKIN("skin", false),
    TOP("top", false),
    HAIR_COLOR("hairColor", false),
    HAT_COLOR("hatColor", false),
    EYES("eyes", false),
    EYEBROWS("eyebrows", false),
    MOUTH("mouth", false),
    CLOTHES("clothes", false),
    CLOTHES_COLOR("clothesColor", false),
    CLOTHES_GRAPHIC("clothesGraphic", true),
    ACCESSORIES("accessories", true),
    FACIAL_HAIR("facialHair", true),
    BACKGROUND("background", false),
    ;

    companion object {
        fun fromKey(key: String): AvatarSlot? = entries.firstOrNull { it.key == key }
    }
}

enum class ShopCategory { HAIR, HEADWEAR, FACE, CLOTHES, ACCESSORIES, COLORS }

/** One wearable part; [price] 0 = basic range (free, owned by everyone, not purchasable). */
data class AvatarItem(
    val id: String,
    val slot: AvatarSlot,
    val category: ShopCategory,
    val title: String,
    val price: Int,
) {
    val isFree: Boolean get() = price == 0
}

/** The fixed catalog (see `docs/03-design/gamification.md`); item ids are stored and must never change. */
object AvatarCatalog {
    val items: List<AvatarItem> = AVATAR_ITEMS

    private val byId = items.associateBy { it.id }
    private val bySlot = items.groupBy { it.slot }

    fun find(id: String): AvatarItem? = byId[id]

    fun itemsOf(slot: AvatarSlot): List<AvatarItem> = bySlot.getValue(slot)

    /** First free item of [slot]: what an avatar gets when nothing valid is stored. */
    fun defaultOf(slot: AvatarSlot): AvatarItem = itemsOf(slot).first { it.isFree }
}
