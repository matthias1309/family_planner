package de.familyplanner.android.core.data.network

import de.familyplanner.android.core.data.session.SessionStore
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sessions: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessions.current?.token ?: return chain.proceed(chain.request())
        val request =
            chain
                .request()
                .newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        return chain.proceed(request)
    }
}
