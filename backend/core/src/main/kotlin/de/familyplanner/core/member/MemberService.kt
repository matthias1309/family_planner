package de.familyplanner.core.member

import de.familyplanner.core.error.AppException
import de.familyplanner.core.error.MEMBER_NAME_MAX
import de.familyplanner.core.error.validatedName
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

class MemberService(
    private val members: MemberRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(MemberService::class.java)

    suspend fun create(
        name: String,
        role: Role,
    ): Member {
        val validName = validatedName(name, "Name", MEMBER_NAME_MAX)
        if (members.existsByName(validName)) throw AppException.Conflict("Name bereits vergeben")
        val member = members.create(Member(MemberId(newId()), validName, role, clock.instant()))
        log.info("Member created: {}", member.id.value)
        return member
    }

    suspend fun list(): List<Member> = members.findAll()

    suspend fun get(id: MemberId): Member = members.findById(id) ?: throw AppException.NotFound("Mitglied nicht gefunden")
}
