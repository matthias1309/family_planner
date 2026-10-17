package de.familyplanner.android.core.data.session

import kotlinx.serialization.Serializable

@Serializable
enum class Role { PARENT, CHILD }

@Serializable
data class Session(
    val token: String,
    val memberId: String,
    val memberName: String,
    val role: Role,
    val deviceId: String,
) {
    // Das Token darf nie in Logs oder Fehlermeldungen landen.
    override fun toString(): String = "Session(memberId=$memberId, role=$role, deviceId=$deviceId)"
}
