package de.familyplanner.android.app

import de.familyplanner.android.BuildConfig
import de.familyplanner.android.core.data.network.ServerConfig
import org.koin.dsl.module

val appModule =
    module {
        single { ServerConfig(BuildConfig.FP_SERVER_URL) }
    }
