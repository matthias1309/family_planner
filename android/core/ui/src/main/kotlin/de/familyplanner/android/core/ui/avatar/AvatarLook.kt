package de.familyplanner.android.core.ui.avatar

private const val FIXED_ACCESSORY_COLOR = "#262e33"

/** What the renderer needs: component variants, named colors (with `#`) and the background circle color. */
data class AvatarSelection(
    val components: Map<String, String>,
    val colors: Map<String, String>,
    val background: String,
)

/**
 * An avatar as stored by the backend: item ids of the form `<slot>.<value>` (see `AvatarCatalog` on the server).
 * Optional slots are `null` when nothing is worn.
 */
data class AvatarLook(
    val skin: String,
    val top: String,
    val hairColor: String,
    val hatColor: String,
    val eyes: String,
    val eyebrows: String,
    val mouth: String,
    val clothes: String,
    val clothesColor: String,
    val background: String,
    val clothesGraphic: String? = null,
    val accessories: String? = null,
    val facialHair: String? = null,
) {
    fun toSelection(): AvatarSelection {
        val components =
            buildMap {
                put("top", valueOf(top))
                put("eyes", valueOf(eyes))
                put("eyebrows", valueOf(eyebrows))
                put("mouth", valueOf(mouth))
                put("clothes", valueOf(clothes))
                put("nose", "default")
                clothesGraphic?.let { put("clothesGraphic", valueOf(it)) }
                accessories?.let { put("accessories", valueOf(it)) }
                facialHair?.let { put("facialHair", valueOf(it)) }
            }
        val colors =
            mapOf(
                "skin" to hex(skin),
                "hair" to hex(hairColor),
                "facialHair" to hex(hairColor),
                "hat" to hex(hatColor),
                "clothes" to hex(clothesColor),
                "accessories" to FIXED_ACCESSORY_COLOR,
            )
        return AvatarSelection(components, colors, hex(background))
    }

    private fun hex(itemId: String): String = "#${valueOf(itemId)}"

    private fun valueOf(itemId: String): String {
        val separator = itemId.indexOf('.')
        require(separator > 0 && separator < itemId.length - 1) { "Invalid avatar item id: $itemId" }
        return itemId.substring(separator + 1)
    }
}
