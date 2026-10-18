package de.familyplanner.android.feature.meals

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedRecipeInboxTest {
    private val inbox = SharedRecipeInbox()

    @Test
    fun `REQ-MEAL-061 offered text is pending until consumed`() {
        inbox.offer("x")
        assertEquals("x", inbox.pending.value)
        inbox.consume()
        assertNull(inbox.pending.value)
    }

    @Test
    fun `REQ-MEAL-061 empty or blank text is ignored`() {
        inbox.offer("x")
        inbox.offer(null)
        inbox.offer("  ")
        assertEquals("x", inbox.pending.value)
    }

    @Test
    fun `REQ-MEAL-061 very long text is shortened`() {
        inbox.offer("a".repeat(5_000))
        assertEquals(2_000, inbox.pending.value?.length)
    }
}
