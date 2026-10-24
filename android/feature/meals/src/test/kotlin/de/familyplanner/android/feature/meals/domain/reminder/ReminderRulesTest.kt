package de.familyplanner.android.feature.meals.domain.reminder

import de.familyplanner.android.core.ui.BerlinZone
import de.familyplanner.android.feature.meals.domain.cooking.CookState
import de.familyplanner.android.feature.meals.domain.plan.Dish
import de.familyplanner.android.feature.meals.domain.plan.DishStatus
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderRulesTest {
    private val week = LocalDate.of(2026, 10, 19)
    private val deadline = Instant.parse("2026-10-15T14:00:00Z") // Do 16:00 Berlin
    private val tuesday = LocalDate.of(2026, 10, 20)

    private fun berlin(
        y: Int,
        m: Int,
        d: Int,
        h: Int,
    ) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, BerlinZone).toInstant()

    private fun dish(
        status: DishStatus,
        cookId: String = "c-1",
        reason: String? = null,
        id: String = "d-1",
    ) = Dish(id, tuesday, cookId, "Curry", emptyList(), status, reason)

    @Test
    fun `REQ-MEAL-050 reminders come 3, 2 and 1 day before the deadline at deadline time`() {
        val times = reminderTimes(week, deadline, berlin(2026, 10, 10, 12)).map { it.at }

        assertEquals(listOf(berlin(2026, 10, 12, 16), berlin(2026, 10, 13, 16), berlin(2026, 10, 14, 16)), times)
    }

    @Test
    fun `REQ-MEAL-050 reminders already past are skipped`() {
        val times = reminderTimes(week, deadline, berlin(2026, 10, 13, 17)).map { it.at }

        assertEquals(listOf(berlin(2026, 10, 14, 16)), times)
    }

    @Test
    fun `REQ-MEAL-050 reminders keep the wall-clock time across the DST change`() {
        val dstDeadline = berlin(2026, 10, 27, 16)
        val times = reminderTimes(week, dstDeadline, berlin(2026, 10, 20, 12)).map { it.at.atZone(BerlinZone) }

        assertEquals(listOf(24, 25, 26), times.map { it.dayOfMonth })
        assertTrue(times.all { it.hour == 16 })
    }

    @Test
    fun `REQ-MEAL-050 only open or rejected cooking needs reminders`() {
        val needing = (CookState.entries + null).filter { needsReminder(it) }

        assertEquals(listOf(CookState.NOT_SUBMITTED, CookState.REJECTED), needing)
    }

    @Test
    fun `REQ-MEAL-052 first sync of a week only records a baseline`() {
        assertEquals(emptyList(), statusNotices(false, "c-1", null, listOf(dish(DishStatus.REJECTED, reason = "x")), emptyMap()))
    }

    @Test
    fun `REQ-MEAL-052 own rejected dish is announced with reason`() {
        val previous = mapOf("d-1" to KnownDish(DishStatus.SUBMITTED, null))

        val notices = statusNotices(false, "c-1", previous, listOf(dish(DishStatus.REJECTED, reason = "kein Gemüse")), emptyMap())

        assertEquals(listOf<StatusNotice>(StatusNotice.Rejected("d-1", "Curry", tuesday, "kein Gemüse")), notices)
    }

    @Test
    fun `REQ-MEAL-052 own approved dish is announced once`() {
        val approved = dish(DishStatus.APPROVED)
        val first = statusNotices(false, "c-1", mapOf("d-1" to KnownDish(DishStatus.SUBMITTED, null)), listOf(approved), emptyMap())
        val second = statusNotices(false, "c-1", mapOf("d-1" to approved.known()), listOf(approved), emptyMap())

        assertEquals(listOf<StatusNotice>(StatusNotice.Approved("d-1", "Curry", tuesday)), first)
        assertEquals(emptyList(), second)
    }

    @Test
    fun `REQ-MEAL-052 new rejection reason is announced again`() {
        val previous = mapOf("d-1" to KnownDish(DishStatus.REJECTED, "a"))

        val notices = statusNotices(false, "c-1", previous, listOf(dish(DishStatus.REJECTED, reason = "b")), emptyMap())

        assertEquals(listOf<StatusNotice>(StatusNotice.Rejected("d-1", "Curry", tuesday, "b")), notices)
    }

    @Test
    fun `REQ-MEAL-052 dishes of other cooks are ignored for a child`() {
        assertEquals(emptyList(), statusNotices(false, "c-1", emptyMap(), listOf(dish(DishStatus.APPROVED, cookId = "p-1")), emptyMap()))
    }

    @Test
    fun `REQ-MEAL-053 newly submitted dish is announced to parents with cook name`() {
        val notices = statusNotices(true, "p-1", emptyMap(), listOf(dish(DishStatus.SUBMITTED)), mapOf("c-1" to "Ben"))

        assertEquals(listOf<StatusNotice>(StatusNotice.Submitted("d-1", "Curry", tuesday, "Ben")), notices)
    }

    @Test
    fun `REQ-MEAL-053 resubmission after approval is announced again`() {
        val previous = mapOf("d-1" to KnownDish(DishStatus.APPROVED, null))

        assertEquals(1, statusNotices(true, "p-1", previous, listOf(dish(DishStatus.SUBMITTED)), mapOf("c-1" to "Ben")).size)
    }

    @Test
    fun `REQ-MEAL-053 still submitted dish is not announced twice and approved parent dishes never notify`() {
        val still =
            statusNotices(
                true,
                "p-1",
                mapOf("d-1" to KnownDish(DishStatus.SUBMITTED, null)),
                listOf(dish(DishStatus.SUBMITTED)),
                emptyMap(),
            )
        val parentDish = statusNotices(true, "p-1", emptyMap(), listOf(dish(DishStatus.APPROVED, cookId = "p-1")), emptyMap())

        assertFalse(still.isNotEmpty() || parentDish.isNotEmpty())
    }

    @Test
    fun `REQ-MEAL-053 unknown cook name falls back to a question mark`() {
        val notice = statusNotices(true, "p-1", emptyMap(), listOf(dish(DishStatus.SUBMITTED, cookId = "gone")), emptyMap()).single()

        assertEquals("?", (notice as StatusNotice.Submitted).cookName)
    }
}
