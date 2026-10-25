package de.familyplanner.gamification.settings

import de.familyplanner.core.error.AppException
import org.slf4j.LoggerFactory
import java.time.Clock

class GameSettingsService(
    private val repository: GameSettingsRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(GameSettingsService::class.java)

    suspend fun current(): GameSettings = repository.find() ?: GameSettings(COOKING_POINTS_DEFAULT)

    suspend fun update(cookingPoints: Int): GameSettings {
        if (cookingPoints !in COOKING_POINTS_MIN..COOKING_POINTS_MAX) {
            throw AppException.Validation("Kochpunkte müssen zwischen $COOKING_POINTS_MIN und $COOKING_POINTS_MAX liegen")
        }
        val settings = GameSettings(cookingPoints)
        repository.save(settings, clock.instant())
        log.info("Cooking points set to {}", cookingPoints)
        return settings
    }
}
