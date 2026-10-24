package de.familyplanner.android.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.meals.SharedRecipeInbox
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {
    // Ohne Zustimmung blockiert Android 17+ jede Verbindung zum Pi im Heimnetz (Timeout).
    private val localNetworkPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestLocalNetworkAccess()
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

    private fun requestLocalNetworkAccess() {
        if (Build.VERSION.SDK_INT < LOCAL_NETWORK_MIN_SDK) return
        if (checkSelfPermission(LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED) return
        localNetworkPermission.launch(LOCAL_NETWORK_PERMISSION)
    }

    private companion object {
        const val LOCAL_NETWORK_MIN_SDK = 37
        const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
