package de.familyplanner.android.feature.gamification.fakes

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.ui.avatar.AvatarLook
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarCache
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarCatalog
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarItem
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarProfile
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarPurchase
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarRepository
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarSlot
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarSlotInfo
import de.familyplanner.android.feature.gamification.domain.avatar.ShopCategory

private fun item(
    id: String,
    price: Int = 0,
    category: ShopCategory = ShopCategory.FACE,
    title: String = id.substringAfter('.'),
) = AvatarItem(id, AvatarSlot.entries.first { it.key == id.substringBefore('.') }, category, title, price)

private fun slot(
    slot: AvatarSlot,
    vararg items: AvatarItem,
    optional: Boolean = false,
) = AvatarSlotInfo(slot, optional, items.toList())

/** Kleiner Katalog: je Pflicht-Slot ein freies Teil, dazu bigHair (20), winterHat02 (30), wayfarers (25). */
fun sampleCatalog() =
    AvatarCatalog(
        listOf(
            slot(AvatarSlot.SKIN, item("skin.EDB98A", category = ShopCategory.COLORS), item("skin.AE5D29", category = ShopCategory.COLORS)),
            slot(
                AvatarSlot.TOP,
                item("top.shortFlat", category = ShopCategory.HAIR, title = "Kurz"),
                item("top.bigHair", 20, ShopCategory.HAIR, "Große Haare"),
                item("top.hijab", category = ShopCategory.HEADWEAR),
                item("top.winterHat02", 30, ShopCategory.HEADWEAR, "Bommelmütze"),
            ),
            slot(AvatarSlot.HAIR_COLOR, item("hairColor.2C1B18", category = ShopCategory.COLORS)),
            slot(AvatarSlot.HAT_COLOR, item("hatColor.262E33", category = ShopCategory.COLORS)),
            slot(AvatarSlot.EYES, item("eyes.default")),
            slot(AvatarSlot.EYEBROWS, item("eyebrows.default")),
            slot(AvatarSlot.MOUTH, item("mouth.smile")),
            slot(AvatarSlot.CLOTHES, item("clothes.hoodie", category = ShopCategory.CLOTHES)),
            slot(AvatarSlot.CLOTHES_COLOR, item("clothesColor.65C9FF", category = ShopCategory.COLORS)),
            slot(AvatarSlot.CLOTHES_GRAPHIC, item("clothesGraphic.bat", category = ShopCategory.CLOTHES), optional = true),
            slot(AvatarSlot.ACCESSORIES, item("accessories.wayfarers", 25, ShopCategory.ACCESSORIES, "Sonnenbrille"), optional = true),
            slot(AvatarSlot.FACIAL_HAIR, optional = true),
            slot(AvatarSlot.BACKGROUND, item("background.FFFFFF", category = ShopCategory.COLORS)),
        ),
    )

val freeLook =
    AvatarLook(
        skin = "skin.EDB98A",
        top = "top.shortFlat",
        hairColor = "hairColor.2C1B18",
        hatColor = "hatColor.262E33",
        eyes = "eyes.default",
        eyebrows = "eyebrows.default",
        mouth = "mouth.smile",
        clothes = "clothes.hoodie",
        clothesColor = "clothesColor.65C9FF",
        background = "background.FFFFFF",
    )

class FakeAvatarRepository : AvatarRepository {
    var catalogResult: ApiResult<AvatarCatalog> = ApiResult.Success(sampleCatalog())
    var avatarsResult: ApiResult<Map<String, AvatarLook>> = ApiResult.Success(emptyMap())
    var profileResult: ApiResult<AvatarProfile> = ApiResult.Success(AvatarProfile("c-1", null, emptySet()))
    var saveResult: ApiResult<AvatarProfile>? = null
    var purchaseResult: ApiResult<AvatarPurchase>? = null

    var avatarsCalls = 0
    val saveCalls = mutableListOf<Pair<String, AvatarLook>>()
    val purchaseCalls = mutableListOf<String>()

    override suspend fun catalog() = catalogResult

    override suspend fun avatars(): ApiResult<Map<String, AvatarLook>> {
        avatarsCalls++
        return avatarsResult
    }

    override suspend fun profile(memberId: String) = profileResult

    override suspend fun save(
        memberId: String,
        look: AvatarLook,
    ): ApiResult<AvatarProfile> {
        saveCalls += memberId to look
        return saveResult ?: ApiResult.Success(AvatarProfile(memberId, look, emptySet()))
    }

    override suspend fun purchase(itemId: String): ApiResult<AvatarPurchase> {
        purchaseCalls += itemId
        return purchaseResult ?: ApiResult.Success(AvatarPurchase(sampleCatalog().slots.flatMap { it.items }.first { it.id == itemId }, 0))
    }
}

class InMemoryAvatarCache : AvatarCache {
    var stored: Map<String, AvatarLook>? = null
    var cleared = 0

    override suspend fun read() = stored

    override suspend fun write(looks: Map<String, AvatarLook>) {
        stored = looks
    }

    override suspend fun clear() {
        stored = null
        cleared++
    }
}
