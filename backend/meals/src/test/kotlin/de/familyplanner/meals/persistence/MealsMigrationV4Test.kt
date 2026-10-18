package de.familyplanner.meals.persistence

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MealsMigrationV4Test {
    @TempDir
    lateinit var dir: Path

    private fun Connection.runMigration(name: String) {
        val sql = checkNotNull(javaClass.getResource("/db/migration/$name")) { "missing $name" }.readText()
        createStatement().use { statement ->
            sql
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach(statement::execute)
        }
    }

    @Test
    fun `REQ-MEAL-032 dishes planned before V4 stay approved and on time`() {
        DriverManager.getConnection("jdbc:sqlite:${dir.resolve("t.db")}").use { connection ->
            // Arrange: schema as deployed after phase 3 with one parent dish
            listOf("V1__auth.sql", "V2__meals_attendance.sql", "V3__meals_plan.sql").forEach { connection.runMigration(it) }
            connection.createStatement().use {
                it.execute("INSERT INTO members(id, name, role, created_at) VALUES ('m1', 'Mama', 'PARENT', 0)")
                it.execute(
                    "INSERT INTO meal_dishes(id, meal_date, cook_member_id, name, created_at, updated_at) " +
                        "VALUES ('d1', '2026-10-20', 'm1', 'Suppe', 0, 0)",
                )
            }

            // Act
            connection.runMigration("V4__meals_submissions.sql")

            // Assert
            connection.createStatement().use { statement ->
                val row = statement.executeQuery("SELECT status, rejection_reason, late FROM meal_dishes WHERE id = 'd1'")
                row.next()
                assertEquals("APPROVED", row.getString("status"))
                assertNull(row.getString("rejection_reason"))
                assertEquals(0, row.getInt("late"))
            }
        }
    }
}
