package de.familyplanner.android.feature.meals.domain.plan

import java.time.LocalDate

private const val DAYS_PER_WEEK = 7L

/** Was der Gericht-Screen erlaubt. */
enum class EditorMode {
    /** Elternteil plant/ändert (Elterngericht oder Kindergericht, gespeichert = freigegeben). */
    PARENT_EDIT,

    /** Elternteil, Kindergericht wartet auf Freigabe: Freigeben/Ablehnen + Ändern/Löschen (REQ-MEAL-030). */
    PARENT_REVIEW,

    /** Elternteil, Kindergericht mit ungültigem Tag: nur verschieben oder löschen (REQ-MEAL-021). */
    PARENT_MOVE,

    /** Kind reicht eigenes Gericht ein / ändert es (REQ-MEAL-020/022). */
    CHILD,
}

fun editorMode(
    isParent: Boolean,
    existing: Dish?,
    cookIsChild: Boolean,
): EditorMode =
    when {
        !isParent -> EditorMode.CHILD
        existing != null && cookIsChild && !existing.dayValid -> EditorMode.PARENT_MOVE
        existing != null && cookIsChild && existing.status == DishStatus.SUBMITTED -> EditorMode.PARENT_REVIEW
        else -> EditorMode.PARENT_EDIT
    }

/** Wählbare Tage: Kind → nur Anwesenheitstage; immer ohne Tage mit einem anderen Gericht. */
fun allowedDates(
    weekStart: LocalDate,
    takenDates: Set<LocalDate>,
    childCook: CookCandidate?,
): Set<LocalDate> {
    val week = (0L until DAYS_PER_WEEK).map { weekStart.plusDays(it) }
    val base = childCook?.let { child -> week.filter { it in child.presentDays } } ?: week
    return base.filterNot { it in takenDates }.toSet()
}

/** Vorauswahl beim Öffnen: angetippter Tag, wenn erlaubt, sonst der erste erlaubte, sonst `null`. */
fun preselectDate(
    tapped: LocalDate,
    allowed: Set<LocalDate>,
): LocalDate? = tapped.takeIf { it in allowed } ?: allowed.minOrNull()
