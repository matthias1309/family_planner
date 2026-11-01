package de.familyplanner.android.feature.gamification.data

import de.familyplanner.android.core.ui.avatar.AvatarLook
import kotlinx.serialization.Serializable

internal const val AVATAR_CACHE_VERSION = 1

/** Dateiformat des lokalen Avatar-Stands (ADR-0011), getrennt von der Domäne. */
@Serializable
data class StoredAvatars(
    val version: Int = AVATAR_CACHE_VERSION,
    val looks: List<StoredLook> = emptyList(),
)

@Serializable
data class StoredLook(
    val memberId: String,
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
)

internal fun Map<String, AvatarLook>.toStored() =
    StoredAvatars(
        looks =
            map { (memberId, look) ->
                StoredLook(
                    memberId = memberId,
                    skin = look.skin,
                    top = look.top,
                    hairColor = look.hairColor,
                    hatColor = look.hatColor,
                    eyes = look.eyes,
                    eyebrows = look.eyebrows,
                    mouth = look.mouth,
                    clothes = look.clothes,
                    clothesColor = look.clothesColor,
                    background = look.background,
                    clothesGraphic = look.clothesGraphic,
                    accessories = look.accessories,
                    facialHair = look.facialHair,
                )
            },
    )

internal fun StoredAvatars.toDomain(): Map<String, AvatarLook> =
    looks.associate {
        it.memberId to
            AvatarLook(
                skin = it.skin,
                top = it.top,
                hairColor = it.hairColor,
                hatColor = it.hatColor,
                eyes = it.eyes,
                eyebrows = it.eyebrows,
                mouth = it.mouth,
                clothes = it.clothes,
                clothesColor = it.clothesColor,
                background = it.background,
                clothesGraphic = it.clothesGraphic,
                accessories = it.accessories,
                facialHair = it.facialHair,
            )
    }
