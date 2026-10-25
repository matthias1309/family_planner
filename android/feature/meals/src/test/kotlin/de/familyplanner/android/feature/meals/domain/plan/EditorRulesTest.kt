package de.familyplanner.android.feature.meals.domain.plan

import de.familyplanner.android.feature.meals.fakes.childDish
import de.familyplanner.android.feature.meals.fakes.planWeek
import de.familyplanner.android.feature.meals.fakes.someDish
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditorRulesTest {
    private val tuesday = planWeek.plusDays(1)
    private val wednesday = planWeek.plusDays(2)
    private val thursday = planWeek.plusDays(3)
    private val ben = CookCandidate("c-1", "Ben", setOf(tuesday, wednesday, thursday), null)

    @Test
    fun `REQ-MEAL-021 child cook allows only present free days`() {
        assertEquals(setOf(tuesday, thursday), allowedDates(planWeek, setOf(wednesday), ben))
    }

    @Test
    fun `REQ-MEAL-032 parent cook allows all free days`() {
        val allowed = allowedDates(planWeek, setOf(wednesday), null)

        assertEquals(6, allowed.size)
        assertEquals(false, wednesday in allowed)
    }

    @Test
    fun `REQ-MEAL-021 tapped absent day preselects first allowed day`() {
        assertEquals(tuesday, preselectDate(planWeek, setOf(tuesday, thursday)))
        assertEquals(thursday, preselectDate(thursday, setOf(tuesday, thursday)))
    }

    @Test
    fun `REQ-MEAL-021 no allowed day preselects nothing`() {
        assertNull(preselectDate(planWeek, emptySet<LocalDate>()))
    }

    @Test
    fun `REQ-MEAL-021 invalid child dish opens move mode for parents`() {
        assertEquals(EditorMode.PARENT_MOVE, editorMode(true, childDish.copy(dayValid = false, status = DishStatus.APPROVED), true))
    }

    @Test
    fun `REQ-MEAL-030 submitted child dish opens review mode`() {
        assertEquals(EditorMode.PARENT_REVIEW, editorMode(true, childDish, true))
    }

    @Test
    fun `REQ-MEAL-021 invalid day wins over submitted`() {
        assertEquals(EditorMode.PARENT_MOVE, editorMode(true, childDish.copy(dayValid = false), true))
    }

    @Test
    fun `REQ-MEAL-030 rejected child dish opens edit mode`() {
        assertEquals(EditorMode.PARENT_EDIT, editorMode(true, childDish.copy(status = DishStatus.REJECTED), true))
    }

    @Test
    fun `REQ-MEAL-020 child always gets child mode`() {
        assertEquals(EditorMode.CHILD, editorMode(false, childDish, true))
        assertEquals(EditorMode.CHILD, editorMode(false, null, true))
    }

    @Test
    fun `REQ-MEAL-032 parent dish opens edit mode`() {
        assertEquals(EditorMode.PARENT_EDIT, editorMode(true, someDish, false))
        assertEquals(EditorMode.PARENT_EDIT, editorMode(true, null, false))
    }

    private val approved = childDish.copy(status = DishStatus.APPROVED, date = tuesday)

    @Test
    fun `REQ-MEAL-034 parent can confirm an approved child dish on or after the cooking day`() {
        assertEquals(true, canConfirmCooking(true, approved, true, tuesday))
        assertEquals(true, canConfirmCooking(true, approved, true, wednesday))
        assertEquals(false, canConfirmCooking(true, approved, true, planWeek))
    }

    @Test
    fun `REQ-MEAL-034 confirmation is not offered for parents' dishes, pending or cooked dishes, or children`() {
        assertEquals(false, canConfirmCooking(true, approved, false, wednesday))
        assertEquals(false, canConfirmCooking(true, approved.copy(status = DishStatus.SUBMITTED), true, wednesday))
        assertEquals(false, canConfirmCooking(true, approved.copy(cookedAt = Instant.EPOCH), true, wednesday))
        assertEquals(false, canConfirmCooking(false, approved, true, wednesday))
        assertEquals(false, canConfirmCooking(true, null, true, wednesday))
    }
}
