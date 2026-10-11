package de.familyplanner.core.member

import de.familyplanner.core.device.Device
import de.familyplanner.core.device.NewDevice

interface MemberRepository {
    suspend fun findAll(): List<Member>

    suspend fun findById(id: MemberId): Member?

    suspend fun existsByName(name: String): Boolean

    suspend fun count(): Long

    suspend fun create(member: Member): Member

    /** Atomically creates the first member and its device; returns null if members already exist. */
    suspend fun createFirstParentWithDevice(
        member: Member,
        device: NewDevice,
    ): Device?
}
