package de.familyplanner.gamification.settings

import de.familyplanner.core.error.AppException
import de.familyplanner.gamification.fakes.GamificationFixture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GameSettingsServiceTest {
    private val service = GamificationFixture().settings

    @Test
    fun `REQ-GAME-011 cooking points default to 3 and must be 1 to 20`() =
        runTest {
            assertEquals(3, service.current().cookingPoints)
            assertFailsWith<AppException.Validation> { service.update(0) }
            assertFailsWith<AppException.Validation> { service.update(21) }
            service.update(1)
            service.update(20)
            service.update(5)
            assertEquals(5, service.current().cookingPoints)
        }
}
