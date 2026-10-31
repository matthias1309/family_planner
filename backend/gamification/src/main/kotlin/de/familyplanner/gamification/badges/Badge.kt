package de.familyplanner.gamification.badges

import java.time.ZoneId

/** Values computed from a child's whole ledger; input of every badge rule. */
data class BadgeStats(
    val tasks: Int,
    val cookings: Int,
    val collected: Int,
    val longestTaskStreak: Int,
    val taskAndCookingDays: Int,
) {
    companion object {
        val ZERO = BadgeStats(0, 0, 0, 0, 0)
    }
}

/** Calendar days of badge rules (streaks) are counted in this zone. */
val BADGE_ZONE: ZoneId = ZoneId.of("Europe/Berlin")

/** Fixed catalog (PRD); order = display order. [id] is stored in game_badges - never change it. */
@Suppress("LongParameterList") // declarative catalog row
enum class Badge(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val target: Int,
    val secret: Boolean = false,
    val hint: String? = null,
    val metric: (BadgeStats) -> Int,
) {
    FIRST_TASK("first-task", "🧹", "Erste Aufgabe", "Erledige deine erste Aufgabe", 1, metric = { it.tasks }),
    TASKS_10("tasks-10", "✅", "Fleißig", "Erledige 10 Aufgaben", 10, metric = { it.tasks }),
    TASKS_50("tasks-50", "🏆", "Haushaltsprofi", "Erledige 50 Aufgaben", 50, metric = { it.tasks }),
    FIRST_COOKING("first-cooking", "🍳", "Erstes Gericht", "Koche dein erstes Gericht", 1, metric = { it.cookings }),
    COOKING_10("cooking-10", "👨‍🍳", "Küchenchef", "Koche 10 Gerichte", 10, metric = { it.cookings }),
    COOKING_25("cooking-25", "⭐", "Sterneköchin/-koch", "Koche 25 Gerichte", 25, metric = { it.cookings }),
    POINTS_100("points-100", "💯", "Hunderter", "Sammle 100 Punkte", 100, metric = { it.collected }),
    POINTS_500("points-500", "💎", "Punktesammler", "Sammle 500 Punkte", 500, metric = { it.collected }),
    STREAK_7(
        "streak-7",
        "🔥",
        "Dranbleiber",
        "Erledige an 7 Tagen in Folge eine Aufgabe",
        7,
        metric = { it.longestTaskStreak },
    ),
    STREAK_30(
        "streak-30",
        "🌋",
        "Unaufhaltsam",
        "Erledige an 30 Tagen in Folge eine Aufgabe",
        30,
        secret = true,
        hint = "Bleib richtig lange dran",
        metric = { it.longestTaskStreak },
    ),
    ALL_ROUNDER(
        "all-rounder",
        "🦸",
        "Allrounder",
        "Erledige an einem Tag eine Aufgabe und koche",
        1,
        secret = true,
        hint = "Haushalt und Küche an einem Tag",
        metric = { it.taskAndCookingDays },
    ),
    POINTS_1000(
        "points-1000",
        "👑",
        "Legende",
        "Sammle 1000 Punkte",
        1000,
        secret = true,
        hint = "Nur für die ganz Großen",
        metric = { it.collected },
    ),
    ;

    fun isMetBy(stats: BadgeStats) = metric(stats) >= target

    fun progressOf(stats: BadgeStats) = minOf(metric(stats), target)
}
