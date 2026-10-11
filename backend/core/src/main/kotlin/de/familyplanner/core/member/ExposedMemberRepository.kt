package de.familyplanner.core.member

import de.familyplanner.core.device.Device
import de.familyplanner.core.device.NewDevice
import de.familyplanner.core.device.insertDevice
import de.familyplanner.core.error.AppException
import de.familyplanner.core.persistence.MembersTable
import de.familyplanner.core.persistence.dbQuery
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Instant

class ExposedMemberRepository(
    private val db: Database,
) : MemberRepository {
    override suspend fun findAll(): List<Member> =
        dbQuery(db) {
            MembersTable.selectAll().orderBy(MembersTable.createdAt).map { it.toMember() }
        }

    override suspend fun findById(id: MemberId): Member? =
        dbQuery(db) {
            MembersTable
                .selectAll()
                .where { MembersTable.id eq id.value }
                .singleOrNull()
                ?.toMember()
        }

    override suspend fun existsByName(name: String): Boolean =
        dbQuery(db) {
            MembersTable.selectAll().any { it[MembersTable.name].equals(name, ignoreCase = true) }
        }

    override suspend fun count(): Long = dbQuery(db) { MembersTable.selectAll().count() }

    override suspend fun create(member: Member): Member =
        dbQuery(db) {
            insertMember(member)
            member
        }

    override suspend fun createFirstParentWithDevice(
        member: Member,
        device: NewDevice,
    ): Device? =
        dbQuery(db) {
            if (MembersTable.selectAll().count() > 0) return@dbQuery null
            insertMember(member)
            insertDevice(device)
            device.device
        }

    private fun JdbcTransaction.insertMember(member: Member) {
        try {
            MembersTable.insert {
                it[id] = member.id.value
                it[name] = member.name
                it[role] = member.role.name
                it[createdAt] = member.createdAt.toEpochMilli()
            }
        } catch (e: ExposedSQLException) {
            if (e.message?.contains("UNIQUE", ignoreCase = true) != true) throw e
            throw AppException.Conflict("Name bereits vergeben").also { it.initCause(e) }
        }
    }
}

internal fun ResultRow.toMember() =
    Member(
        id = MemberId(this[MembersTable.id]),
        name = this[MembersTable.name],
        role = Role.valueOf(this[MembersTable.role]),
        createdAt = Instant.ofEpochMilli(this[MembersTable.createdAt]),
    )
