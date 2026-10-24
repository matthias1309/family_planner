package de.familyplanner.android.feature.tasks.domain.catalog

data class CatalogTask(
    val id: String,
    val title: String,
    val emoji: String?,
    val points: Int,
)

data class CatalogInput(
    val title: String,
    val emoji: String?,
    val points: Int,
)
