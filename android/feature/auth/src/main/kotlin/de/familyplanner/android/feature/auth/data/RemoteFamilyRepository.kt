package de.familyplanner.android.feature.auth.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.apiCall
import de.familyplanner.android.core.data.apiCallNoContent
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.feature.auth.domain.FamilyDevice
import de.familyplanner.android.feature.auth.domain.FamilyMember
import de.familyplanner.android.feature.auth.domain.FamilyRepository
import de.familyplanner.android.feature.auth.domain.IssuedPairingCode
import de.familyplanner.api.client.DevicesApi
import de.familyplanner.api.client.MembersApi
import de.familyplanner.api.model.CreateMemberRequest
import kotlinx.serialization.json.Json

class RemoteFamilyRepository(
    private val members: MembersApi,
    private val devices: DevicesApi,
    private val json: Json,
) : FamilyRepository {
    override suspend fun members(): ApiResult<List<FamilyMember>> =
        apiCall(json, { members.listMembers() }) { response -> response.data?.map { it.toDomain() } }

    override suspend fun createMember(
        name: String,
        role: Role,
    ): ApiResult<FamilyMember> = apiCall(json, { members.createMember(CreateMemberRequest(name, role.toApi())) }) { it.data?.toDomain() }

    override suspend fun createPairingCode(memberId: String): ApiResult<IssuedPairingCode> =
        apiCall(json, { members.createPairingCode(memberId) }) { it.data?.toDomain() }

    override suspend fun devices(): ApiResult<List<FamilyDevice>> =
        apiCall(json, { devices.listDevices() }) { response -> response.data?.mapAllOrNull { it.toDomain() } }

    override suspend fun revokeDevice(deviceId: String): ApiResult<Unit> = apiCallNoContent(json) { devices.revokeDevice(deviceId) }
}

/** Wandelt alle Elemente um; `null`, sobald eines nicht umgewandelt werden kann (Serverfehler statt halber Liste). */
private inline fun <T, R : Any> List<T>.mapAllOrNull(transform: (T) -> R?): List<R>? {
    val result = ArrayList<R>(size)
    for (item in this) result += transform(item) ?: return null
    return result
}
