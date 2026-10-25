package de.familyplanner.gamification.account

import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.Role
import de.familyplanner.gamification.ledger.Totals
import kotlin.test.Test
import kotlin.test.assertEquals

class LeaderboardTest {
    private val anna = aMember(name = "Anna", role = Role.CHILD)
    private val ben = aMember(name = "Ben", role = Role.CHILD)
    private val cleo = aMember(name = "Cleo", role = Role.CHILD)

    @Test
    fun `REQ-GAME-014 leaderboard sorts by collected, ties share a rank`() {
        val totals = mapOf(cleo.id to Totals(4, 4), ben.id to Totals(10, 10), anna.id to Totals(10, 2))

        val board = rankedLeaderboard(listOf(cleo, ben, anna), totals)

        assertEquals(listOf("Anna", "Ben", "Cleo"), board.map { it.name })
        assertEquals(listOf(1, 1, 3), board.map { it.rank })
    }

    @Test
    fun `REQ-GAME-014 child without entries is listed with zero`() {
        val board = rankedLeaderboard(listOf(anna, ben), mapOf(ben.id to Totals(5, 5)))

        assertEquals(listOf("Ben", "Anna"), board.map { it.name })
        assertEquals(listOf(1, 2), board.map { it.rank })
        assertEquals(0, board.last().collected)
    }

    @Test
    fun `REQ-GAME-014 spending does not change the position`() {
        // balance dropped by spending, collected unchanged
        val board = rankedLeaderboard(listOf(anna, ben), mapOf(anna.id to Totals(10, -5), ben.id to Totals(8, 8)))

        assertEquals(listOf("Anna", "Ben"), board.map { it.name })
    }

    @Test
    fun `parents are not ranked`() {
        val parent = aMember(name = "Mama", role = Role.PARENT)

        assertEquals(listOf("Anna"), rankedLeaderboard(listOf(parent, anna), emptyMap()).map { it.name })
    }
}
