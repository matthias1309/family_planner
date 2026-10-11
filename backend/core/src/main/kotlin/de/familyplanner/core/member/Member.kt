package de.familyplanner.core.member

import java.time.Instant

@JvmInline
value class MemberId(
    val value: String,
)

enum class Role { PARENT, CHILD }

data class Member(
    val id: MemberId,
    val name: String,
    val role: Role,
    val createdAt: Instant,
)
