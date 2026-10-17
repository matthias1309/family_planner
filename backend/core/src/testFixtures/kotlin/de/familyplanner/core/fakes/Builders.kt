package de.familyplanner.core.fakes

import de.familyplanner.core.member.Member
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import java.time.Instant
import java.util.UUID

fun aMember(
    name: String = "Mitglied-${UUID.randomUUID()}".take(MAX_TEST_NAME),
    role: Role = Role.CHILD,
) = Member(
    id = MemberId(UUID.randomUUID().toString()),
    name = name,
    role = role,
    createdAt = Instant.parse("2026-10-12T09:00:00Z"),
)

private const val MAX_TEST_NAME = 40
