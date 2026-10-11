package de.familyplanner.core.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

suspend fun <T> dbQuery(
    db: Database,
    block: JdbcTransaction.() -> T,
): T = withContext(Dispatchers.IO) { transaction(db) { block() } }
