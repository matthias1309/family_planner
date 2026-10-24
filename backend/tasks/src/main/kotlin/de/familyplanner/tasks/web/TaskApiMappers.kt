package de.familyplanner.tasks.web

import de.familyplanner.core.auth.DevicePrincipal
import de.familyplanner.tasks.board.Actor
import de.familyplanner.tasks.board.Lane
import de.familyplanner.tasks.board.TaskCard
import de.familyplanner.tasks.catalog.CatalogTask
import de.familyplanner.api.model.CatalogTask as ApiCatalogTask
import de.familyplanner.api.model.TaskCard as ApiTaskCard
import de.familyplanner.api.model.TaskLane as ApiTaskLane
import de.familyplanner.api.model.TaskStatus as ApiTaskStatus

fun DevicePrincipal.toActor() = Actor(memberId, role)

fun CatalogTask.toApi() = ApiCatalogTask(id = id.value, title = title, emoji = emoji, points = points, archived = archived)

fun TaskCard.toApi() =
    ApiTaskCard(
        id = id.value,
        memberId = memberId.value,
        catalogTaskId = catalogTaskId?.value,
        title = title,
        emoji = emoji,
        points = points,
        status = ApiTaskStatus.valueOf(status.name),
        rejectionReason = rejectionReason,
        assignedAt = assignedAt.toString(),
        reportedAt = reportedAt?.toString(),
        doneAt = doneAt?.toString(),
    )

fun Lane.toApi() = ApiTaskLane(memberId = member.id.value, name = member.name, cards = cards.map { it.toApi() })
