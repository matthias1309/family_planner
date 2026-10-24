package de.familyplanner.android.feature.meals.domain.reminder

interface ReminderStore {
    suspend fun read(): ReminderSnapshot?

    suspend fun write(snapshot: ReminderSnapshot)

    suspend fun clear()
}

interface ReminderScheduler {
    fun replace(reminders: List<Reminder>)

    fun cancelAll()
}

interface Notifier {
    fun remind(reminder: DeadlineReminder)

    fun announce(notice: StatusNotice)
}

/** Stößt den Abgleich im Hintergrund an (WorkManager). */
interface ReminderSync {
    fun requestNow()

    fun schedulePeriodic()
}
