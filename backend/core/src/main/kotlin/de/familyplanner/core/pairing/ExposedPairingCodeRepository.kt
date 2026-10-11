package de.familyplanner.core.pairing

import de.familyplanner.core.device.NewDevice
import de.familyplanner.core.device.insertDevice
import de.familyplanner.core.device.toDevice
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.toMember
import de.familyplanner.core.persistence.DevicesTable
import de.familyplanner.core.persistence.MembersTable
import de.familyplanner.core.persistence.PairingCodesTable
import de.familyplanner.core.persistence.dbQuery
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

class ExposedPairingCodeRepository(
    private val db: Database,
) : PairingCodeRepository {
    override suspend fun replaceForMember(
        memberId: MemberId,
        codeHash: String,
        expiresAt: Instant,
        now: Instant,
    ) = dbQuery(db) {
        PairingCodesTable.deleteWhere {
            (PairingCodesTable.memberId eq memberId.value) or
                PairingCodesTable.usedAt.isNotNull() or
                (PairingCodesTable.expiresAt less now.toEpochMilli())
        }
        PairingCodesTable.insert {
            it[PairingCodesTable.codeHash] = codeHash
            it[PairingCodesTable.memberId] = memberId.value
            it[PairingCodesTable.expiresAt] = expiresAt.toEpochMilli()
        }
        Unit
    }

    override suspend fun redeem(
        codeHash: String,
        now: Instant,
        device: (MemberId) -> NewDevice,
    ): RedeemedPairing? =
        dbQuery(db) {
            val code =
                PairingCodesTable.selectAll().where { PairingCodesTable.codeHash eq codeHash }.singleOrNull()
                    ?: return@dbQuery null
            if (code[PairingCodesTable.expiresAt] <= now.toEpochMilli()) return@dbQuery null
            val claimed =
                PairingCodesTable.update({
                    (PairingCodesTable.codeHash eq codeHash) and PairingCodesTable.usedAt.isNull()
                }) { it[usedAt] = now.toEpochMilli() }
            if (claimed == 0) return@dbQuery null

            val memberId = code[PairingCodesTable.memberId]
            val member =
                MembersTable
                    .selectAll()
                    .where { MembersTable.id eq memberId }
                    .single()
                    .toMember()
            val newDevice = device(member.id)
            insertDevice(newDevice)
            val stored = DevicesTable.selectAll().where { DevicesTable.id eq newDevice.device.id.value }.single()
            RedeemedPairing(member, stored.toDevice())
        }
}
