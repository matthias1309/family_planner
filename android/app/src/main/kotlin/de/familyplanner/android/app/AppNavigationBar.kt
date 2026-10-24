package de.familyplanner.android.app

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun AppNavigationBar(
    destinations: List<TopLevelDestination>,
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        destinations.forEach { destination ->
            NavigationBarItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.label)) },
            )
        }
    }
}
