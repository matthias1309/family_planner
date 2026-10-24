package de.familyplanner.tasks.catalog

import de.familyplanner.core.error.AppException
import de.familyplanner.core.error.validatedName

const val TASK_TITLE_MAX = 40
const val TASK_EMOJI_MAX = 16
const val TASK_POINTS_MIN = 1
const val TASK_POINTS_MAX = 100

@JvmInline
value class CatalogTaskId(
    val value: String,
)

/** A prepared task ("magnet") that parents assign to children. */
data class CatalogTask(
    val id: CatalogTaskId,
    val title: String,
    val emoji: String?,
    val points: Int,
    val archived: Boolean,
)

/** What a caller wants to store; validated by [validated]. */
data class CatalogTaskInput(
    val title: String,
    val emoji: String?,
    val points: Int,
)

/** Trims the title, turns a blank emoji into `null` and checks all limits. */
fun validated(input: CatalogTaskInput): CatalogTaskInput {
    val title = validatedName(input.title, "Titel", TASK_TITLE_MAX)
    val emoji = input.emoji?.trim()?.takeIf { it.isNotEmpty() }
    if (emoji != null && emoji.length > TASK_EMOJI_MAX) throw AppException.Validation("Symbol ist zu lang")
    if (input.points !in TASK_POINTS_MIN..TASK_POINTS_MAX) {
        throw AppException.Validation("Punkte müssen zwischen $TASK_POINTS_MIN und $TASK_POINTS_MAX liegen")
    }
    return CatalogTaskInput(title, emoji, input.points)
}
