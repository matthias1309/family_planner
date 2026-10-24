package de.familyplanner.tasks.web

import de.familyplanner.api.model.AssignTasksRequest
import de.familyplanner.api.model.CatalogTaskListResponse
import de.familyplanner.api.model.CatalogTaskResponse
import de.familyplanner.api.model.RejectTaskRequest
import de.familyplanner.api.model.TaskBoard
import de.familyplanner.api.model.TaskBoardResponse
import de.familyplanner.api.model.TaskCardListResponse
import de.familyplanner.api.model.TaskCardResponse
import de.familyplanner.core.auth.AUTH_DEVICE
import de.familyplanner.core.auth.principalOrThrow
import de.familyplanner.core.auth.requireParent
import de.familyplanner.core.auth.uuidParameter
import de.familyplanner.core.error.AppException
import de.familyplanner.core.member.MemberId
import de.familyplanner.tasks.board.BoardService
import de.familyplanner.tasks.board.HISTORY_LIMIT_DEFAULT
import de.familyplanner.tasks.board.TaskCard
import de.familyplanner.tasks.board.TaskCardId
import de.familyplanner.tasks.catalog.CatalogService
import de.familyplanner.tasks.catalog.CatalogTaskId
import de.familyplanner.tasks.catalog.CatalogTaskInput
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import de.familyplanner.api.model.CatalogTaskInput as ApiCatalogTaskInput

fun Route.taskRoutes(
    catalog: CatalogService,
    board: BoardService,
) {
    authenticate(AUTH_DEVICE) {
        catalogRoutes(catalog)
        boardRoutes(board)
        transitionRoutes(board)
    }
}

private fun Route.catalogRoutes(catalog: CatalogService) {
    get("/api/v1/tasks/catalog") {
        call.requireParent()
        val tasks = catalog.list(call.includeArchived())
        call.respond(CatalogTaskListResponse(success = true, data = tasks.map { it.toApi() }))
    }
    post("/api/v1/tasks/catalog") {
        call.requireParent()
        val task = catalog.create(call.receive<ApiCatalogTaskInput>().toInput())
        call.respond(HttpStatusCode.Created, CatalogTaskResponse(success = true, data = task.toApi()))
    }
    put("/api/v1/tasks/catalog/{catalogTaskId}") {
        call.requireParent()
        val id = CatalogTaskId(call.uuidParameter("catalogTaskId"))
        val task = catalog.update(id, call.receive<ApiCatalogTaskInput>().toInput())
        call.respond(CatalogTaskResponse(success = true, data = task.toApi()))
    }
    delete("/api/v1/tasks/catalog/{catalogTaskId}") {
        call.requireParent()
        catalog.archive(CatalogTaskId(call.uuidParameter("catalogTaskId")))
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun Route.boardRoutes(board: BoardService) {
    post("/api/v1/tasks/cards") {
        call.requireParent()
        val request = call.receive<AssignTasksRequest>()
        val ids = request.catalogTaskIds.map { CatalogTaskId(it) }
        val cards = board.assign(MemberId(request.memberId), ids)
        call.respond(HttpStatusCode.Created, TaskCardListResponse(success = true, data = cards.map { it.toApi() }))
    }
    get("/api/v1/tasks/board") {
        call.principalOrThrow()
        val lanes = board.board().map { it.toApi() }
        call.respond(TaskBoardResponse(success = true, data = TaskBoard(lanes = lanes)))
    }
    get("/api/v1/tasks/members/{memberId}/history") {
        call.principalOrThrow()
        val cards = board.history(MemberId(call.uuidParameter("memberId")), call.historyLimit())
        call.respond(TaskCardListResponse(success = true, data = cards.map { it.toApi() }))
    }
    delete("/api/v1/tasks/cards/{cardId}") {
        call.requireParent()
        board.remove(call.cardId())
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun Route.transitionRoutes(board: BoardService) {
    post("/api/v1/tasks/cards/{cardId}/report") {
        call.respondCard(board.report(call.principalOrThrow().toActor(), call.cardId()))
    }
    post("/api/v1/tasks/cards/{cardId}/withdrawal") {
        call.respondCard(board.withdraw(call.principalOrThrow().toActor(), call.cardId()))
    }
    post("/api/v1/tasks/cards/{cardId}/acceptance") {
        call.respondCard(board.accept(call.principalOrThrow().toActor(), call.cardId()))
    }
    post("/api/v1/tasks/cards/{cardId}/rejection") {
        val actor = call.principalOrThrow().toActor()
        call.respondCard(board.reject(actor, call.cardId(), call.optionalReason()))
    }
}

private suspend fun ApplicationCall.respondCard(card: TaskCard) = respond(TaskCardResponse(success = true, data = card.toApi()))

private fun ApplicationCall.cardId() = TaskCardId(uuidParameter("cardId"))

private fun ApiCatalogTaskInput.toInput() = CatalogTaskInput(title, emoji, points)

/** The rejection body is optional: no JSON content type means no reason. */
private suspend fun ApplicationCall.optionalReason(): String? =
    if (request.contentType().match(ContentType.Application.Json)) receiveNullable<RejectTaskRequest>()?.reason else null

private fun ApplicationCall.includeArchived(): Boolean {
    val raw = request.queryParameters["includeArchived"] ?: return false
    return raw.toBooleanStrictOrNull() ?: throw AppException.Validation("Ungültiger Wert für 'includeArchived'")
}

private fun ApplicationCall.historyLimit(): Int {
    val raw = request.queryParameters["limit"] ?: return HISTORY_LIMIT_DEFAULT
    return raw.toIntOrNull() ?: throw AppException.Validation("Ungültiger Wert für 'limit'")
}
