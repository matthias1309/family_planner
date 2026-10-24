package de.familyplanner.android.feature.tasks.ui

import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.android.feature.tasks.fakes.InMemoryBoardCache
import de.familyplanner.android.feature.tasks.fakes.card
import de.familyplanner.android.feature.tasks.fakes.childSession
import de.familyplanner.android.feature.tasks.fakes.lane
import de.familyplanner.android.feature.tasks.fakes.parentSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewBadgeTest {
    private val cache = InMemoryBoardCache()
    private val lanes =
        arrayOf(
            lane("c-1", "Ben", card("a", status = TaskStatus.REVIEW), card("b", status = TaskStatus.REVIEW), card("c")),
        )

    @Test
    fun `REQ-TASK-003 badge counts review cards for parents only`() =
        runTest {
            cache.seed("p-1", *lanes)

            assertEquals(2, ReviewBadge(cache, InMemorySessionStore(parentSession)).count.first())
            cache.seed("c-1", *lanes)
            assertEquals(0, ReviewBadge(cache, InMemorySessionStore(childSession)).count.first())
        }

    @Test
    fun `REQ-TASK-003 badge ignores a board cached for another member`() =
        runTest {
            cache.seed("p-9", *lanes)

            assertEquals(0, ReviewBadge(cache, InMemorySessionStore(parentSession)).count.first())
        }

    @Test
    fun `REQ-TASK-003 badge is zero without a stored board`() =
        runTest {
            assertEquals(0, ReviewBadge(cache, InMemorySessionStore(parentSession)).count.first())
        }
}
