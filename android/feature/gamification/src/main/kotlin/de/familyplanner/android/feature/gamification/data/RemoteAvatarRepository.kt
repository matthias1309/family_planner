package de.familyplanner.android.feature.gamification.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.apiCall
import de.familyplanner.android.core.ui.avatar.AvatarLook
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarCatalog
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarProfile
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarPurchase
import de.familyplanner.android.feature.gamification.domain.avatar.AvatarRepository
import de.familyplanner.api.client.GamificationApi
import kotlinx.serialization.json.Json

class RemoteAvatarRepository(
    private val api: GamificationApi,
    private val json: Json,
) : AvatarRepository {
    override suspend fun catalog(): ApiResult<AvatarCatalog> = apiCall(json, { api.getAvatarCatalog() }) { it.data?.toDomain() }

    override suspend fun avatars(): ApiResult<Map<String, AvatarLook>> =
        apiCall(json, { api.listAvatars() }) { response -> response.data?.toLooks() }

    override suspend fun profile(memberId: String): ApiResult<AvatarProfile> =
        apiCall(json, { api.getAvatarProfile(memberId) }) { it.data?.toDomain() }

    override suspend fun save(
        memberId: String,
        look: AvatarLook,
    ): ApiResult<AvatarProfile> = apiCall(json, { api.saveAvatar(memberId, look.toApi()) }) { it.data?.toDomain() }

    override suspend fun purchase(itemId: String): ApiResult<AvatarPurchase> =
        apiCall(json, { api.purchaseAvatarItem(itemId) }) { it.data?.toDomain() }
}
