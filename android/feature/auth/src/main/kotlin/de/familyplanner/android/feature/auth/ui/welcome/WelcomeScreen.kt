package de.familyplanner.android.feature.auth.ui.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.familyplanner.android.feature.auth.R

@Composable
fun WelcomeScreen(
    signedOutNotice: Boolean,
    onPair: () -> Unit,
    onSetup: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineLarge)
        if (signedOutNotice) {
            Text(stringResource(R.string.welcome_signed_out_notice), color = MaterialTheme.colorScheme.error)
        }
        Button(onClick = onPair, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.welcome_pair)) }
        TextButton(onClick = onSetup) { Text(stringResource(R.string.welcome_setup)) }
    }
}
