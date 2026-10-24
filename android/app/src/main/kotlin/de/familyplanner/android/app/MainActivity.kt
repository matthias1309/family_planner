package de.familyplanner.android.app

import android.Manifest
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
import de.familyplanner.android.feature.meals.PlanOpenRequests
import de.familyplanner.android.feature.meals.SharedRecipeInbox
import de.familyplanner.android.feature.meals.domain.reminder.ReminderSync
import de.familyplanner.android.feature.tasks.TaskOpenRequests
import de.familyplanner.android.feature.tasks.domain.board.TaskSync
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {
    // Heimnetz: ohne Zustimmung blockiert Android 17+ jede Verbindung zum Pi (Timeout). Benachrichtigungen: ab Android 13.
    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions()
        if (savedInstanceState == null) {
            receiveShare(intent)
            receiveOpenPlan(intent)
            receiveOpenTasks(intent)
        }
        setContent {
            FamilyPlannerTheme {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    AppNavHost()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        get<ReminderSync>().requestNow()
        get<TaskSync>().requestNow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveOpenPlan(intent)
        receiveOpenTasks(intent)
    }

    // ADR-0013: Tippen auf eine Erinnerung oder Statusmeldung öffnet den Essensplan.
    private fun receiveOpenPlan(intent: Intent?) {
        if (intent?.getBooleanExtra(PlanOpenRequests.EXTRA_OPEN_PLAN, false) == true) get<PlanOpenRequests>().request()
    }

    // ADR-0013: Tippen auf einen Aufgaben-Hinweis öffnet das Board.
    private fun receiveOpenTasks(intent: Intent?) {
        if (intent?.getBooleanExtra(TaskOpenRequests.EXTRA_OPEN_TASKS, false) == true) get<TaskOpenRequests>().request()
    }

    // ADR-0012: Rezept-Link aus Browser/REWE-App; der angemeldete Hauptbereich holt ihn aus der Inbox.
    private fun receiveShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            get<SharedRecipeInbox>().offer(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }

    private fun requestPermissions() {
        val missing =
            listOfNotNull(
                LOCAL_NETWORK_PERMISSION.takeIf { Build.VERSION.SDK_INT >= LOCAL_NETWORK_MIN_SDK },
                Manifest.permission.POST_NOTIFICATIONS.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU },
            ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
    }

    private companion object {
        const val LOCAL_NETWORK_MIN_SDK = 37
        const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
