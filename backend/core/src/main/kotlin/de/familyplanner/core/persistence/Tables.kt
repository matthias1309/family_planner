package de.familyplanner.core.persistence

import org.jetbrains.exposed.v1.core.Table

object MembersTable : Table("members") {
    val id = varchar("id", ID_LENGTH)
    val name = varchar("name", NAME_LENGTH)
    val role = varchar("role", ROLE_LENGTH)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object DevicesTable : Table("devices") {
    val id = varchar("id", ID_LENGTH)
    val memberId = varchar("member_id", ID_LENGTH).references(MembersTable.id)
    val name = varchar("name", NAME_LENGTH)
    val tokenHash = varchar("token_hash", HASH_LENGTH)
    val createdAt = long("created_at")
    val lastSeenAt = long("last_seen_at").nullable()
    val revokedAt = long("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object PairingCodesTable : Table("pairing_codes") {
    val codeHash = varchar("code_hash", HASH_LENGTH)
    val memberId = varchar("member_id", ID_LENGTH).references(MembersTable.id)
    val expiresAt = long("expires_at")
    val usedAt = long("used_at").nullable()

    override val primaryKey = PrimaryKey(codeHash)
}

private const val ID_LENGTH = 36
private const val NAME_LENGTH = 60
private const val ROLE_LENGTH = 10
private const val HASH_LENGTH = 64
