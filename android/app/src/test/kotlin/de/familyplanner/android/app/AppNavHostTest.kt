package de.familyplanner.android.app

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.session.DefaultSessionEvents
import de.familyplanner.android.core.data.session.Role
import de.familyplanner.android.core.data.session.Session
import de.familyplanner.android.core.data.session.SessionEvent
import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.auth.domain.AuthRepository
import de.familyplanner.android.feature.auth.domain.Me
import de.familyplanner.android.feature.auth.domain.RefreshMeUseCase
import de.familyplanner.android.feature.auth.ui.home.HomeViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppNavHostTest {
    @get:Rule
    val compose = createComposeRule()

    private val session = Session("tok", "m", "Lea", Role.CHILD, "d")
    private val sessions = InMemorySessionStore()
    private val events = DefaultSessionEvents()

    private val authRepository =
        object : AuthRepository {
            override suspend fun setup(
                setupToken: String,
                parentName: String,
                deviceName: String,
            ) = ApiResult.Success(session)

            override suspend fun pair(
                code: String,
                deviceName: String,
            ) = ApiResult.Success(session)

            override suspend fun me() = ApiResult.Success(Me("m", "Lea", Role.CHILD, "d"))
        }

    @Before
    fun setUp() {
        startKoin {
            modules(module { factory { HomeViewModel(sessions, RefreshMeUseCase(authRepository, sessions)) } })
        }
    }

    @After
    fun tearDown() = stopKoin()

    private fun show() = compose.setContent { FamilyPlannerTheme { AppNavHost(sessions, events) } }

    @Test
    fun `without session the welcome screen is shown`() {
        show()

        compose.onNodeWithText("Mit Code verbinden").assertIsDisplayed()
    }

    @Test
    fun `with session the home screen greets the member`() {
        sessions.setCurrent(session)
        show()

        compose.onNodeWithText("Hallo Lea 👋").assertIsDisplayed()
    }

    @Test
    fun `session switch moves between welcome and home`() {
        show()
        compose.onNodeWithText("Mit Code verbinden").assertIsDisplayed()

        sessions.setCurrent(session)
        compose.waitForIdle()
        compose.onNodeWithText("Hallo Lea 👋").assertIsDisplayed()

        sessions.setCurrent(null)
        compose.waitForIdle()
        compose.onNodeWithText("Mit Code verbinden").assertIsDisplayed()
    }

    @Test
    fun `sign out event shows hint on the welcome screen`() {
        show()

        events.emit(SessionEvent.SignedOut)
        compose.waitForIdle()

        compose.onNodeWithText("Dieses Gerät wurde abgemeldet. Bitte neu koppeln.").assertIsDisplayed()
    }
}
