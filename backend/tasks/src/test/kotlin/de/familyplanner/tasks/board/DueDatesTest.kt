package de.familyplanner.tasks.board

import de.familyplanner.core.error.AppException
import de.familyplanner.core.member.MemberId
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DueDatesTest {
    private val now = Instant.parse("2026-10-24T10:00:00Z")

    private fun card(
        dueAt: Instant?,
        reportedAt: Instant? = null,
        doneAt: Instant? = null,
    ) = TaskCard(
        TaskCardId("c"),
        MemberId("m"),
        null,
        "Bad putzen",
        null,
        2,
        TaskStatus.TODO,
        null,
        now,
        reportedAt,
        doneAt,
        dueAt,
    )

    @Test
    fun `REQ-TASK-026 no due date stays none`() {
        assertNull(validatedDueAt(null, now))
    }

    @Test
    fun `REQ-TASK-026 due date must lie strictly in the future`() {
        assertFailsWith<AppException.Validation> { validatedDueAt(now.minusSeconds(60), now) }
        assertFailsWith<AppException.Validation> { validatedDueAt(now, now) }
        assertEquals(now.plusMillis(1), validatedDueAt(now.plusMillis(1), now))
    }

    @Test
    fun `REQ-TASK-026 due date is at most 365 days ahead`() {
        val limit = now.plus(Duration.ofDays(DUE_DATE_MAX_DAYS))

        assertEquals(limit, validatedDueAt(limit, now))
        assertFailsWith<AppException.Validation> { validatedDueAt(limit.plusMillis(1), now) }
    }

    @Test
    fun `REQ-TASK-027 report after the due date is late`() {
        assertTrue(card(dueAt = now, reportedAt = now.plusSeconds(1)).isReportedLate())
    }

    @Test
    fun `REQ-TASK-027 report on or before the due date is not late`() {
        assertFalse(card(dueAt = now, reportedAt = now).isReportedLate())
        assertFalse(card(dueAt = now, reportedAt = now.minusSeconds(1)).isReportedLate())
    }

    @Test
    fun `REQ-TASK-027 directly accepted card counts with its done time`() {
        assertTrue(card(dueAt = now, doneAt = now.plusSeconds(1)).isReportedLate())
        assertFalse(card(dueAt = now, doneAt = now.minusSeconds(1)).isReportedLate())
    }

    @Test
    fun `REQ-TASK-027 card without due date or report is never late`() {
        assertFalse(card(dueAt = null, reportedAt = now.plusSeconds(1)).isReportedLate())
        assertFalse(card(dueAt = now).isReportedLate())
    }
}
