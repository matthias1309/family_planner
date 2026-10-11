package de.familyplanner.android.feature.auth.domain

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.core.data.session.SessionStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class SetupFamilyUseCase(
    private val repository: AuthRepository,
    private val sessions: SessionStore,
) {
    suspend operator fun invoke(
        setupToken: String,
        parentName: String,
        deviceName: String,
    ): ApiResult<Session> {
        val token = setupToken.trim()
        val name = parentName.trim()
        val device = deviceName.trim()
        validateSetupToken(token)?.let { return it }
        validateName(name)?.let { return it }
        validateDeviceName(device)?.let { return it }
        return repository.setup(token, name, device).also { if (it is ApiResult.Success) sessions.save(it.data) }
    }
}

class PairDeviceUseCase(
    private val repository: AuthRepository,
    private val sessions: SessionStore,
) {
    suspend operator fun invoke(
        code: String,
        deviceName: String,
    ): ApiResult<Session> {
        val trimmedCode = code.trim()
        val device = deviceName.trim()
        validateCode(trimmedCode)?.let { return it }
        validateDeviceName(device)?.let { return it }
        return repository.pair(trimmedCode, device).also { if (it is ApiResult.Success) sessions.save(it.data) }
    }
}

/** Gleicht Name/Rolle der gespeicherten Session mit dem Server ab; Fehler lassen die Session unberührt. */
class RefreshMeUseCase(
    private val repository: AuthRepository,
    private val sessions: SessionStore,
) {
    suspend operator fun invoke(): ApiResult<Me> =
        repository.me().also { result ->
            val current = sessions.current
            if (result is ApiResult.Success && current != null) {
                val updated = current.copy(memberName = result.data.name, role = result.data.role)
                if (updated != current) sessions.save(updated)
            }
        }
}

class LoadFamilyUseCase(
    private val repository: FamilyRepository,
) {
    suspend operator fun invoke(): ApiResult<FamilyOverview> =
        coroutineScope {
            val members = async { repository.members() }
            val devices = async { repository.devices() }
            val membersResult = members.await()
            val devicesResult = devices.await()
            when {
                membersResult is ApiResult.Failure -> membersResult
                devicesResult is ApiResult.Failure -> devicesResult
                membersResult is ApiResult.Success && devicesResult is ApiResult.Success ->
                    ApiResult.Success(FamilyOverview(membersResult.data, devicesResult.data))
                else -> error("unreachable")
            }
        }
}

class CreateMemberUseCase(
    private val repository: FamilyRepository,
) {
    suspend operator fun invoke(
        name: String,
        role: Role,
    ): ApiResult<FamilyMember> {
        val trimmed = name.trim()
        validateName(trimmed)?.let { return it }
        return repository.createMember(trimmed, role)
    }
}

class CreatePairingCodeUseCase(
    private val repository: FamilyRepository,
) {
    suspend operator fun invoke(memberId: String): ApiResult<IssuedPairingCode> = repository.createPairingCode(memberId)
}

/** Entfernt ein Gerät; das eigene Gerät meldet danach lokal ab. */
class RevokeDeviceUseCase(
    private val repository: FamilyRepository,
    private val sessions: SessionStore,
) {
    suspend operator fun invoke(deviceId: String): ApiResult<Unit> {
        val ownDevice = sessions.current?.deviceId == deviceId
        return repository.revokeDevice(deviceId).also { if (it is ApiResult.Success && ownDevice) sessions.clear() }
    }
}
