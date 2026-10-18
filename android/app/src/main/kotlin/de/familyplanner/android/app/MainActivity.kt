package de.familyplanner.android.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.meals.SharedRecipeInbox
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) receiveShare(intent)
        setContent {
            FamilyPlannerTheme {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    AppNavHost()
                }
            }
        }
    }

    // ADR-0012: Rezept-Link aus Browser/REWE-App; der angemeldete Hauptbereich holt ihn aus der Inbox.
    private fun receiveShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            get<SharedRecipeInbox>().offer(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }
}
