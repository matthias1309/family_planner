package de.familyplanner.core.device

import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.toMember
import de.familyplanner.core.persistence.DevicesTable
import de.familyplanner.core.persistence.MembersTable
import de.familyplanner.core.persistence.dbQuery
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

class ExposedDeviceRepository(
    private val db: Database,
) : DeviceRepository {
    override suspend fun findAuthByTokenHash(tokenHash: String): DeviceAuth? =
        dbQuery(db) {
            (DevicesTable innerJoin MembersTable)
                .selectAll()
                .where { DevicesTable.tokenHash eq tokenHash }
                .singleOrNull()
                ?.let { DeviceAuth(it.toDevice(), it.toMember()) }
        }

    override suspend fun findActive(): List<Device> =
        dbQuery(db) {
            DevicesTable
                .selectAll()
                .where { DevicesTable.revokedAt.isNull() }
                .orderBy(DevicesTable.createdAt)
                .map { it.toDevice() }
        }

    override suspend fun touch(
        id: DeviceId,
        at: Instant,
    ) = dbQuery(db) {
        DevicesTable.update({ DevicesTable.id eq id.value }) { it[lastSeenAt] = at.toEpochMilli() }
        Unit
    }

    override suspend fun revoke(
        id: DeviceId,
        at: Instant,
    ): Boolean =
        dbQuery(db) {
            DevicesTable.update({ (DevicesTable.id eq id.value) and DevicesTable.revokedAt.isNull() }) {
                it[revokedAt] = at.toEpochMilli()
            } > 0
        }
}

internal fun JdbcTransaction.insertDevice(newDevice: NewDevice) {
    val device = newDevice.device
    DevicesTable.insert {
        it[id] = device.id.value
        it[memberId] = device.memberId.value
        it[name] = device.name
        it[tokenHash] = newDevice.tokenHash
        it[createdAt] = device.createdAt.toEpochMilli()
    }
}

internal fun ResultRow.toDevice() =
    Device(
        id = DeviceId(this[DevicesTable.id]),
        memberId = MemberId(this[DevicesTable.memberId]),
        name = this[DevicesTable.name],
        createdAt = Instant.ofEpochMilli(this[DevicesTable.createdAt]),
        lastSeenAt = this[DevicesTable.lastSeenAt]?.let(Instant::ofEpochMilli),
        revokedAt = this[DevicesTable.revokedAt]?.let(Instant::ofEpochMilli),
    )
