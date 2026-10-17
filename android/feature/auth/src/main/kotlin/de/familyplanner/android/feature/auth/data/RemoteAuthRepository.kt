package de.familyplanner.android.feature.auth.data

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.apiCall
import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.feature.auth.domain.AuthRepository
import de.familyplanner.android.feature.auth.domain.Me
import de.familyplanner.api.client.AuthApi
import de.familyplanner.api.model.PairDeviceRequest
import de.familyplanner.api.model.SetupRequest
import kotlinx.serialization.json.Json

class RemoteAuthRepository(
    private val api: AuthApi,
    private val json: Json,
) : AuthRepository {
    override suspend fun setup(
        setupToken: String,
        parentName: String,
        deviceName: String,
    ): ApiResult<Session> = apiCall(json, { api.setupFamily(SetupRequest(setupToken, parentName, deviceName)) }) { it.data?.toSession() }

    override suspend fun pair(
        code: String,
        deviceName: String,
    ): ApiResult<Session> = apiCall(json, { api.pairDevice(PairDeviceRequest(code, deviceName)) }) { it.data?.toSession() }

    override suspend fun me(): ApiResult<Me> = apiCall(json, { api.getMe() }) { it.data?.toDomain() }
}
