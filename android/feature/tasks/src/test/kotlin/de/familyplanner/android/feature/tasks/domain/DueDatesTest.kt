package de.familyplanner.android.feature.tasks.domain

import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.android.feature.tasks.domain.board.dueInstant
import de.familyplanner.android.feature.tasks.domain.board.isOverdue
import de.familyplanner.android.feature.tasks.domain.board.taskReminders
import de.familyplanner.android.feature.tasks.domain.board.validateDueAt
import de.familyplanner.android.feature.tasks.fakes.card
import de.familyplanner.android.feature.tasks.fakes.lane
import de.familyplanner.android.feature.tasks.fakes.noon
import de.familyplanner.android.feature.tasks.ui.due.dateToPickerMillis
import de.familyplanner.android.feature.tasks.ui.due.initialDueTime
import de.familyplanner.android.feature.tasks.ui.due.pickerMillisToDate
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DueDatesTest {
    @Test
    fun `REQ-TASK-027 card is overdue from the due instant on`() {
        assertTrue(card("a", dueAt = noon).isOverdue(noon))
        assertFalse(card("a", dueAt = noon.plusSeconds(1)).isOverdue(noon))
        assertFalse(card("a").isOverdue(noon))
    }

    @Test
    fun `REQ-TASK-027 reported card is never overdue`() {
        assertFalse(card("a", status = TaskStatus.REVIEW, dueAt = noon.minusSeconds(60)).isOverdue(noon))
    }

    @Test
    fun `REQ-TASK-026 due instant uses Berlin time across DST`() {
        assertEquals(Instant.parse("2026-10-25T17:00:00Z"), dueInstant(LocalDate.of(2026, 10, 25), LocalTime.of(18, 0)))
        assertEquals(Instant.parse("2026-10-24T16:00:00Z"), dueInstant(LocalDate.of(2026, 10, 24), LocalTime.of(18, 0)))
    }

    @Test
    fun `REQ-TASK-026 due date must lie in the future`() {
        assertNotNull(validateDueAt(noon.minusSeconds(1), noon))
        assertNotNull(validateDueAt(noon, noon))
        assertNull(validateDueAt(noon.plusSeconds(1), noon))
        assertNull(validateDueAt(null, noon))
    }

    @Test
    fun `REQ-TASK-026 picker date conversion round trips`() {
        val date = LocalDate.of(2026, 11, 1)

        assertEquals(date, pickerMillisToDate(dateToPickerMillis(date)))
    }

    @Test
    fun `REQ-TASK-026 initial time is 18 00 without a due date`() {
        assertEquals(LocalTime.of(18, 0), initialDueTime(null))
        assertEquals(LocalTime.of(9, 30), initialDueTime(Instant.parse("2026-11-01T08:30:00Z")))
    }

    @Test
    fun `REQ-TASK-031 child gets reminder two hours before due`() {
        val lanes = listOf(lane("c-1", "Ben", card("a", dueAt = noon.plus(Duration.ofHours(5)))))

        val reminders = taskReminders(lanes, "c-1", isParent = false, now = noon)

        assertEquals(noon.plus(Duration.ofHours(3)), reminders.single().at)
    }

    @Test
    fun `REQ-TASK-031 no reminder when less than two hours remain`() {
        val lanes = listOf(lane("c-1", "Ben", card("a", dueAt = noon.plus(Duration.ofHours(1)))))

        assertEquals(emptyList(), taskReminders(lanes, "c-1", isParent = false, now = noon))
    }

    @Test
    fun `REQ-TASK-031 parents and other lanes and reported cards get no reminder`() {
        val due = noon.plus(Duration.ofHours(5))
        val lanes =
            listOf(
                lane("c-1", "Ben", card("a", dueAt = due), card("r", status = TaskStatus.REVIEW, dueAt = due)),
                lane("c-2", "Lea", card("b", "c-2", dueAt = due)),
            )

        assertEquals(emptyList(), taskReminders(lanes, "p-1", isParent = true, now = noon))
        assertEquals(listOf("a"), taskReminders(lanes, "c-1", isParent = false, now = noon).map { it.cardId })
    }
}
