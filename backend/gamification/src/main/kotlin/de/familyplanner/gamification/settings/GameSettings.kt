package de.familyplanner.gamification.settings

import java.time.Instant

data class GameSettings(
    val cookingPoints: Int,
)

const val COOKING_POINTS_DEFAULT = 3
const val COOKING_POINTS_MIN = 1
const val COOKING_POINTS_MAX = 20

interface GameSettingsRepository {
    suspend fun find(): GameSettings?

    suspend fun save(
        settings: GameSettings,
        at: Instant,
    )
}
