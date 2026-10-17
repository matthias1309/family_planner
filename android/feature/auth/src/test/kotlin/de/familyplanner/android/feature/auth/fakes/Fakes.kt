package de.familyplanner.android.feature.auth.fakes

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.feature.auth.domain.AuthRepository
import de.familyplanner.android.feature.auth.domain.FamilyDevice
import de.familyplanner.android.feature.auth.domain.FamilyMember
import de.familyplanner.android.feature.auth.domain.FamilyRepository
import de.familyplanner.android.feature.auth.domain.IssuedPairingCode
import de.familyplanner.android.feature.auth.domain.Me

val aSession = Session("tok", "m-1", "Lea", Role.CHILD, "d-1")

class FakeAuthRepository : AuthRepository {
    var setupResult: ApiResult<Session> = ApiResult.Success(aSession)
    var pairResult: ApiResult<Session> = ApiResult.Success(aSession)
    var meResult: ApiResult<Me> = ApiResult.Success(Me("m-1", "Lea", Role.CHILD, "d-1"))
    var calls = 0
        private set

    override suspend fun setup(
        setupToken: String,
        parentName: String,
        deviceName: String,
    ): ApiResult<Session> = setupResult.also { calls++ }

    override suspend fun pair(
        code: String,
        deviceName: String,
    ): ApiResult<Session> = pairResult.also { calls++ }

    override suspend fun me(): ApiResult<Me> = meResult.also { calls++ }
}

class FakeFamilyRepository : FamilyRepository {
    var membersResult: ApiResult<List<FamilyMember>> = ApiResult.Success(emptyList())
    var devicesResult: ApiResult<List<FamilyDevice>> = ApiResult.Success(emptyList())
    var createMemberResult: ApiResult<FamilyMember> = ApiResult.Success(FamilyMember("m-2", "Max", Role.CHILD))
    var codeResult: ApiResult<IssuedPairingCode> =
        ApiResult.Success(IssuedPairingCode("483920", java.time.Instant.parse("2026-10-12T16:42:00Z")))
    var revokeResult: ApiResult<Unit> = ApiResult.Success(Unit)
    val revokedIds = mutableListOf<String>()
    val createdMembers = mutableListOf<Pair<String, Role>>()

    override suspend fun members() = membersResult

    override suspend fun createMember(
        name: String,
        role: Role,
    ): ApiResult<FamilyMember> {
        createdMembers += name to role
        return createMemberResult
    }

    override suspend fun createPairingCode(memberId: String) = codeResult

    override suspend fun devices() = devicesResult

    override suspend fun revokeDevice(deviceId: String): ApiResult<Unit> {
        revokedIds += deviceId
        return revokeResult
    }
}
