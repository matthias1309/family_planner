package de.familyplanner.gamification.persistence

import de.familyplanner.core.persistence.dbQuery
import de.familyplanner.gamification.settings.GameSettings
import de.familyplanner.gamification.settings.GameSettingsRepository
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Instant

class ExposedGameSettingsRepository(
    private val db: Database,
) : GameSettingsRepository {
    override suspend fun find(): GameSettings? =
        dbQuery(db) {
            GameSettingsTable.selectAll().firstOrNull()?.let { GameSettings(it[GameSettingsTable.cookingPoints]) }
        }

    override suspend fun save(
        settings: GameSettings,
        at: Instant,
    ) = dbQuery(db) {
        GameSettingsTable.upsert {
            it[id] = SINGLE_ROW_ID
            it[cookingPoints] = settings.cookingPoints
            it[updatedAt] = at.toEpochMilli()
        }
        Unit
    }

    private companion object {
        const val SINGLE_ROW_ID = 1
    }
}
