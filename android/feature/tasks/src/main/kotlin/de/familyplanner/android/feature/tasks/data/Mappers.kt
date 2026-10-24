package de.familyplanner.android.feature.tasks.data

import de.familyplanner.android.feature.tasks.domain.board.TaskCard
import de.familyplanner.android.feature.tasks.domain.board.TaskLane
import de.familyplanner.android.feature.tasks.domain.board.TaskStatus
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogInput
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogTask
import de.familyplanner.api.model.CatalogTaskInput
import de.familyplanner.api.model.CatalogTask as ApiCatalogTask
import de.familyplanner.api.model.TaskCard as ApiTaskCard
import de.familyplanner.api.model.TaskLane as ApiTaskLane

/** `null`, wenn ein Zeitpunkt oder Status unlesbar ist (Serverfehler statt halber Karte). */
internal fun ApiTaskCard.toDomain(): TaskCard? {
    val assigned = parseInstant(assignedAt)
    val reported = reportedAt?.let { parseInstant(it) }
    val done = doneAt?.let { parseInstant(it) }
    val mapped = TaskStatus.entries.firstOrNull { it.name == status.name }
    // Ein vorhandener, aber unlesbarer Zeitpunkt ergibt null und damit einen Fehler, ein fehlender bleibt null.
    val unreadable = assigned == null || mapped == null || (reportedAt != null && reported == null) || (doneAt != null && done == null)
    return if (unreadable) null else TaskCard(id, memberId, title, emoji, points, mapped, rejectionReason, assigned, reported, done)
}

internal fun ApiTaskLane.toDomain(): TaskLane? {
    val mapped = cards.mapAllOrNull { it.toDomain() } ?: return null
    return TaskLane(memberId, name, mapped)
}

internal fun ApiCatalogTask.toDomain() = CatalogTask(id, title, emoji, points)

internal fun CatalogInput.toApi() = CatalogTaskInput(title = title, points = points, emoji = emoji)
