package de.familyplanner.android.feature.auth.domain

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.Session

interface AuthRepository {
    suspend fun setup(
        setupToken: String,
        parentName: String,
        deviceName: String,
    ): ApiResult<Session>

    suspend fun pair(
        code: String,
        deviceName: String,
    ): ApiResult<Session>

    suspend fun me(): ApiResult<Me>
}

interface FamilyRepository {
    suspend fun members(): ApiResult<List<FamilyMember>>

    suspend fun createMember(
        name: String,
        role: Role,
    ): ApiResult<FamilyMember>

    suspend fun createPairingCode(memberId: String): ApiResult<IssuedPairingCode>

    suspend fun devices(): ApiResult<List<FamilyDevice>>

    suspend fun revokeDevice(deviceId: String): ApiResult<Unit>
}
