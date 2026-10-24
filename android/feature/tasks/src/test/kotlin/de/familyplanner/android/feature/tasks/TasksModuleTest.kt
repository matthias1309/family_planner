package de.familyplanner.android.feature.tasks

import de.familyplanner.android.core.data.network.ServerConfig
import de.familyplanner.android.core.data.network.createJson
import de.familyplanner.android.core.data.network.createRetrofit
import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.testing.MainDispatcherRule
import de.familyplanner.android.feature.tasks.domain.board.BoardCache
import de.familyplanner.android.feature.tasks.domain.board.TaskNotifier
import de.familyplanner.android.feature.tasks.domain.board.TaskSync
import de.familyplanner.android.feature.tasks.domain.notice.SyncTaskNoticesUseCase
import de.familyplanner.android.feature.tasks.fakes.FakeTaskNotifier
import de.familyplanner.android.feature.tasks.fakes.FakeTaskSync
import de.familyplanner.android.feature.tasks.fakes.InMemoryBoardCache
import de.familyplanner.android.feature.tasks.fakes.parentSession
import de.familyplanner.android.feature.tasks.ui.ReviewBadge
import de.familyplanner.android.feature.tasks.ui.assign.AssignArgs
import de.familyplanner.android.feature.tasks.ui.assign.AssignViewModel
import de.familyplanner.android.feature.tasks.ui.board.BoardViewModel
import de.familyplanner.android.feature.tasks.ui.catalog.CatalogEditArgs
import de.familyplanner.android.feature.tasks.ui.catalog.CatalogEditViewModel
import de.familyplanner.android.feature.tasks.ui.catalog.CatalogViewModel
import de.familyplanner.android.feature.tasks.ui.history.HistoryArgs
import de.familyplanner.android.feature.tasks.ui.history.HistoryViewModel
import de.familyplanner.android.feature.tasks.ui.today.TodayTasksViewModel
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import retrofit2.Retrofit
import kotlin.test.assertNotNull

class TasksModuleTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    /** Ersatz für das, was `networkModule`/`sessionModule` in der App bereitstellen. */
    private val platform =
        module {
            single { createJson() }
            single<Retrofit> { createRetrofit(ServerConfig("http://localhost:1/"), OkHttpClient(), get()) }
            single<SessionStore> { InMemorySessionStore(parentSession) }
        }

    /** Ersetzt, was einen Android-`Context` braucht; muss nach `tasksModule` geladen werden. */
    private val testOverrides =
        module {
            single<BoardCache> { InMemoryBoardCache() }
            single<TaskNotifier> { FakeTaskNotifier() }
            single<TaskSync> { FakeTaskSync() }
        }

    @Test
    fun `every view model can be built from the koin graph`() {
        val koin = koinApplication { modules(platform, tasksModule, testOverrides) }.koin

        assertNotNull(koin.get<BoardViewModel>())
        assertNotNull(koin.get<AssignViewModel> { parametersOf(AssignArgs("c-1")) })
        assertNotNull(koin.get<CatalogViewModel>())
        assertNotNull(koin.get<CatalogEditViewModel> { parametersOf(CatalogEditArgs()) })
        assertNotNull(koin.get<HistoryViewModel> { parametersOf(HistoryArgs("c-1", "Ben")) })
        assertNotNull(koin.get<TodayTasksViewModel>())
        assertNotNull(koin.get<TaskOpenRequests>())
        assertNotNull(koin.get<ReviewBadge>())
        assertNotNull(koin.get<SyncTaskNoticesUseCase>())
    }
}
