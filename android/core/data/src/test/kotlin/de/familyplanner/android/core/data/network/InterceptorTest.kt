package de.familyplanner.android.core.data.network

import de.familyplanner.android.core.data.session.DefaultSessionEvents
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.core.data.session.SessionEvent
import de.familyplanner.android.core.testing.InMemorySessionStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InterceptorTest {
    private val session = Session("tok", "m", "Lea", Role.CHILD, "d")
    private val sessions = InMemorySessionStore()
    private val events = DefaultSessionEvents()
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = createHttpClient(sessions, events)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun call() = client.newCall(Request.Builder().url(server.url("/x")).build()).execute().close()

    @Test
    fun `bearer header is sent only with a session`() {
        server.enqueue(MockResponse.Builder().build())
        call()
        assertNull(server.takeRequest().headers["Authorization"])

        sessions.setCurrent(session)
        server.enqueue(MockResponse.Builder().build())
        call()
        assertEquals("Bearer tok", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `401 with token clears session and emits SignedOut`() =
        runTest(UnconfinedTestDispatcher()) {
            sessions.setCurrent(session)
            val received = mutableListOf<SessionEvent>()
            val job = launch { events.events.collect { received += it } }
            server.enqueue(MockResponse.Builder().code(401).build())

            call()

            assertNull(sessions.current)
            assertEquals(listOf<SessionEvent>(SessionEvent.SignedOut), received)
            job.cancel()
        }

    @Test
    fun `401 without token does not emit`() =
        runTest(UnconfinedTestDispatcher()) {
            val received = mutableListOf<SessionEvent>()
            val job = launch { events.events.collect { received += it } }
            server.enqueue(MockResponse.Builder().code(401).build())

            call()

            assertEquals(emptyList(), received)
            job.cancel()
        }

    @Test
    fun `401 for an outdated token does not sign out a newer session`() =
        runTest(UnconfinedTestDispatcher()) {
            sessions.setCurrent(session)
            val newer = session.copy(token = "new-token")
            val swapSession =
                Interceptor { chain ->
                    sessions.setCurrent(newer)
                    chain.proceed(chain.request())
                }
            val racing =
                OkHttpClient
                    .Builder()
                    .addInterceptor(AuthInterceptor(sessions))
                    .addInterceptor(UnauthorizedInterceptor(sessions, events))
                    .addInterceptor(swapSession)
                    .build()
            val received = mutableListOf<SessionEvent>()
            val job = launch { events.events.collect { received += it } }
            server.enqueue(MockResponse.Builder().code(401).build())

            racing.newCall(Request.Builder().url(server.url("/x")).build()).execute().close()

            assertEquals(newer, sessions.current)
            assertEquals(emptyList(), received)
            job.cancel()
        }
}
