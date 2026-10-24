package de.familyplanner.android.feature.tasks.domain.catalog

import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.feature.tasks.domain.board.TaskLimits

/** Prüft wie das Backend (REQ-TASK-010); `null` = gültig. Titel und Symbol werden vor dem Senden getrimmt. */
fun validateCatalogInput(input: CatalogInput): AppError.Validation? =
    when {
        input.title.trim().length !in 1..TaskLimits.TITLE_MAX ->
            AppError.Validation("Titel muss zwischen 1 und ${TaskLimits.TITLE_MAX} Zeichen lang sein")
        (input.emoji?.trim()?.length ?: 0) > TaskLimits.EMOJI_MAX -> AppError.Validation("Symbol ist zu lang")
        input.points !in TaskLimits.POINTS_MIN..TaskLimits.POINTS_MAX ->
            AppError.Validation("Punkte müssen zwischen ${TaskLimits.POINTS_MIN} und ${TaskLimits.POINTS_MAX} liegen")
        else -> null
    }

internal fun CatalogInput.normalized() = CatalogInput(title.trim(), emoji?.trim()?.takeIf { it.isNotEmpty() }, points)
