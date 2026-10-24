package de.familyplanner.android.feature.tasks.domain.catalog

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.feature.tasks.domain.board.CatalogRepository

class LoadCatalogUseCase(
    private val repository: CatalogRepository,
) {
    suspend operator fun invoke(): ApiResult<List<CatalogTask>> = repository.list()
}

class SaveCatalogTaskUseCase(
    private val repository: CatalogRepository,
) {
    /** [id] `null` = neue Aufgabe. */
    suspend operator fun invoke(
        id: String?,
        input: CatalogInput,
    ): ApiResult<CatalogTask> {
        validateCatalogInput(input)?.let { return ApiResult.Failure(it) }
        val clean = input.normalized()
        return if (id == null) repository.create(clean) else repository.update(id, clean)
    }
}

class ArchiveCatalogTaskUseCase(
    private val repository: CatalogRepository,
) {
    suspend operator fun invoke(id: String): ApiResult<Unit> = repository.archive(id)
}

/** Bündelt die Katalog-Use-Cases (hält die ViewModel-Parameterliste kurz). */
class CatalogUseCases(
    val load: LoadCatalogUseCase,
    val save: SaveCatalogTaskUseCase,
    val archive: ArchiveCatalogTaskUseCase,
)
