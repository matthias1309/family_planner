package de.familyplanner.android.core.data.network

import de.familyplanner.android.core.data.session.SessionEvent
import de.familyplanner.android.core.data.session.SessionEvents
import de.familyplanner.android.core.data.session.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

private const val HTTP_UNAUTHORIZED = 401

/** Meldet ab, wenn ein Request *mit* Token mit 401 beantwortet wird (Gerät entfernt). */
class UnauthorizedInterceptor(
    private val sessions: SessionStore,
    private val events: SessionEvents,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val sentToken = chain.request().header("Authorization")?.removePrefix("Bearer ")
        // Nur abmelden, wenn das abgelehnte Token noch das aktuelle ist (kein Request einer alten Session).
        val rejectedCurrentSession = sentToken != null && sentToken == sessions.current?.token
        if (rejectedCurrentSession && response.code == HTTP_UNAUTHORIZED) {
            // OkHttp-Interceptoren laufen auf Dispatcher-Threads, nicht auf dem Main-Thread.
            runBlocking { sessions.clear() }
            events.emit(SessionEvent.SignedOut)
        }
        return response
    }
}
