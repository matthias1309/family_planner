package de.familyplanner.android.feature.meals

import de.familyplanner.android.core.data.network.ServerConfig
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.core.data.network.createRetrofit
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.testing.MainDispatcherRule
import de.familyplanner.android.feature.meals.domain.plan.PlanCache
import de.familyplanner.android.feature.meals.fakes.InMemoryPlanCache
import de.familyplanner.android.feature.meals.fakes.parentSession
import de.familyplanner.android.feature.meals.ui.dish.DishEditViewModel
import de.familyplanner.android.feature.meals.ui.plan.WeekPlanViewModel
import de.familyplanner.android.feature.meals.ui.profile.ProfileEditViewModel
import de.familyplanner.android.feature.meals.ui.profiles.ProfilesViewModel
import de.familyplanner.android.feature.meals.ui.settings.DeadlineSettingsViewModel
import de.familyplanner.android.feature.meals.ui.week.WeekOverviewViewModel
import de.familyplanner.api.client.MembersApi
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import retrofit2.Retrofit
import java.time.LocalDate
import kotlin.test.assertNotNull

class MealsModuleTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    /** Ersatz für das, was `networkModule`/`sessionModule` in der App bereitstellen. */
    private val platform =
        module {
            single { createJson() }
            single<Retrofit> { createRetrofit(ServerConfig("http://localhost:1/"), OkHttpClient(), get()) }
            single { get<Retrofit>().create(MembersApi::class.java) }
            single<SessionStore> { InMemorySessionStore(parentSession) }
        }

    /** Ersetzt den DataStore-Cache (braucht einen Android-`Context`); muss nach `mealsModule` geladen werden. */
    private val testOverrides = module { single<PlanCache> { InMemoryPlanCache() } }

    @Test
    fun `every view model can be built from the koin graph`() {
        val koin = koinApplication { modules(platform, mealsModule, testOverrides) }.koin

        assertNotNull(koin.get<WeekOverviewViewModel>())
        assertNotNull(koin.get<ProfilesViewModel>())
        assertNotNull(koin.get<ProfileEditViewModel> { parametersOf("c-1") })
        assertNotNull(koin.get<DeadlineSettingsViewModel>())
        assertNotNull(koin.get<WeekPlanViewModel>())
        assertNotNull(koin.get<DishEditViewModel> { parametersOf(LocalDate.of(2026, 10, 12), null) })
        assertNotNull(koin.get<DishEditViewModel> { parametersOf(LocalDate.of(2026, 10, 12), "d-1") })
    }
}
