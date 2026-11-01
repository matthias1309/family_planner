package de.familyplanner.android.feature.gamification.domain.avatar

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.avatar.AvatarLook
import de.familyplanner.android.feature.gamification.domain.RewardRepository
import de.familyplanner.android.feature.gamification.domain.SavingsGoal
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Daten des Editors: Katalog, eigenes Profil und Sparziel (liefert auch das Guthaben). */
data class AvatarEditorData(
    val catalog: AvatarCatalog,
    val profile: AvatarProfile,
    val goal: SavingsGoal,
    val startLook: AvatarLook,
)

class LoadAvatarEditorUseCase(
    private val avatars: AvatarRepository,
    private val rewards: RewardRepository,
) {
    suspend operator fun invoke(memberId: String): ApiResult<AvatarEditorData> =
        coroutineScope {
            val catalog = async { avatars.catalog() }
            val profile = async { avatars.profile(memberId) }
            val goal = async { rewards.savingsGoal(memberId) }
            val catalogResult = catalog.await()
            val profileResult = profile.await()
            val goalResult = goal.await()
            val failure = listOf(catalogResult, profileResult, goalResult).filterIsInstance<ApiResult.Failure>().firstOrNull()
            if (failure != null) return@coroutineScope failure
            val loadedCatalog = (catalogResult as ApiResult.Success).data
            val loadedProfile = (profileResult as ApiResult.Success).data
            // Ohne kostenloses Teil in einem Pflicht-Slot ist der Katalog kaputt: Serverfehler statt Absturz.
            val start = loadedProfile.look ?: defaultLook(loadedCatalog) ?: return@coroutineScope ApiResult.Failure(AppError.Server)
            ApiResult.Success(AvatarEditorData(loadedCatalog, loadedProfile, (goalResult as ApiResult.Success).data, start))
        }
}

class AvatarActions(
    private val repository: AvatarRepository,
    private val directory: AvatarDirectory,
) {
    suspend fun save(
        memberId: String,
        look: AvatarLook,
    ): ApiResult<AvatarProfile> =
        repository.save(memberId, look).also {
            if (it is ApiResult.Success) directory.update(memberId, it.data.look ?: look)
        }

    suspend fun purchase(itemId: String): ApiResult<AvatarPurchase> = repository.purchase(itemId)
}
