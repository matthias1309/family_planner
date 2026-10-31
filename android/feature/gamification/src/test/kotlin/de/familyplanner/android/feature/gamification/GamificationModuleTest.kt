package de.familyplanner.android.feature.gamification

import de.familyplanner.android.core.data.network.ServerConfig
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.core.data.network.createRetrofit
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.testing.MainDispatcherRule
import de.familyplanner.android.feature.gamification.domain.PointsCache
import de.familyplanner.android.feature.gamification.fakes.InMemoryPointsCache
import de.familyplanner.android.feature.gamification.fakes.parentSession
import de.familyplanner.android.feature.gamification.ui.account.ChildAccountArgs
import de.familyplanner.android.feature.gamification.ui.account.ChildAccountViewModel
import de.familyplanner.android.feature.gamification.ui.badges.MemberBadgesArgs
import de.familyplanner.android.feature.gamification.ui.badges.MemberBadgesViewModel
import de.familyplanner.android.feature.gamification.ui.family.FamilyPointsViewModel
import de.familyplanner.android.feature.gamification.ui.me.MeViewModel
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import retrofit2.Retrofit
import kotlin.test.assertNotNull

class GamificationModuleTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val platform =
        module {
            single { createJson() }
            single<Retrofit> { createRetrofit(ServerConfig("http://localhost:1/"), OkHttpClient(), get()) }
            single<SessionStore> { InMemorySessionStore(parentSession) }
        }

    /** Ersetzt, was einen Android-`Context` braucht; muss nach `gamificationModule` geladen werden. */
    private val testOverrides = module { single<PointsCache> { InMemoryPointsCache() } }

    @Test
    fun `every view model can be built from the koin graph`() {
        val koin = koinApplication { modules(platform, gamificationModule, testOverrides) }.koin

        assertNotNull(koin.get<MeViewModel>())
        assertNotNull(koin.get<FamilyPointsViewModel>())
        assertNotNull(koin.get<MemberBadgesViewModel> { parametersOf(MemberBadgesArgs("c-2", "Ben")) })
        assertNotNull(koin.get<ChildAccountViewModel> { parametersOf(ChildAccountArgs("c-1", "Lea")) })
    }
}
