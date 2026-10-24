package de.familyplanner.android.app

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.familyplanner.android.R

@Composable
fun AppNavigationBar(
    destinations: List<TopLevelDestination>,
    selected: TopLevelDestination,
    badges: Map<TopLevelDestination, Int> = emptyMap(),
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        destinations.forEach { destination ->
            NavigationBarItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                icon = { DestinationIcon(destination, badges[destination] ?: 0) },
                label = { Text(stringResource(destination.label)) },
            )
        }
    }
}

/** Symbol mit Zahl (z. B. Karten zur Abnahme); bei 0 ohne Badge. */
@Composable
private fun DestinationIcon(
    destination: TopLevelDestination,
    badge: Int,
) {
    if (badge <= 0) {
        Icon(destination.icon, contentDescription = null)
        return
    }
    val description = stringResource(R.string.nav_badge_review, badge)
    BadgedBox(
        badge = { Badge { Text(badge.toString()) } },
        modifier = Modifier.semantics { contentDescription = description },
    ) { Icon(destination.icon, contentDescription = null) }
}
