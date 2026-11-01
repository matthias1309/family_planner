package de.familyplanner.android.feature.gamification.domain.avatar

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.ui.avatar.AvatarLook

interface AvatarRepository {
    suspend fun catalog(): ApiResult<AvatarCatalog>

    /** Gespeicherte Avatare aller Kinder: memberId → Look. */
    suspend fun avatars(): ApiResult<Map<String, AvatarLook>>

    suspend fun profile(memberId: String): ApiResult<AvatarProfile>

    suspend fun save(
        memberId: String,
        look: AvatarLook,
    ): ApiResult<AvatarProfile>

    suspend fun purchase(itemId: String): ApiResult<AvatarPurchase>
}

/** Lokaler Stand der Avatare (ADR-0011). Fehler beim Speichern sind nie ein Nutzerfehler. */
interface AvatarCache {
    /** Gespeicherter Stand, sonst `null` (nie geschrieben, unlesbar oder andere Version). */
    suspend fun read(): Map<String, AvatarLook>?

    suspend fun write(looks: Map<String, AvatarLook>)

    suspend fun clear()
}
