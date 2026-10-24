package de.familyplanner.android.feature.tasks.domain.notice

sealed interface TaskNotice {
    val cardId: String

    data class Reported(
        override val cardId: String,
        val title: String,
        val childName: String,
    ) : TaskNotice

    data class Assigned(
        override val cardId: String,
        val title: String,
    ) : TaskNotice

    data class Accepted(
        override val cardId: String,
        val title: String,
        val points: Int,
    ) : TaskNotice

    data class Rejected(
        override val cardId: String,
        val title: String,
        val reason: String?,
    ) : TaskNotice
}
