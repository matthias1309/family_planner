package de.familyplanner.android.feature.meals.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.familyplanner.android.core.data.AppError
import de.familyplanner.android.core.ui.theme.FamilyPlannerTheme
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceDay
import de.familyplanner.android.feature.meals.domain.attendance.AttendancePattern
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceProfile
import de.familyplanner.android.feature.meals.domain.attendance.AttendanceSource
import de.familyplanner.android.feature.meals.domain.attendance.ChildProfile
import de.familyplanner.android.feature.meals.domain.attendance.ChildWeek
import de.familyplanner.android.feature.meals.domain.attendance.DeadlineSettings
import de.familyplanner.android.feature.meals.domain.attendance.DeadlineSettingsOverview
import de.familyplanner.android.feature.meals.domain.attendance.WeekOverview
import de.familyplanner.android.feature.meals.fakes.alternatingPattern
import de.familyplanner.android.feature.meals.fakes.planWeek
import de.familyplanner.android.feature.meals.fakes.someException
import de.familyplanner.android.feature.meals.fakes.someSettings
import de.familyplanner.android.feature.meals.ui.profile.ProfileEditEvent
import de.familyplanner.android.feature.meals.ui.profile.ProfileEditScreen
import de.familyplanner.android.feature.meals.ui.profile.ProfileEditUiState
import de.familyplanner.android.feature.meals.ui.profiles.ProfilesScreen
import de.familyplanner.android.feature.meals.ui.profiles.ProfilesUiState
import de.familyplanner.android.feature.meals.ui.settings.DeadlineSettingsScreen
import de.familyplanner.android.feature.meals.ui.settings.DeadlineSettingsUiState
import de.familyplanner.android.feature.meals.ui.week.WeekOverviewEvent
import de.familyplanner.android.feature.meals.ui.week.WeekOverviewScreen
import de.familyplanner.android.feature.meals.ui.week.WeekOverviewUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) =
        compose.setContent { FamilyPlannerTheme(content = content) }

    private fun days(
        present: Boolean = true,
        exceptionOn: Int? = null,
    ) = (0L..6L).map {
        val source = if (it.toInt() == exceptionOn) AttendanceSource.EXCEPTION else AttendanceSource.PATTERN
        AttendanceDay(planWeek.plusDays(it), present, source)
    }

    private fun overview(vararg children: ChildWeek) = WeekOverview(planWeek, Instant.parse("2026-10-18T14:00:00Z"), children.toList())

    private val deadline = Instant.parse("2026-10-22T14:00:00Z")

    private fun weekState(
        overview: WeekOverview? = overview(),
        isParent: Boolean = false,
        error: AppError? = null,
    ) = WeekOverviewUiState(planWeek, overview, error = error, isParent = isParent)

    private fun showWeek(
        state: WeekOverviewUiState,
        onManage: () -> Unit = {},
        onEvent: (WeekOverviewEvent) -> Unit = {},
    ) = show { WeekOverviewScreen(state, onEvent, onManage, {}) }

    @Test
    fun `REQ-MEAL-012 cooking child shows submission deadline in Berlin time`() {
        showWeek(weekState(overview(ChildWeek("c-1", "Ben", true, days(), deadline))))

        compose.onNodeWithText("Woche 19.10.–25.10.").assertIsDisplayed()
        compose.onNodeWithText("Bestellfrist: So 18.10., 16:00").assertIsDisplayed()
        compose.onNodeWithText("Ben (kocht)").assertIsDisplayed()
        compose.onNodeWithText("Einreichen bis Do 22.10., 16:00").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-012 absent cooking child shows no deadline`() {
        showWeek(weekState(overview(ChildWeek("c-1", "Ben", true, days(present = false), null))))

        compose.onNodeWithText("In dieser Woche nicht da – kein Kochtag").assertIsDisplayed()
    }

    @Test
    fun `non cooking child shows no deadline line`() {
        showWeek(weekState(overview(ChildWeek("c-2", "Lea", false, days(), null))))

        compose.onNodeWithText("Lea").assertIsDisplayed()
        compose.onNodeWithText("In dieser Woche nicht da – kein Kochtag").assertDoesNotExist()
    }

    @Test
    fun `REQ-MEAL-011 exception days are marked`() {
        showWeek(weekState(overview(ChildWeek("c-1", "Ben", true, days(exceptionOn = 2), deadline))))

        compose.onNodeWithContentDescription("Mi: anwesend (Ausnahme)").assertIsDisplayed()
        compose.onNodeWithContentDescription("Di: anwesend").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-010 child sees overview without manage button`() {
        showWeek(weekState(overview(), isParent = false))

        compose.onNodeWithText("Anwesenheit verwalten").assertDoesNotExist()
        compose.onNodeWithText("Noch keine Kinder angelegt.").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-010 parent sees manage button`() {
        var managed = false
        showWeek(weekState(isParent = true), onManage = { managed = true })

        compose.onNodeWithText("Anwesenheit verwalten").performClick()

        assertTrue(managed)
    }

    @Test
    fun `offline error is shown in German`() {
        showWeek(weekState(overview = null, error = AppError.Network))

        compose.onNodeWithText("Server nicht erreichbar. Bist du im Heim-WLAN?").assertIsDisplayed()
    }

    @Test
    fun `week arrows send navigation events and respect limits`() {
        val events = mutableListOf<WeekOverviewEvent>()
        showWeek(weekState(), onEvent = { events += it })

        compose.onNodeWithContentDescription("Nächste Woche").performClick()
        compose.onNodeWithContentDescription("Vorige Woche").performClick()

        assertEquals(listOf(WeekOverviewEvent.NextWeek, WeekOverviewEvent.PreviousWeek), events)
    }

    @Test
    fun `previous week is disabled at the lower limit`() {
        show { WeekOverviewScreen(WeekOverviewUiState(LocalDate.of(2000, 1, 3)), {}, {}, {}) }

        compose.onNodeWithContentDescription("Vorige Woche").assertIsNotEnabled()
    }

    // --- Profile ---

    @Test
    fun `REQ-MEAL-010 profiles list shows cooking flag and pattern summary`() {
        var edited: String? = null
        val state =
            ProfilesUiState(
                profiles =
                    listOf(
                        ChildProfile("c-1", "Ben", AttendanceProfile("c-1", true, alternatingPattern)),
                        ChildProfile("c-2", "Lea", AttendanceProfile("c-2", false, AttendancePattern.Always)),
                    ),
            )
        show { ProfilesScreen(state, {}, onEdit = { edited = it }, onSettings = {}, onBack = {}) }

        compose.onNodeWithText("kocht · Wechselmodell · A: Di–Do · B: Di, So").assertIsDisplayed()
        compose.onNodeWithText("kocht nicht · immer da").assertIsDisplayed()
        compose.onNodeWithText("Ben").performClick()
        assertEquals("c-1", edited)
    }

    @Test
    fun `no children shows hint and settings button works`() {
        var opened = false
        show { ProfilesScreen(ProfilesUiState(), {}, {}, onSettings = { opened = true }, onBack = {}) }

        compose.onNodeWithText("Noch keine Kinder angelegt.").assertIsDisplayed()
        compose.onNodeWithText("Fristen einstellen").performClick()
        assertTrue(opened)
    }

    // --- Profil bearbeiten ---

    private fun editState(
        alternating: Boolean,
        configure: ProfileEditUiState.() -> ProfileEditUiState = { this },
    ) = ProfileEditUiState(
        name = "Ben",
        weekOfToday = LocalDate.of(2026, 10, 12),
        isLoaded = true,
        cooks = true,
        alternating = alternating,
    ).configure()

    @Test
    fun `REQ-MEAL-010 day chips are shown only for alternating`() {
        show { ProfileEditScreen(editState(alternating = false), {}, {}) }

        compose.onNodeWithText("Woche A").assertDoesNotExist()
        compose.onNodeWithText("Woche B").assertDoesNotExist()
    }

    @Test
    fun `REQ-MEAL-010 alternating shows both weeks and this week choice`() {
        show { ProfileEditScreen(editState(alternating = true), {}, {}) }

        compose.onNodeWithText("Woche A").assertIsDisplayed()
        compose.onNodeWithText("Woche B").assertExists()
        compose.onNodeWithText("Diese Woche (12.10.–18.10.) ist Woche …").assertExists()
    }

    @Test
    fun `REQ-MEAL-010 forbidden save shows parents only`() {
        show { ProfileEditScreen(editState(false) { copy(saveError = AppError.Forbidden) }, {}, {}) }

        compose.onNodeWithText("Nur für Eltern.").assertIsDisplayed()
    }

    @Test
    fun `save button and saved hint`() {
        val events = mutableListOf<ProfileEditEvent>()
        show { ProfileEditScreen(editState(false) { copy(saved = true) }, { events += it }, {}) }

        compose.onNodeWithText("Gespeichert.").assertIsDisplayed()
        compose.onNodeWithText("Speichern").performClick()
        assertEquals(listOf<ProfileEditEvent>(ProfileEditEvent.Save), events)
    }

    @Test
    fun `REQ-MEAL-011 exception row shows range presence and note`() {
        val events = mutableListOf<ProfileEditEvent>()
        show { ProfileEditScreen(editState(false) { copy(exceptions = listOf(someException)) }, { events += it }, {}) }

        compose.onNodeWithText("20.12.–06.01.").assertIsDisplayed()
        compose.onNodeWithText("anwesend · Ferien").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ausnahme löschen").performClick()
        assertEquals(listOf<ProfileEditEvent>(ProfileEditEvent.RequestDelete(someException)), events)
    }

    @Test
    fun `REQ-MEAL-011 add dialog shows the server conflict message`() {
        val message = "Zeitraum überschneidet sich mit einer bestehenden Ausnahme"
        val state = editState(false) { copy(showAddException = true, addExceptionError = AppError.Conflict(message)) }
        show { ProfileEditScreen(state, {}, {}) }

        compose.onNodeWithText("Ausnahme anlegen").assertIsDisplayed()
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithText("Zeitraum wählen").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-011 delete confirmation explains the effect`() {
        show { ProfileEditScreen(editState(false) { copy(pendingDelete = someException) }, {}, {}) }

        compose.onNodeWithText("Ausnahme löschen?").assertIsDisplayed()
        compose.onNodeWithText("Danach gilt wieder das Muster.").assertIsDisplayed()
    }

    // --- Fristen ---

    @Test
    fun `REQ-MEAL-013 settings show current and scheduled settings`() {
        val scheduled =
            DeadlineSettings(
                java.time.DayOfWeek.SATURDAY,
                java.time.LocalTime.of(18, 0),
                java.time.LocalTime.of(16, 0),
                LocalDate.of(2026, 10, 26),
            )
        val state =
            DeadlineSettingsUiState(
                overview = DeadlineSettingsOverview(someSettings, scheduled),
                orderDay = scheduled.orderDay,
                orderTime = scheduled.orderTime,
                childTime = scheduled.childTime,
            )
        show { DeadlineSettingsScreen(state, {}, {}) }

        compose.onNodeWithText("Gilt jetzt: Sonntag, 16:00 · Kind-Frist 16:00").assertIsDisplayed()
        compose.onNodeWithText("Vorgemerkt ab Planwoche 26.10.: Samstag, 18:00 · Kind-Frist 16:00").assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-013 hint explains effect from the week after next`() {
        show { DeadlineSettingsScreen(DeadlineSettingsUiState(overview = DeadlineSettingsOverview(someSettings, null)), {}, {}) }

        compose
            .onNodeWithText(
                "Änderungen gelten ab der übernächsten Planwoche; die gerade geplante Woche behält ihre Frist.",
            ).assertIsDisplayed()
    }

    @Test
    fun `REQ-MEAL-013 forbidden save shows parents only on settings`() {
        val state = DeadlineSettingsUiState(overview = DeadlineSettingsOverview(someSettings, null), saveError = AppError.Forbidden)
        show { DeadlineSettingsScreen(state, {}, {}) }

        compose.onNodeWithText("Nur für Eltern.").assertIsDisplayed()
    }
}
