package de.familyplanner.core.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.time.Clock

object DatabaseFactory {
    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)

    private const val BUSY_TIMEOUT_MS = "5000"
    const val DEFAULT_MIGRATION_LOCATION = "classpath:db/migration"

    fun create(
        dbPath: Path,
        clock: Clock = Clock.systemUTC(),
        migrationLocations: List<String> = listOf(DEFAULT_MIGRATION_LOCATION),
    ): Database {
        Files.createDirectories(dbPath.toAbsolutePath().parent)
        val databaseExisted = Files.exists(dbPath)
        val dataSource = HikariDataSource(hikariConfig(dbPath))

        // Spread is fine here: runs once at startup with a handful of locations.
        @Suppress("SpreadOperator")
        val flyway =
            Flyway
                .configure()
                .dataSource(dataSource)
                .locations(*migrationLocations.toTypedArray())
                .load()
        val pending = flyway.info().pending().size
        DatabaseBackup(dbPath, clock).beforeMigration(databaseExisted = databaseExisted, hasPendingMigrations = pending > 0)
        val result = flyway.migrate()
        log.info("Applied {} migrations", result.migrationsExecuted)
        return Database.connect(
            dataSource,
            databaseConfig = DatabaseConfig { defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE },
        )
    }

    private fun hikariConfig(dbPath: Path) =
        HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:$dbPath"
            maximumPoolSize = 1
            addDataSourceProperty("foreign_keys", "true")
            addDataSourceProperty("journal_mode", "WAL")
            addDataSourceProperty("busy_timeout", BUSY_TIMEOUT_MS)
        }
}
