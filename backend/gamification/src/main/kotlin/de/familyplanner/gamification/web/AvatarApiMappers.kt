package de.familyplanner.gamification.web

import de.familyplanner.gamification.avatar.AvatarConfig
import de.familyplanner.gamification.avatar.AvatarItem
import de.familyplanner.gamification.avatar.AvatarProfile
import de.familyplanner.gamification.avatar.AvatarPurchase
import de.familyplanner.gamification.avatar.AvatarSlot
import de.familyplanner.gamification.avatar.MemberAvatar
import de.familyplanner.gamification.avatar.ShopCategory
import de.familyplanner.api.model.AvatarCatalog as ApiAvatarCatalog
import de.familyplanner.api.model.AvatarConfig as ApiAvatarConfig
import de.familyplanner.api.model.AvatarItem as ApiAvatarItem
import de.familyplanner.api.model.AvatarProfile as ApiAvatarProfile
import de.familyplanner.api.model.AvatarPurchase as ApiAvatarPurchase
import de.familyplanner.api.model.AvatarSlot as ApiAvatarSlot
import de.familyplanner.api.model.AvatarSlotInfo as ApiAvatarSlotInfo
import de.familyplanner.api.model.MemberAvatar as ApiMemberAvatar
import de.familyplanner.api.model.ShopCategory as ApiShopCategory

private fun AvatarSlot.toApi() = ApiAvatarSlot.valueOf(name)

private fun ShopCategory.toApi() = ApiShopCategory.valueOf(name)

fun AvatarItem.toApi() = ApiAvatarItem(id = id, slot = slot.toApi(), category = category.toApi(), title = title, price = price)

fun List<Pair<AvatarSlot, List<AvatarItem>>>.toApiCatalog() =
    ApiAvatarCatalog(slots = map { (slot, items) -> ApiAvatarSlotInfo(slot.toApi(), slot.optional, items.map { it.toApi() }) })

fun AvatarConfig.toApi(): ApiAvatarConfig {
    fun id(slot: AvatarSlot) = checkNotNull(items[slot]) { "Missing required slot ${slot.key}" }
    return ApiAvatarConfig(
        skin = id(AvatarSlot.SKIN),
        top = id(AvatarSlot.TOP),
        hairColor = id(AvatarSlot.HAIR_COLOR),
        hatColor = id(AvatarSlot.HAT_COLOR),
        eyes = id(AvatarSlot.EYES),
        eyebrows = id(AvatarSlot.EYEBROWS),
        mouth = id(AvatarSlot.MOUTH),
        clothes = id(AvatarSlot.CLOTHES),
        clothesColor = id(AvatarSlot.CLOTHES_COLOR),
        background = id(AvatarSlot.BACKGROUND),
        clothesGraphic = items[AvatarSlot.CLOTHES_GRAPHIC],
        accessories = items[AvatarSlot.ACCESSORIES],
        facialHair = items[AvatarSlot.FACIAL_HAIR],
    )
}

/** Required slots that are blank end up as ids the service rejects with 400 (never silently defaulted). */
fun ApiAvatarConfig.toDomain() =
    AvatarConfig(
        listOfNotNull(
            AvatarSlot.SKIN to skin,
            AvatarSlot.TOP to top,
            AvatarSlot.HAIR_COLOR to hairColor,
            AvatarSlot.HAT_COLOR to hatColor,
            AvatarSlot.EYES to eyes,
            AvatarSlot.EYEBROWS to eyebrows,
            AvatarSlot.MOUTH to mouth,
            AvatarSlot.CLOTHES to clothes,
            AvatarSlot.CLOTHES_COLOR to clothesColor,
            AvatarSlot.BACKGROUND to background,
            clothesGraphic?.let { AvatarSlot.CLOTHES_GRAPHIC to it },
            accessories?.let { AvatarSlot.ACCESSORIES to it },
            facialHair?.let { AvatarSlot.FACIAL_HAIR to it },
        ).toMap(),
    )

fun MemberAvatar.toApi() = ApiMemberAvatar(memberId = memberId.value, avatar = config.toApi(), updatedAt = updatedAt.toString())

fun AvatarProfile.toApi() = ApiAvatarProfile(memberId = memberId.value, avatar = avatar?.toApi(), ownedItemIds = ownedItemIds)

fun AvatarPurchase.toApi() = ApiAvatarPurchase(item = item.toApi(), balance = balance)
