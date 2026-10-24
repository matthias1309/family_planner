package de.familyplanner.android.app

import de.familyplanner.android.app.TopLevelDestination.FAMILY
import de.familyplanner.android.app.TopLevelDestination.ME
import de.familyplanner.android.app.TopLevelDestination.MEALS
import de.familyplanner.android.app.TopLevelDestination.TASKS
import de.familyplanner.android.app.TopLevelDestination.TODAY
import de.familyplanner.android.core.data.session.Role
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MainDestinationsTest {
    @Test
    fun `REQ-TASK-001 child gets today, meals, tasks and me`() {
        assertEquals(listOf(TODAY, MEALS, TASKS, ME), topLevelDestinations(Role.CHILD))
    }

    @Test
    fun `REQ-TASK-001 parent gets family instead of me`() {
        assertEquals(listOf(TODAY, MEALS, TASKS, FAMILY), topLevelDestinations(Role.PARENT))
    }

    @Test
    fun `REQ-TASK-001 no destination belongs to a main area`() {
        val destination: androidx.navigation.NavDestination? = null

        assertNull(destination.topLevelOf(topLevelDestinations(Role.CHILD)))
        assertFalse(destination.showsNavigationBar(topLevelDestinations(Role.CHILD)))
    }
}
