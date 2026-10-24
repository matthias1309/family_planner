package de.familyplanner.tasks

import de.familyplanner.tasks.board.BoardService
import de.familyplanner.tasks.board.TaskCardRepository
import de.familyplanner.tasks.catalog.CatalogRepository
import de.familyplanner.tasks.catalog.CatalogService
import de.familyplanner.tasks.persistence.ExposedCatalogRepository
import de.familyplanner.tasks.persistence.ExposedTaskCardRepository
import org.koin.dsl.module
import java.time.Clock

/** `Database`, `MemberRepository` and `EventNotifier` come from `coreModule`. */
fun tasksModule(clock: Clock) =
    module {
        single<CatalogRepository> { ExposedCatalogRepository(get()) }
        single<TaskCardRepository> { ExposedTaskCardRepository(get()) }
        single { CatalogService(get(), clock) }
        single { BoardService(get(), get(), get(), get(), clock) }
    }
