package de.familyplanner.android.feature.meals.fakes

import de.familyplanner.android.feature.meals.domain.reminder.DeadlineReminder
import de.familyplanner.android.feature.meals.domain.reminder.Notifier
import de.familyplanner.android.feature.meals.domain.reminder.Reminder
import de.familyplanner.android.feature.meals.domain.reminder.ReminderScheduler
import de.familyplanner.android.feature.meals.domain.reminder.ReminderSnapshot
import de.familyplanner.android.feature.meals.domain.reminder.ReminderStore
import de.familyplanner.android.feature.meals.domain.reminder.ReminderSync
import de.familyplanner.android.feature.meals.domain.reminder.StatusNotice

class FakeReminderStore(
    var snapshot: ReminderSnapshot? = null,
) : ReminderStore {
    var cleared = 0
    var writes = 0

    override suspend fun read() = snapshot

    override suspend fun write(snapshot: ReminderSnapshot) {
        writes++
        this.snapshot = snapshot
    }

    override suspend fun clear() {
        cleared++
        snapshot = null
    }
}

class FakeReminderScheduler : ReminderScheduler {
    val replaced = mutableListOf<List<Reminder>>()
    var cancelled = 0

    override fun replace(reminders: List<Reminder>) {
        replaced += listOf(reminders)
    }

    override fun cancelAll() {
        cancelled++
    }
}

class FakeNotifier : Notifier {
    val reminders = mutableListOf<DeadlineReminder>()
    val notices = mutableListOf<StatusNotice>()

    override fun remind(reminder: DeadlineReminder) {
        reminders += reminder
    }

    override fun announce(notice: StatusNotice) {
        notices += notice
    }
}

class FakeReminderSync : ReminderSync {
    var requests = 0
    var periodic = 0

    override fun requestNow() {
        requests++
    }

    override fun schedulePeriodic() {
        periodic++
    }
}
