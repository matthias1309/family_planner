package de.familyplanner.android.feature.gamification.data

import de.familyplanner.android.core.ui.avatar.AvatarLook
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarCatalog
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarItem
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarProfile
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarPurchase
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarSlot
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarSlotInfo
import de.familyplanner.android.feature.gamification.domain.avatar.ShopCategory
import de.familyplanner.api.model.AvatarCatalog as ApiAvatarCatalog
import de.familyplanner.api.model.AvatarConfig as ApiAvatarConfig
import de.familyplanner.api.model.AvatarItem as ApiAvatarItem
import de.familyplanner.api.model.AvatarProfile as ApiAvatarProfile
import de.familyplanner.api.model.AvatarPurchase as ApiAvatarPurchase
import de.familyplanner.api.model.MemberAvatar as ApiMemberAvatar

internal fun ApiAvatarConfig.toDomain() =
    AvatarLook(
        skin = skin,
        top = top,
        hairColor = hairColor,
        hatColor = hatColor,
        eyes = eyes,
        eyebrows = eyebrows,
        mouth = mouth,
        clothes = clothes,
        clothesColor = clothesColor,
        background = background,
        clothesGraphic = clothesGraphic,
        accessories = accessories,
        facialHair = facialHair,
    )

internal fun AvatarLook.toApi() =
    ApiAvatarConfig(
        skin = skin,
        top = top,
        hairColor = hairColor,
        hatColor = hatColor,
        eyes = eyes,
        eyebrows = eyebrows,
        mouth = mouth,
        clothes = clothes,
        clothesColor = clothesColor,
        background = background,
        clothesGraphic = clothesGraphic,
        accessories = accessories,
        facialHair = facialHair,
    )

/** `null` bei einem Slot oder einer Kategorie, die die App nicht kennt (neuer Server) — Serverfehler statt halber Antwort. */
internal fun ApiAvatarItem.toDomain(): AvatarItem? {
    val mappedSlot = AvatarSlot.entries.firstOrNull { it.name == slot.name } ?: return null
    val mappedCategory = ShopCategory.entries.firstOrNull { it.name == category.name } ?: return null
    return AvatarItem(id, mappedSlot, mappedCategory, title, price)
}

internal fun ApiAvatarCatalog.toDomain(): AvatarCatalog? {
    val mapped =
        slots.mapAllOrNull { info ->
            val mappedSlot = AvatarSlot.entries.firstOrNull { it.name == info.slot.name } ?: return@mapAllOrNull null
            val items = info.items.mapAllOrNull { it.toDomain() } ?: return@mapAllOrNull null
            AvatarSlotInfo(mappedSlot, info.optional, items)
        }
    return mapped?.let { AvatarCatalog(it) }
}

internal fun ApiAvatarProfile.toDomain() = AvatarProfile(memberId, avatar?.toDomain(), ownedItemIds.toSet())

internal fun ApiAvatarPurchase.toDomain(): AvatarPurchase? = item.toDomain()?.let { AvatarPurchase(it, balance) }

internal fun List<ApiMemberAvatar>.toLooks(): Map<String, AvatarLook> = associate { it.memberId to it.avatar.toDomain() }
