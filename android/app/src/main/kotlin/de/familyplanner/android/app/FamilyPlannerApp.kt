package de.familyplanner.android.app

import android.app.Application
import de.familyplanner.android.core.data.network.networkModule
import de.familyplanner.android.core.data.network.sessionModule
import de.familyplanner.android.feature.auth.authModule
import de.familyplanner.android.feature.meals.domain.reminder.ReminderSync
import de.familyplanner.android.feature.meals.mealsModule
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class FamilyPlannerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FamilyPlannerApp)
            modules(appModule, sessionModule, networkModule, authModule, mealsModule)
        }
        get<ReminderSync>().schedulePeriodic()
    }
}
