package de.familyplanner.android.feature.tasks.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode

internal fun ComposeContentTestRule.onAllNodesWithTextFirst(
    text: String,
    byDescription: Boolean = false,
): SemanticsNodeInteraction = onAllNodes(if (byDescription) hasContentDescription(text) else hasText(text)).onFirst()

internal fun ComposeContentTestRule.onAllNodesWithTextLast(text: String): SemanticsNodeInteraction = onAllNodes(hasText(text)).onLast()

/** Scrollt die Liste bis zum Text (Lazy-Einträge außerhalb des Bildschirms gibt es sonst nicht) und liefert ihn. */
internal fun ComposeContentTestRule.scrollTo(text: String): SemanticsNodeInteraction {
    onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
    return onNodeWithText(text)
}
