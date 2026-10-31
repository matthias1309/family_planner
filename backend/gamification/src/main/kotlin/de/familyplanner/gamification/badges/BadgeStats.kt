package de.familyplanner.gamification.badges

import de.familyplanner.gamification.ledger.EntryKind
import de.familyplanner.gamification.ledger.LedgerEntry
import de.familyplanner.gamification.ledger.totalsFrom
import java.time.LocalDate

/** Pure statistics over a child's whole ledger. */
fun badgeStatsFrom(entries: List<LedgerEntry>): BadgeStats {
    val taskDays = daysOf(entries, EntryKind.TASK)
    val cookingDays = daysOf(entries, EntryKind.COOKING)
    return BadgeStats(
        tasks = entries.count { it.kind == EntryKind.TASK && it.amount > 0 },
        cookings = entries.count { it.kind == EntryKind.COOKING && it.amount > 0 },
        collected = totalsFrom(entries.map { it.kind to it.amount }).collected,
        longestTaskStreak = longestStreak(taskDays),
        taskAndCookingDays = taskDays.intersect(cookingDays).size,
    )
}

private fun daysOf(
    entries: List<LedgerEntry>,
    kind: EntryKind,
): Set<LocalDate> = entries.filter { it.kind == kind && it.amount > 0 }.map { LocalDate.ofInstant(it.bookedAt, BADGE_ZONE) }.toSet()

/** Length of the longest run of consecutive calendar days. */
internal fun longestStreak(days: Set<LocalDate>): Int {
    var longest = 0
    var current = 0
    var previous: LocalDate? = null
    for (day in days.sorted()) {
        current = if (previous != null && previous.plusDays(1) == day) current + 1 else 1
        longest = maxOf(longest, current)
        previous = day
    }
    return longest
}
