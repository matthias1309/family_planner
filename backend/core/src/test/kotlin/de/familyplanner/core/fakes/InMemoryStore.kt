package de.familyplanner.core.fakes

import de.familyplanner.core.device.Device
import de.familyplanner.core.device.DeviceAuth
import de.familyplanner.core.device.DeviceId
import de.familyplanner.core.device.DeviceRepository
import de.familyplanner.core.device.NewDevice
import de.familyplanner.core.member.Member
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.MemberRepository
import de.familyplanner.core.member.Role
import de.familyplanner.core.pairing.PairingCodeRepository
import de.familyplanner.core.pairing.RedeemedPairing
import java.time.Instant

/** Shared state behind the in-memory repositories so atomic cross-table operations stay consistent. */
class InMemoryStore {
    val members = mutableListOf<Member>()
    val devices = mutableListOf<NewDevice>()
    val codes = mutableListOf<StoredCode>()
    var touchCount = 0

    data class StoredCode(
        val codeHash: String,
        val memberId: MemberId,
        val expiresAt: Instant,
        val usedAt: Instant? = null,
    )
}

class InMemoryMemberRepository(
    private val store: InMemoryStore = InMemoryStore(),
) : MemberRepository {
    override suspend fun findAll(): List<Member> = store.members.sortedBy { it.createdAt }

    override suspend fun findById(id: MemberId): Member? = store.members.firstOrNull { it.id == id }

    override suspend fun existsByName(name: String): Boolean = store.members.any { it.name.equals(name, ignoreCase = true) }

    override suspend fun count(): Long = store.members.size.toLong()

    override suspend fun create(member: Member): Member = member.also { store.members += it }

    override suspend fun createFirstParentWithDevice(
        member: Member,
        device: NewDevice,
    ): Device? {
        if (store.members.isNotEmpty()) return null
        require(member.role == Role.PARENT)
        store.members += member
        store.devices += device
        return device.device
    }
}

class InMemoryDeviceRepository(
    private val store: InMemoryStore = InMemoryStore(),
) : DeviceRepository {
    override suspend fun findAuthByTokenHash(tokenHash: String): DeviceAuth? {
        val stored = store.devices.firstOrNull { it.tokenHash == tokenHash } ?: return null
        val member = store.members.firstOrNull { it.id == stored.device.memberId } ?: return null
        return DeviceAuth(stored.device, member)
    }

    override suspend fun findActive(): List<Device> = store.devices.map { it.device }.filter { it.revokedAt == null }

    override suspend fun touch(
        id: DeviceId,
        at: Instant,
    ) {
        store.touchCount++
        replace(id) { it.copy(lastSeenAt = at) }
    }

    override suspend fun revoke(
        id: DeviceId,
        at: Instant,
    ): Boolean {
        val active = store.devices.any { it.device.id == id && it.device.revokedAt == null }
        if (active) replace(id) { it.copy(revokedAt = at) }
        return active
    }

    private fun replace(
        id: DeviceId,
        change: (Device) -> Device,
    ) {
        val index = store.devices.indexOfFirst { it.device.id == id }
        if (index >= 0) store.devices[index] = store.devices[index].let { it.copy(device = change(it.device)) }
    }
}

class InMemoryPairingCodeRepository(
    private val store: InMemoryStore = InMemoryStore(),
) : PairingCodeRepository {
    override suspend fun replaceForMember(
        memberId: MemberId,
        codeHash: String,
        expiresAt: Instant,
        now: Instant,
    ) {
        store.codes.removeAll { it.memberId == memberId || it.usedAt != null || it.expiresAt < now }
        store.codes += InMemoryStore.StoredCode(codeHash, memberId, expiresAt)
    }

    override suspend fun redeem(
        codeHash: String,
        now: Instant,
        device: (MemberId) -> NewDevice,
    ): RedeemedPairing? {
        val index = store.codes.indexOfFirst { it.codeHash == codeHash }
        if (index < 0) return null
        val code = store.codes[index]
        if (code.usedAt != null || code.expiresAt <= now) return null
        val member = store.members.firstOrNull { it.id == code.memberId } ?: return null
        store.codes[index] = code.copy(usedAt = now)
        val newDevice = device(member.id)
        store.devices += newDevice
        return RedeemedPairing(member, newDevice.device)
    }
}
