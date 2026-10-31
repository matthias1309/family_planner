package de.familyplanner.gamification

import de.familyplanner.core.error.AppException
import de.familyplanner.core.member.Member
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.MemberRepository
import de.familyplanner.core.member.Role

/** Only children take part in the game: unknown member -> 404, parent -> 400. */
internal suspend fun MemberRepository.requireChild(id: MemberId): Member {
    val member = findById(id) ?: throw AppException.NotFound("Mitglied nicht gefunden")
    if (member.role != Role.CHILD) throw AppException.Validation("Eltern sammeln keine Punkte")
    return member
}
