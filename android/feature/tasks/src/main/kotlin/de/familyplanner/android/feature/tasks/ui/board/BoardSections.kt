package de.familyplanner.android.feature.tasks.ui.board

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.familyplanner.android.feature.tasks.R
import de.familyplanner.android.feature.tasks.domain.board.TaskCard
import de.familyplanner.android.feature.tasks.domain.board.TaskLane

@Composable
private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium)

private fun LazyListScope.cardItems(
    cards: List<TaskCard>,
    emptyText: Int,
    state: BoardUiState,
    onEvent: (BoardEvent) -> Unit,
    childName: (TaskCard) -> String? = { null },
) {
    if (cards.isEmpty()) {
        item { Text(stringResource(emptyText)) }
        return
    }
    items(cards, key = { it.id }) { card ->
        TaskCardItem(
            card = card,
            childName = childName(card),
            actions = state.actionsFor(card),
            enabled = !state.isBusy,
            onAction = { onEvent(BoardEvent.Act(card, it)) },
        )
    }
}

/** Spur eines Kindes: Todo · Zur Abnahme · Erledigt, darunter der Verlauf (REQ-TASK-021, 025). */
internal fun LazyListScope.laneSections(
    lane: TaskLane,
    state: BoardUiState,
    onEvent: (BoardEvent) -> Unit,
    onOpenHistory: (TaskLane) -> Unit,
) {
    val sections = state.sectionsOf(lane)
    item { SectionTitle(stringResource(R.string.tasks_section_todo)) }
    cardItems(sections.todo, R.string.tasks_empty_todo, state, onEvent)
    item { SectionTitle(stringResource(R.string.tasks_section_review)) }
    cardItems(sections.review, R.string.tasks_empty_review, state, onEvent)
    item { SectionTitle(stringResource(R.string.tasks_section_done)) }
    cardItems(sections.done, R.string.tasks_empty_done, state, onEvent)
    item { TextButton(onClick = { onOpenHistory(lane) }) { Text(stringResource(R.string.tasks_history_show)) } }
}

/** Sammelansicht der Eltern: alle gemeldeten Karten, älteste Meldung zuerst (REQ-TASK-021). */
internal fun LazyListScope.reviewSection(
    state: BoardUiState,
    onEvent: (BoardEvent) -> Unit,
) {
    val queue = state.reviewQueue
    val names = queue.associate { (lane, card) -> card.id to lane.name }
    cardItems(queue.map { it.second }, R.string.tasks_empty_review, state, onEvent) { names[it.id] }
}
