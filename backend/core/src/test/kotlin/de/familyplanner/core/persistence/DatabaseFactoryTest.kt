package de.familyplanner.core.persistence

import de.familyplanner.core.fakes.MutableClock
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseFactoryTest {
    @TempDir
    lateinit var dir: Path

    private val clock = MutableClock(Instant.parse("2026-10-12T10:00:00Z"))
    private val extraMigrations = listOf(DatabaseFactory.DEFAULT_MIGRATION_LOCATION, "classpath:db/extra")

    private fun pragma(
        db: org.jetbrains.exposed.v1.jdbc.Database,
        name: String,
    ): String =
        transaction(db) {
            exec("PRAGMA $name") { rs -> if (rs.next()) rs.getString(1) else "" } ?: ""
        }

    @Test
    fun `DatabaseFactory enables foreign keys and WAL and applies V1`() {
        // Arrange + Act
        val db = DatabaseFactory.create(dir.resolve("data/test.db"), clock)

        // Assert
        assertEquals("1", pragma(db, "foreign_keys"))
        assertEquals("wal", pragma(db, "journal_mode"))
        val tables =
            transaction(db) {
                exec("SELECT name FROM sqlite_master WHERE type='table'") { rs ->
                    buildList { while (rs.next()) add(rs.getString(1)) }
                }.orEmpty()
            }
        assertTrue(tables.containsAll(listOf("members", "devices", "pairing_codes")))
    }

    @Test
    fun `backup is not written on first start without database file`() {
        // Act
        DatabaseFactory.create(dir.resolve("test.db"), clock)

        // Assert
        assertTrue(!dir.resolve("backups").toFile().exists())
    }

    @Test
    fun `backup is written before pending migrations and rotated`() {
        // Arrange
        val dbPath = dir.resolve("test.db")
        DatabaseFactory.create(dbPath, clock)

        // Act: first run with an additional pending migration, then repeat to exceed the rotation limit
        DatabaseFactory.create(dbPath, clock, extraMigrations)
        assertEquals(1, dir.resolve("backups").listDirectoryEntries().size)
        repeat(DatabaseBackup.MAX_BACKUPS + 1) {
            clock.advance(Duration.ofSeconds(1))
            DatabaseBackup(dbPath, clock).beforeMigration(databaseExisted = true, hasPendingMigrations = true)
        }

        // Assert
        assertEquals(DatabaseBackup.MAX_BACKUPS, dir.resolve("backups").listDirectoryEntries().size)
    }

    @Test
    fun `no backup is written when no migration is pending`() {
        // Arrange
        val dbPath = dir.resolve("test.db")
        DatabaseFactory.create(dbPath, clock)

        // Act
        DatabaseFactory.create(dbPath, clock)

        // Assert
        assertTrue(!dir.resolve("backups").toFile().exists())
    }
}
