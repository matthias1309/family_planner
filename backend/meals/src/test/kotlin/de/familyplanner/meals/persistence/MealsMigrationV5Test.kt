package de.familyplanner.meals.persistence

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MealsMigrationV5Test {
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
    fun `REQ-MEAL-062 ingredient lines from before V5 stay as names without quantity`() {
        DriverManager.getConnection("jdbc:sqlite:${dir.resolve("t.db")}").use { connection ->
            // Arrange: schema as deployed after phase 4 with one dish and two ingredient lines
            listOf("V1__auth.sql", "V2__meals_attendance.sql", "V3__meals_plan.sql", "V4__meals_submissions.sql")
                .forEach { connection.runMigration(it) }
            connection.createStatement().use {
                it.execute("INSERT INTO members(id, name, role, created_at) VALUES ('m1', 'Mama', 'PARENT', 0)")
                it.execute(
                    "INSERT INTO meal_dishes(id, meal_date, cook_member_id, name, created_at, updated_at) " +
                        "VALUES ('d1', '2026-10-20', 'm1', 'Suppe', 0, 0)",
                )
                it.execute(
                    "INSERT INTO meal_dish_ingredients(dish_id, position, text) VALUES ('d1', 0, 'Lauch'), ('d1', 1, '500 g Nudeln')",
                )
            }

            // Act
            connection.runMigration("V5__meals_recipes.sql")

            // Assert
            connection.createStatement().use { statement ->
                val rows = statement.executeQuery("SELECT text, quantity, unit FROM meal_dish_ingredients ORDER BY position")
                rows.next()
                assertEquals("Lauch", rows.getString("text"))
                assertNull(rows.getString("unit"))
                assertEquals(0.0, rows.getDouble("quantity"))
                assertEquals(true, rows.wasNull())
                rows.next()
                assertEquals("500 g Nudeln", rows.getString("text"))
                val dish = statement.executeQuery("SELECT recipe_id, servings FROM meal_dishes WHERE id = 'd1'")
                dish.next()
                assertNull(dish.getString("recipe_id"))
                assertNull(dish.getString("servings"))
            }
        }
    }
}
