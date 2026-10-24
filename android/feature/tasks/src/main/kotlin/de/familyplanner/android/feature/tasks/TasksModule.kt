package de.familyplanner.android.feature.tasks

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import de.familyplanner.android.feature.tasks.data.AndroidTaskNotifier
import de.familyplanner.android.feature.tasks.data.BoardCacheSerializer
import de.familyplanner.android.feature.tasks.data.DataStoreBoardCache
import de.familyplanner.android.feature.tasks.data.RemoteCatalogRepository
import de.familyplanner.android.feature.tasks.data.RemoteTaskRepository
import de.familyplanner.android.feature.tasks.data.WorkManagerTaskSync
import de.familyplanner.android.feature.tasks.domain.board.AssignTasksUseCase
import de.familyplanner.android.feature.tasks.domain.board.BoardCache
import de.familyplanner.android.feature.tasks.domain.board.CatalogRepository
import de.familyplanner.android.feature.tasks.domain.board.LoadBoardUseCase
import de.familyplanner.android.feature.tasks.domain.board.LoadHistoryUseCase
import de.familyplanner.android.feature.tasks.domain.board.MoveCardUseCase
import de.familyplanner.android.feature.tasks.domain.board.RemoveCardUseCase
import de.familyplanner.android.feature.tasks.domain.board.TaskNotifier
import de.familyplanner.android.feature.tasks.domain.board.TaskRepository
import de.familyplanner.android.feature.tasks.domain.board.TaskSync
import de.familyplanner.android.feature.tasks.domain.catalog.ArchiveCatalogTaskUseCase
import de.familyplanner.android.feature.tasks.domain.catalog.CatalogUseCases
import de.familyplanner.android.feature.tasks.domain.catalog.LoadCatalogUseCase
import de.familyplanner.android.feature.tasks.domain.catalog.SaveCatalogTaskUseCase
import de.familyplanner.android.feature.tasks.domain.notice.SyncTaskNoticesUseCase
import de.familyplanner.android.feature.tasks.ui.ReviewBadge
import de.familyplanner.android.feature.tasks.ui.assign.AssignViewModel
import de.familyplanner.android.feature.tasks.ui.board.BoardViewModel
import de.familyplanner.android.feature.tasks.ui.catalog.CatalogEditViewModel
import de.familyplanner.android.feature.tasks.ui.catalog.CatalogViewModel
import de.familyplanner.android.feature.tasks.ui.history.HistoryViewModel
import de.familyplanner.android.feature.tasks.ui.today.TodayTasksViewModel
import de.familyplanner.api.client.TasksApi
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

private const val BOARD_CACHE_FILE = "task_board.json"

val tasksModule =
    module {
        // Der einzige TasksApi-Eintrag: Aufgaben und Katalog nutzen dasselbe single.
        single { get<Retrofit>().create(TasksApi::class.java) }
        single<TaskRepository> { RemoteTaskRepository(get(), get()) }
        single<CatalogRepository> { RemoteCatalogRepository(get(), get()) }
        // Genau eine DataStore-Instanz je Datei und Prozess, sonst IllegalStateException.
        single<BoardCache> {
            DataStoreBoardCache(
                DataStoreFactory.create(
                    serializer = BoardCacheSerializer(get()),
                    produceFile = { androidContext().dataStoreFile(BOARD_CACHE_FILE) },
                ),
            )
        }

        // board
        factory { LoadBoardUseCase(get(), get(), get()) }
        factory { MoveCardUseCase(get(), get(), get()) }
        factory { RemoveCardUseCase(get(), get(), get()) }
        factory { AssignTasksUseCase(get()) }
        factory { LoadHistoryUseCase(get()) }
        viewModel { BoardViewModel(get(), get(), get(), get()) }
        viewModel { params -> AssignViewModel(params.get(), get(), get(), get()) }
        viewModel { params -> HistoryViewModel(params.get(), get()) }
        viewModel { TodayTasksViewModel(get(), get()) }

        // catalog
        factory { LoadCatalogUseCase(get()) }
        factory { CatalogUseCases(LoadCatalogUseCase(get()), SaveCatalogTaskUseCase(get()), ArchiveCatalogTaskUseCase(get())) }
        viewModel { CatalogViewModel(get()) }
        viewModel { params -> CatalogEditViewModel(params.get(), get()) }

        // notice
        single<TaskNotifier> { AndroidTaskNotifier(androidContext()) }
        single<TaskSync> { WorkManagerTaskSync(androidContext()) }
        // single wegen des Mutex: periodischer und sofortiger Abgleich dürfen nicht gleichzeitig laufen.
        single { SyncTaskNoticesUseCase(get(), get(), get(), get()) }
        single { TaskOpenRequests() }
        single { ReviewBadge(get(), get()) }
    }
