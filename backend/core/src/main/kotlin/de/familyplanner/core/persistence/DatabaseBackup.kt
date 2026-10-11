package de.familyplanner.core.persistence

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/** Writes a consistent copy of the database (VACUUM INTO) before pending migrations run. */
class DatabaseBackup(
    private val dbPath: Path,
    private val clock: Clock,
    private val maxBackups: Int = MAX_BACKUPS,
) {
    private val log = LoggerFactory.getLogger(DatabaseBackup::class.java)

    fun beforeMigration(
        databaseExisted: Boolean,
        hasPendingMigrations: Boolean,
    ): Path? {
        if (!hasPendingMigrations || !databaseExisted) return null
        val backupDir = dbPath.toAbsolutePath().parent.resolve(BACKUP_DIR)
        Files.createDirectories(backupDir)
        val timestamp = TIMESTAMP_FORMAT.format(clock.instant().atOffset(ZoneOffset.UTC))
        val target = backupDir.resolve("$BACKUP_PREFIX$timestamp$BACKUP_SUFFIX")
        target.deleteIfExists()
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
            connection.createStatement().use { it.execute("VACUUM INTO '${sqlQuote(target)}'") }
        }
        log.info("Database backup written to {}", target)
        rotate(backupDir)
        return target
    }

    private fun rotate(backupDir: Path) {
        backupDir
            .listDirectoryEntries("$BACKUP_PREFIX*$BACKUP_SUFFIX")
            .sortedByDescending { it.name }
            .drop(maxBackups)
            .forEach { it.deleteIfExists() }
    }

    private fun sqlQuote(path: Path): String = path.toString().replace("'", "''")

    companion object {
        const val MAX_BACKUPS = 10
        private const val BACKUP_DIR = "backups"
        private const val BACKUP_PREFIX = "family-planner-"
        private const val BACKUP_SUFFIX = ".db"
        private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
