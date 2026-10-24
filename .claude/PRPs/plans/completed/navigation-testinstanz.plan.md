# Plan: Navigation & Testinstanz (TASK Phase 1)

## Summary
Die App bekommt eine untere Navigationsleiste mit vier rollenabhängigen Hauptbereichen (Heute · Essen · Aufgaben · Ich
bzw. Familie). Der Startbildschirm wird zur Seite „Heute“ mit dem heutigen Gericht, die bisherigen Essen-Buttons ziehen in
den Bereich „Essen“. Zusätzlich entsteht eine **Testinstanz** auf dem Pi (zweiter Container, eigene Daten, Port 8097) und
ein **Test-Build** der App, der neben der Familien-App installiert werden kann. Keine API- und keine Backend-Code-Änderung.

## User Story
Als Familienmitglied möchte ich die Bereiche der App über eine feste Leiste erreichen und auf „Heute“ sehen, was es heute
gibt, damit der Startbildschirm übersichtlich bleibt, wenn Aufgaben und Punkte dazukommen.
Als Entwickler möchte ich neue Features auf zwei Testhandys gegen eine eigene Testinstanz ausprobieren, ohne die Daten der
Familie zu berühren.

## Problem → Solution
Startbildschirm = Spalte aus Buttons, die jedes Feature-Modul ergänzt; neue Features landen direkt in der Familien-DB →
Material-3-`NavigationBar` mit festen Hauptbereichen, „Heute“ als Übersicht; getrennte Testinstanz + Test-Build.

## Metadata
- **Complexity**: Medium (≈ 20 Dateien, App + Deploy, keine neuen Bibliotheken)
- **Source PRD**: `docs/01-requirements/tasks.prd.md`
- **PRD Phase**: Phase 1 — Navigation & Testinstanz
- **Requirements**: REQ-TASK-001 (vollständig), REQ-TASK-002 (nur Essen-Inhalt; Aufgaben folgen in Phase 3)
- **Estimated Files**: ≈ 20

---

## Entscheidungen (aus PRD und Planung, 2026-10-24)

| Thema | Entscheidung | Begründung |
|---|---|---|
| Navigationsmuster | Material 3 `NavigationBar`, 4 Ziele | PRD-Decisions-Log (3–5 gleichrangige Bereiche, Daumen-erreichbar) |
| Ziele Kind | Heute · Essen · Aufgaben · Ich | REQ-TASK-001 |
| Ziele Eltern | Heute · Essen · Aufgaben · Familie | REQ-TASK-001; „Familie verwalten“ zieht vom Startbildschirm hierher |
| Sichtbarkeit der Leiste | **Nur auf den vier Hauptseiten**; Unterseiten (Essensplan, Rezept, Editor …) behalten ihren „Zurück“-Button und zeigen keine Leiste | Bestehende Screens bleiben unverändert (Zurück-Buttons, Tastatur in Editoren); kein verschachtelter Back-Stack je Tab nötig (KISS) |
| Bereich „Essen“ | Neue Übersichtsseite `MealsHomeRoute` in `feature:meals` mit den vier bisherigen Einstiegen | Kleinster Umbau; alle bestehenden Abläufe starten wie bisher, nur von hier statt vom Startbildschirm |
| Zustand beim Tab-Wechsel | `saveState`/`restoreState` + `launchSingleTop`, Start = `HomeRoute` | Standard von Navigation Compose; System-Zurück auf einer Hauptseite → „Heute“, auf „Heute“ → App verlassen |
| „Aufgaben“ / „Ich“ | Platzhalterseiten in `android/app` („Kommt bald“) | Inhalte kommen mit TASK Phase 3 bzw. GAME Phase 1; Platzhalter werden dann ersetzt |
| „Heute“ | `HomeScreen` (feature:auth) zeigt Begrüßung, Rolle, Offline-Hinweis und einen Slot; `feature:meals` liefert `TodayMealCard` | Bestehendes Slot-Muster (`homeActions`) beibehalten; `feature:auth` kennt `meals` nicht (ADR-0001) |
| Testinstanz | Zweiter Service `family-planner-test` im **selben** `docker-compose.yml`, Compose-Profil `test`, Port **8097**, Daten `./data-test`, Zusatz-Env `.env.test` | Gleiches `stable`-Image, ein Timer/`update.sh` für beide; Profil aus = heutiges Verhalten |
| Test-Build | Schalter `fp.testInstance=true` (bzw. `FP_TEST_INSTANCE`) → `applicationIdSuffix ".test"`, Name „Familienplaner Test“, Standard-URL `…:8097/` | Läuft neben der Familien-App, kein versehentliches Überschreiben; Zertifikat/Hostname gleich |
| ADRs | ADR-0014 „Hauptnavigation der App“, ADR-0015 „Testinstanz und Test-Build“ | Architekturentscheidungen werden als ADR festgehalten (CLAUDE.md) |

PRD-Anpassung (bereits mit diesem Plan erledigt): REQ-TASK-001 Kriterium 2 präzisiert auf „jeder Hauptbereich behält seinen
Zustand“, weil die Leiste nur auf Hauptseiten erscheint.

---

## UX Design

### Before
```
┌─────────────────────────────┐
│      Hallo Lea 👋           │
│         Kind                │
│  Weitere Funktionen folgen. │
│  [ Essensplan            ]  │
│  [ Anwesenheit & Fristen ]  │
│  [ Rezepte               ]  │
│  [ Einkaufsliste         ]  │
│  [ Familie verwalten     ]  │  ← nur Eltern
└─────────────────────────────┘
```

### After
```
Heute (Start)                    Essen
┌─────────────────────────────┐  ┌─────────────────────────────┐
│ Hallo Lea 👋 · Kind         │  │ Essen                       │
│ (Offline-Hinweis)           │  │ [ Essensplan            ]   │
│ ┌─────────────────────────┐ │  │ [ Einkaufsliste         ]   │
│ │ Heute gibt es           │ │  │ [ Rezepte               ]   │
│ │ Lauch-Carbonara         │ │  │ [ Anwesenheit & Fristen ]   │
│ │ Es kocht: Ben           │ │  │                             │
│ └─────────────────────────┘ │  │                             │
├─────────────────────────────┤  ├─────────────────────────────┤
│ ⌂Heute  ▦Essen  ✓Aufg.  ☺Ich│  │ ⌂Heute  ▦Essen  ✓Aufg.  ☺Ich│
└─────────────────────────────┘  └─────────────────────────────┘
Eltern: letztes Ziel „Familie“ (Mitglieder & Geräte, ohne Zurück-Button).
Unterseiten (z. B. Essensplan) ohne Leiste, mit „Zurück“ → Essen.
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| App-Start | Button-Spalte | „Heute“ mit Gericht des Tages + Leiste | Kein Gericht → „Heute ist noch nichts geplant“ |
| Essensplan öffnen | Button auf Start | Leiste „Essen“ → „Essensplan“ oder Tipp auf die Heute-Karte | Zurück → Essen |
| Familie verwalten (Eltern) | Button auf Start | Leiste „Familie“ | FamilyScreen ohne Zurück-Button als Hauptseite |
| Benachrichtigung tippen | öffnet Essensplan über Start | wechselt zu „Essen“, öffnet Essensplan | Zurück → Essen |
| Rezept teilen | öffnet Editor über Start | wechselt zu „Essen“, öffnet Editor | Zurück → Essen |
| System-Zurück auf Essen/Aufgaben/Ich/Familie | – | → Heute | auf Heute → App schließen |
| Aufgaben / Ich | – | Platzhalter „Kommt bald“ | |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `android/app/src/main/kotlin/de/familyplanner/android/app/AppNavHost.kt` | all (96) | Wird zur Scaffold+Leiste umgebaut; Deep-Link-Effekte erhalten |
| P0 | `android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/AuthNavigation.kt` | 47-56 | `mainGraph` mit Slot `homeActions` und `FamilyRoute` |
| P0 | `android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/ui/home/HomeScreen.kt` | all | Wird „Heute“ |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/MealsNavigation.kt` | 87-160 | `MealsHomeActions`, `mealsGraph`, Routen-Muster |
| P0 | `android/app/src/test/kotlin/de/familyplanner/android/app/AppNavHostTest.kt` | all | Koin-Setup + bestehende Navigationstests, werden erweitert |
| P1 | `android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/ui/home/HomeViewModel.kt` | all | `HomeUiState.canManageFamily` entfällt |
| P1 | `android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/ui/family/FamilyScreen.kt` | 38-60 | `onBack` wird optional |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/plan/UseCases.kt` | 22-60 | `LoadWeekPlanUseCase` (Cache-Fallback) für die Heute-Karte |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/plan/Models.kt` | 9-36, 78-105 | `DishStatus`, `PlannedDay`, `WeekPlanSnapshot`, `WeekPlanLoad` |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/MealsModule.kt` | ViewModel-Block | Koin-Registrierung neuer ViewModel |
| P1 | `android/feature/auth/src/test/kotlin/de/familyplanner/android/feature/auth/ui/ScreensTest.kt` | 70-90 | Home-Tests, die angepasst werden |
| P1 | `android/app/build.gradle.kts` | 1-60 | `config()`-Helfer, `serverUrl`, Version, Kover-Filter |
| P1 | `deploy/docker-compose.yml`, `deploy/update.sh`, `deploy/README.md` | all | Testinstanz |
| P2 | `android/app/src/main/AndroidManifest.xml` | 14 | `android:label` → Platzhalter |
| P2 | `android/app/src/main/res/xml/network_security_config.xml` | all | Pinning gilt je Hostname, unabhängig vom Port → keine Änderung nötig |
| P2 | `backend/Dockerfile` | 7-10 | Healthcheck intern auf 8096, Daten unter `/app/data` |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Bottom navigation | developer.android.com „Navigate with a navigation bar“ / Navigation Compose „Integrate with bottom nav bar“ | `navigate(route) { popUpTo(graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }`; ausgewählt über `destination.hierarchy.any { it.hasRoute(route::class) }` |
| Material 3 NavigationBar | m3.material.io/components/navigation-bar | 3–5 Ziele, Icon + Label immer sichtbar |
| Compose profiles | docs.docker.com/compose/how-tos/profiles | Service mit `profiles: [test]` startet nur, wenn `COMPOSE_PROFILES=test` (z. B. in `.env`) gesetzt ist |
| Compose env_file | docs.docker.com/compose/how-tos/environment-variables | Bei mehreren `env_file` überschreiben spätere Dateien frühere |
| AGP | developer.android.com „Configure build variants“ | `applicationIdSuffix`, `versionNameSuffix`, `manifestPlaceholders` in `defaultConfig` |

KEY_INSIGHT: `hasRoute(KClass)` (Navigation 2.8+) funktioniert mit den bestehenden `@Serializable`-Routen; Projekt nutzt 2.10.2.
APPLIES_TO: Task 4. GOTCHA: `findStartDestination()` liegt in `androidx.navigation.NavGraph.Companion` — Import
`androidx.navigation.NavGraph.Companion.findStartDestination`.

KEY_INSIGHT: `material-icons-core` ist schon im Katalog (`libs.compose.material.icons`), aber nicht in `:app`. Enthält `Home`,
`DateRange`, `CheckCircle`, `Face`, `AccountCircle` (alle `Icons.Filled`). APPLIES_TO: Task 4. GOTCHA: kein `material-icons-extended`
hinzufügen (groß, R8-Last); keine „Restaurant“-Ikone im Core-Set → `DateRange` für Essen.

---

## Patterns to Mirror

### NAMING_CONVENTION (Routen)
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/MealsNavigation.kt:34-35, 77-78
@Serializable
data object WeekPlanRoute

@Serializable
data object WeekOverviewRoute
```
Routen als `@Serializable data object XyzRoute` in der `*Navigation.kt` des Moduls; Composable-Einstieg `XyzRoute(...)` mit gleichem
Namen im `ui`-Paket (holt ViewModel per `koinViewModel`), reiner Screen `XyzScreen(state, …)` daneben. KDoc/Kommentare deutsch.

### SLOT_PATTERN (Modulgrenze auth ↔ meals)
```kotlin
// SOURCE: android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/AuthNavigation.kt:47-56
/** [homeActions] nimmt die Einstiege anderer Feature-Module auf (feature:auth kennt sie nicht). */
fun NavGraphBuilder.mainGraph(
    navController: NavHostController,
    homeActions: @Composable () -> Unit = {},
) {
    composable<HomeRoute> {
        HomeRoute(onManageFamily = { navController.navigate(FamilyRoute) }, homeActions = homeActions)
    }
    composable<FamilyRoute> { FamilyRoute(onBack = { navController.popBackStack() }) }
}
```

### INBOX_NAVIGATION_PATTERN (Deep-Links)
```kotlin
// SOURCE: android/app/src/main/kotlin/de/familyplanner/android/app/AppNavHost.kt:63-84
val shared by inbox.pending.collectAsStateWithLifecycle()
LaunchedEffect(shared) {
    shared?.let { text ->
        inbox.consume()
        navController.openRecipeImport(text)
    }
}
val openPlan by planRequests.pending.collectAsStateWithLifecycle()
LaunchedEffect(openPlan) {
    if (openPlan) {
        planRequests.consume()
        navController.navigate(WeekPlanRoute)
    }
}
```

### VIEWMODEL_PATTERN
```kotlin
// SOURCE: android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/ui/home/HomeViewModel.kt:18-48
data class HomeUiState(
    val session: Session? = null,
    val isOffline: Boolean = false,
) {
    val canManageFamily: Boolean get() = session?.role == Role.PARENT
}

class HomeViewModel(
    sessions: SessionStore,
    private val refreshMe: RefreshMeUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    init { ... refresh() }
```
Abgeleitete Flags als `get()` im UiState; Laden im `init`; `MutableStateFlow` + `update { it.copy(...) }`.

### USE_CASE / CACHE (Heute-Karte)
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/plan/UseCases.kt:22-40
class LoadWeekPlanUseCase(
    private val repository: MealPlanRepository,
    private val cache: PlanCache,
    private val sessions: SessionStore,
    private val clock: Clock = Clock.system(BerlinZone),
) {
    suspend operator fun invoke(weekStart: LocalDate): WeekPlanLoad { ... Fresh / Cached / Failed }
```
Woche über `mondayOf(date)` aus `feature.meals.domain.attendance` (genutzt in `DishEditViewModel.kt:147`). „Heute“ immer
`LocalDate.now(clock)` mit `Clock.system(BerlinZone)`; in Tests `Clock.fixed(…, BerlinZone)`.

### ERROR_HANDLING
```kotlin
// SOURCE: HomeViewModel.kt:42-46
val result = refreshMe()
// Unauthorized räumt der UnauthorizedInterceptor auf; hier zählt nur „Server nicht erreichbar“.
val offline = result is ApiResult.Failure && result.error.isConnectivityProblem()
```
Heute-Karte: `Fresh`/`Cached` → anzeigen (bei `Cached` ohne eigenen Hinweis, der Offline-Hinweis steht schon auf „Heute“);
`Failed` → Karte zeigt `stringResource(R.string.today_unavailable)`; nie abstürzen, kein Logging nötig.

### SCREEN_PATTERN
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/MealsNavigation.kt:92-111
@Composable
fun MealsHomeActions(...) {
    Button(onClick = onOpenPlan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_plan)) }
    OutlinedButton(onClick = onOpenWeek, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_attendance)) }
    ...
}
```
Karten wie `StatusCards.kt`: `Card(Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(CardPadding)) { Text(..., style = titleMedium) … } }`.

### KOIN_PATTERN
```kotlin
// SOURCE: AppNavHostTest.kt:177-215 (Testmodul) / MealsModule.kt (viewModel { … })
viewModel { WeekPlanViewModel(sessions, LoadWeekPlanUseCase(planRepository, planCache, sessions), …) }
```
Neues `viewModel { TodayMealViewModel(get()) }` in `MealsModule`; im `AppNavHostTest` ebenfalls registrieren (sonst Koin-Fehler
beim Rendern von „Heute“).

### TEST_STRUCTURE
```kotlin
// SOURCE: android/feature/auth/src/test/kotlin/de/familyplanner/android/feature/auth/ui/ScreensTest.kt:74-81
@Test
fun `REQ-AUTH-002 child sees no parent actions`() {
    show { HomeScreen(HomeUiState(session = aSession), onManageFamily = {}) }

    compose.onNodeWithText("Hallo Lea 👋").assertIsDisplayed()
    compose.onNodeWithText("Kind").assertIsDisplayed()
    compose.onNodeWithText("Familie verwalten").assertDoesNotExist()
}
```
Robolectric + `createComposeRule`, Fakes statt Mocks, `kotlin.test`-Asserts, REQ-ID am Anfang des Testnamens.

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `docs/02-architecture/adr/0014-app-main-navigation.md` | CREATE | Navigationsentscheidung |
| `docs/02-architecture/adr/0015-test-instance.md` | CREATE | Testinstanz + Test-Build |
| `android/feature/auth/.../AuthNavigation.kt` | UPDATE | `mainGraph`: Slot umbenennen, `FamilyRoute` als Hauptseite ohne Zurück |
| `android/feature/auth/.../ui/home/HomeScreen.kt` | UPDATE | „Heute“: ohne „Weitere Funktionen…“ und ohne Familie-Button; Slot `todayContent` |
| `android/feature/auth/.../ui/home/HomeViewModel.kt` | UPDATE | `canManageFamily` entfernen |
| `android/feature/auth/.../ui/family/FamilyScreen.kt` | UPDATE | `onBack: (() -> Unit)?` – Zurück-Button nur wenn gesetzt |
| `android/feature/auth/src/main/res/values/strings.xml` | UPDATE | `home_more`, `home_manage_family` entfernen |
| `android/feature/auth/src/test/.../ui/ScreensTest.kt` | UPDATE | Home-Tests an „Heute“ anpassen; FamilyScreen ohne Zurück |
| `android/feature/meals/.../MealsNavigation.kt` | UPDATE | `MealsHomeRoute` + `MealsHomeScreen` statt `MealsHomeActions`; `TodayMealCard`-Einstieg |
| `android/feature/meals/.../ui/today/TodayMealViewModel.kt` | CREATE | Lädt Plan der laufenden Woche, wählt heutigen Tag |
| `android/feature/meals/.../ui/today/TodayMealCard.kt` | CREATE | Karte „Heute gibt es“ (Route + reiner Screen) |
| `android/feature/meals/.../MealsModule.kt` | UPDATE | `viewModel { TodayMealViewModel(get()) }` |
| `android/feature/meals/src/main/res/values/strings.xml` | UPDATE | `meals_home_title`, `today_*` |
| `android/feature/meals/src/test/.../ui/TodayMealTest.kt` | CREATE | ViewModel- und Screen-Tests |
| `android/app/.../MainDestinations.kt` | CREATE | `TopLevelDestination`, Rollen-Liste, `navigateToTopLevel` |
| `android/app/.../AppNavigationBar.kt` | CREATE | `NavigationBar` + Sichtbarkeitslogik |
| `android/app/.../PlaceholderScreens.kt` | CREATE | „Aufgaben“ und „Ich“ (Kommt bald) + Routen |
| `android/app/.../AppNavHost.kt` | UPDATE | `Scaffold(bottomBar)`, Graphen, Deep-Links über „Essen“ |
| `android/app/src/main/res/values/strings.xml` | UPDATE | Leisten-Labels, Platzhaltertexte |
| `android/app/src/test/.../AppNavHostTest.kt` | UPDATE | Neue Navigationstests, `TodayMealViewModel` im Koin-Testmodul |
| `android/app/src/test/.../MainDestinationsTest.kt` | CREATE | Reine Tests der Ziel-Listen je Rolle |
| `android/app/build.gradle.kts` | UPDATE | Icons-Abhängigkeit, Test-Build-Schalter, Version 0.8.0 |
| `android/app/src/main/AndroidManifest.xml` | UPDATE | `android:label="${appLabel}"` |
| `deploy/docker-compose.yml` | UPDATE | Service `family-planner-test` (Profil `test`) |
| `deploy/update.sh` | UPDATE | Health-Prüfung für alle laufenden Container des Projekts |
| `deploy/README.md` | UPDATE | Abschnitt „Testinstanz“ |
| `android/README.md` | UPDATE | „Test-Build“ + Abnahme-Checkliste TASK Phase 1 |
| `docs/03-design/core.md` | UPDATE | Abschnitt „App-Navigation (TASK Phase 1)“ |
| `docs/04-verification/traceability.md` | UPDATE | Zeilen REQ-TASK-001/002 |
| `docs/01-requirements/tasks.prd.md` | UPDATE | Bericht-Verweis nach Umsetzung |

## NOT Building

- Kein Modul `feature/tasks` und kein Backend-Modul `tasks` (Phase 2/3).
- Kein Abnahme-Badge an „Aufgaben“ (REQ-TASK-003, Phase 3) und keine Aufgaben auf „Heute“ (Phase 3).
- Keine Leiste auf Unterseiten, kein verschachtelter Back-Stack je Tab, keine Umgestaltung der bestehenden Essen-Screens.
- Keine Änderung an `api/`, `backend/` (Gegenprobe: `git diff main -- api/ backend/` leer).
- Keine eigene CI-Pipeline oder eigenes Image-Tag für die Testinstanz — sie nutzt `stable`.
- Kein Navigation Drawer, keine Einstellungsseite.
- Kein Umzug von „Anwesenheit & Fristen“ oder „Profile“ in „Familie“ (bleibt unter Essen, wie bisher für alle sichtbar).

---

## Step-by-Step Tasks

### Task 1: ADRs
- **ACTION**: `docs/02-architecture/adr/0014-app-main-navigation.md` und `0015-test-instance.md` nach `0000-template.md` anlegen.
- **IMPLEMENT**:
  - 0014: Kontext (Button-Spalte wächst; TASK/GAME kommen), Entscheidung (NavigationBar, 4 Ziele je Rolle, nur auf
    Hauptseiten, Slots statt Modulabhängigkeiten, `saveState`/`restoreState`), Alternativen (Drawer, Kachel-Start, Leiste
    überall mit verschachtelten Graphen), Folgen (Platzhalter werden in TASK 3 / GAME 1 ersetzt; neue Hauptbereiche nur per ADR).
  - 0015: Kontext (Test nur auf zwei Testhandys, Familie nutzt dasselbe Backend), Entscheidung (Compose-Profil `test`, Port 8097,
    `./data-test`, `.env.test`, gleiches `stable`-Image; App-Schalter `fp.testInstance`), Alternativen (Testkind in der
    Familien-DB, eigenes Image-Tag, zweiter Pi), Folgen (Backend-Änderungen gehen weiterhin sofort auf beide Instanzen → API nur
    additiv; Rollback in `update.sh` betrifft beide Container).
- **MIRROR**: Aufbau von `0013-local-reminders-and-status-sync.md`.
- **VALIDATE**: Status „akzeptiert“, Datum 2026-10-24, Verweise auf ADR-0001, 0005, 0006, 0009.

### Task 2: „Heute“ in `feature:auth`
- **ACTION**: `HomeScreen`, `HomeViewModel`, `AuthNavigation.mainGraph`, `FamilyScreen` anpassen.
- **IMPLEMENT**:
  - `HomeUiState`: `canManageFamily` entfernen.
  - `HomeScreen(state, todayContent: @Composable () -> Unit = {})`: `Column` mit `verticalScroll`, oben ausgerichtet
    (`Arrangement.spacedBy(16.dp)`, **nicht** mehr vertikal zentriert), Begrüßung, Rolle, Offline-Hinweis, dann `todayContent()`.
    `home_more` und den Familie-Button entfernen; `HomeRoute(todayContent)` entsprechend.
  - `mainGraph(navController, todayContent)`: `composable<HomeRoute> { HomeRoute(todayContent = todayContent) }`,
    `composable<FamilyRoute> { FamilyRoute(onBack = null) }` (Hauptseite). KDoc des Slots: „nimmt die Inhalte anderer
    Feature-Module für „Heute“ auf (feature:auth kennt sie nicht)“.
  - `FamilyRoute(onBack: (() -> Unit)? = null)`, `FamilyScreen(..., onBack: (() -> Unit)?)`: Zurück-Button nur `if (onBack != null)`.
  - Strings `home_more`, `home_manage_family` löschen (Lint `UnusedResources`).
- **MIRROR**: SLOT_PATTERN, VIEWMODEL_PATTERN.
- **GOTCHA**: `FamilyRoute` wird weiter auch für Kinder per Deep-Link erreichbar sein? Nein — das Ziel „Familie“ erscheint nur für
  Eltern; der Server liefert Kindern ohnehin 403 (bestehender Test `forbidden error is shown to children …` bleibt).
- **VALIDATE**: `ScreensTest` angepasst: `REQ-TASK-002 home shows greeting, role and today slot` (Slot rendert Testtext),
  `REQ-TASK-002 home shows offline hint`, `REQ-AUTH-002 child sees no parent actions` (ohne „Familie verwalten“ – Text existiert
  nicht mehr), `family screen as main page has no back button`.

### Task 3: „Essen“-Übersicht und Heute-Karte in `feature:meals`
- **ACTION**: `MealsHomeActions` durch `MealsHomeRoute`/`MealsHomeScreen` ersetzen; `TodayMealViewModel` + `TodayMealCard` anlegen.
- **IMPLEMENT**:
  - `MealsNavigation.kt`: `@Serializable data object MealsHomeRoute`. Im `mealsGraph`:
    `composable<MealsHomeRoute> { MealsHomeScreen(onOpenPlan = { navController.navigate(WeekPlanRoute) }, onOpenWeek = { … WeekOverviewRoute },
    onOpenRecipes = { … RecipeListRoute() }, onOpenShopping = { … ShoppingListRoute() }) }`.
    `MealsHomeScreen`: Titel `meals_home_title` („Essen“, `headlineMedium`), darunter die vier Buttons in der Reihenfolge
    **Essensplan (Button), Einkaufsliste, Rezepte, Anwesenheit & Fristen (OutlinedButton)**, Padding 24.dp, oben ausgerichtet.
  - `fun NavHostController.openWeekPlan() = navigate(WeekPlanRoute) { launchSingleTop = true }` neben `openRecipeImport` (für `app`).
  - `ui/today/TodayMealViewModel.kt`:
    ```kotlin
    /** Gericht des heutigen Tages für „Heute“ (REQ-TASK-002); offline aus dem Plan-Cache (ADR-0011). */
    data class TodayMealUiState(
        val isLoading: Boolean = true,
        val day: PlannedDay? = null,
        val unavailable: Boolean = false,
    )
    class TodayMealViewModel(
        private val loadWeekPlan: LoadWeekPlanUseCase,
        private val clock: Clock = Clock.system(BerlinZone),
    ) : ViewModel() { init { load() } … }
    ```
    `load()`: `today = LocalDate.now(clock)`, `when (val r = loadWeekPlan(mondayOf(today)))`: `Fresh`/`Cached` →
    `day = r.week.days.firstOrNull { it.date == today }`; `Failed` → `unavailable = true`.
    Geladen wird nur im `init` (kein öffentliches `refresh()`, YAGNI).
  - `ui/today/TodayMealCard.kt`: `@Composable fun TodayMealRoute(onOpenPlan: () -> Unit)` (koinViewModel) und reiner
    `TodayMealCard(state, onOpenPlan)`: `Card(onClick = onOpenPlan, Modifier.fillMaxWidth())`, Titel `today_title`
    („Heute gibt es“), Inhalt:
    - `isLoading` → nichts außer Titel (kein Spinner nötig),
    - `unavailable` → `today_unavailable` („Essensplan gerade nicht erreichbar.“),
    - `day?.dish == null` oder `dish.status == REJECTED` → `today_nothing` („Heute ist noch nichts geplant.“),
    - sonst `dish.name` (`titleLarge`), `today_cook` („Es kocht: %1$s“, `cookName ?: "?"`), bei `SUBMITTED` zusätzlich
      `today_pending` („Noch nicht freigegeben.“).
  - `MealsModule`: `viewModel { TodayMealViewModel(get()) }`.
  - Strings in `feature/meals/.../values/strings.xml`: `meals_home_title`, `today_title`, `today_nothing`, `today_unavailable`,
    `today_cook`, `today_pending`. `home_plan`, `home_attendance`, `home_recipes`, `home_shopping` bleiben (werden weiter benutzt).
- **MIRROR**: NAMING_CONVENTION, USE_CASE / CACHE, SCREEN_PATTERN, KOIN_PATTERN.
- **IMPORTS**: `de.familyplanner.android.feature.meals.domain.attendance.mondayOf`, `de.familyplanner.android.core.ui.BerlinZone`,
  `java.time.Clock`, `java.time.LocalDate`.
- **GOTCHA**: `LoadWeekPlanUseCase` braucht eine Session; ohne Session → `Failed(Unauthorized)` → `unavailable` (kommt in der
  App nicht vor, weil „Heute“ nur angemeldet erscheint). Plan-Cache hält nur die gespeicherten Wochen; am Montag ohne Netz und
  ohne Sync der neuen Woche → `unavailable` (korrekt).
- **VALIDATE**: `TodayMealTest` (siehe Testing Strategy) grün.

### Task 4: Leiste und Hauptbereiche in `android/app`
- **ACTION**: `MainDestinations.kt`, `AppNavigationBar.kt`, `PlaceholderScreens.kt` anlegen; `AppNavHost.MainHost` umbauen.
- **IMPLEMENT**:
  - `PlaceholderScreens.kt`: `@Serializable data object TasksRoute`, `@Serializable data object MeRoute`;
    `@Composable fun ComingSoonScreen(title: String, text: String)` (Titel `headlineMedium`, Text, Padding 24.dp).
  - `MainDestinations.kt`:
    ```kotlin
    /** Hauptbereiche der unteren Leiste (ADR-0014); Reihenfolge = Anzeige. */
    enum class TopLevelDestination(val route: Any, @StringRes val label: Int, val icon: ImageVector) {
        TODAY(HomeRoute, R.string.nav_today, Icons.Filled.Home),
        MEALS(MealsHomeRoute, R.string.nav_meals, Icons.Filled.DateRange),
        TASKS(TasksRoute, R.string.nav_tasks, Icons.Filled.CheckCircle),
        ME(MeRoute, R.string.nav_me, Icons.Filled.Face),
        FAMILY(FamilyRoute, R.string.nav_family, Icons.Filled.AccountCircle),
    }

    fun topLevelDestinations(role: Role): List<TopLevelDestination> =
        listOf(TODAY, MEALS, TASKS, if (role == Role.PARENT) FAMILY else ME)

    /** Wechselt den Hauptbereich; jeder Bereich behält seinen Zustand, Zurück führt zu „Heute“. */
    fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) =
        navigate(destination.route) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }

    fun NavDestination?.topLevelOf(destinations: List<TopLevelDestination>): TopLevelDestination? =
        destinations.firstOrNull { d -> this?.hierarchy?.any { it.hasRoute(d.route::class) } == true }
    ```
  - `AppNavigationBar.kt`: `@Composable fun AppNavigationBar(destinations, selected, onSelect)` mit `NavigationBar { destinations.forEach { NavigationBarItem(selected = it == selected, onClick = { onSelect(it) }, icon = { Icon(it.icon, contentDescription = null) }, label = { Text(stringResource(it.label)) }) } }`.
  - `AppNavHost`: `SessionState.SignedIn` → `MainHost(role = state.session.role)`. In `MainHost`:
    ```kotlin
    val destinations = remember(role) { topLevelDestinations(role) }
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination.topLevelOf(destinations)
    Scaffold(bottomBar = { current?.let { AppNavigationBar(destinations, it) { d -> navController.navigateToTopLevel(d) } } }) { padding ->
        NavHost(navController, startDestination = HomeRoute, modifier = Modifier.padding(padding)) {
            mainGraph(navController) { TodayMealRoute(onOpenPlan = { navController.openMealsAndThen { openWeekPlan() } }) }
            mealsGraph(navController)
            composable<TasksRoute> { ComingSoonScreen(stringResource(R.string.nav_tasks), stringResource(R.string.tasks_coming_soon)) }
            composable<MeRoute> { ComingSoonScreen(stringResource(R.string.nav_me), stringResource(R.string.me_coming_soon)) }
        }
    }
    ```
    Hilfsfunktion in `MainDestinations.kt`: `fun NavHostController.openMealsAndThen(open: NavHostController.() -> Unit) { navigateToTopLevel(TopLevelDestination.MEALS); open() }`.
    Deep-Links: `planRequests` → `navController.openMealsAndThen { openWeekPlan() }`; `inbox` → `navController.openMealsAndThen { openRecipeImport(text) }`.
  - `MainActivity` bleibt (Surface mit `safeDrawingPadding`); Scaffold-`contentWindowInsets` auf `WindowInsets(0)` setzen, weil
    die Insets schon außen verbraucht sind (sonst doppelter Abstand unten).
  - Strings `android/app/.../values/strings.xml`: `nav_today` „Heute“, `nav_meals` „Essen“, `nav_tasks` „Aufgaben“, `nav_me` „Ich“,
    `nav_family` „Familie“, `tasks_coming_soon` „Hier entsteht das Aufgaben-Board.“, `me_coming_soon` „Hier findest du bald
    Punkte, Badges und deinen Avatar.“
  - `app/build.gradle.kts`: `implementation(libs.compose.material.icons)`.
- **MIRROR**: INBOX_NAVIGATION_PATTERN, NAMING_CONVENTION.
- **IMPORTS**: `androidx.navigation.NavGraph.Companion.findStartDestination`, `androidx.navigation.NavDestination.Companion.hasRoute`,
  `androidx.navigation.NavDestination.Companion.hierarchy`, `androidx.navigation.compose.currentBackStackEntryAsState`,
  `androidx.compose.material.icons.Icons`, `…icons.filled.*`, `de.familyplanner.android.feature.auth.FamilyRoute`,
  `de.familyplanner.android.feature.meals.MealsHomeRoute`.
- **GOTCHA**:
  - `route: Any` in einem Enum: `HomeRoute`/`FamilyRoute`/… sind `data object`s → `d.route::class` liefert die richtige KClass.
  - `navigateToTopLevel(MEALS)` gefolgt von `navigate(WeekPlanRoute)` im selben Frame ist erlaubt (Back-Stack: Home → MealsHome → WeekPlan).
    Mit `restoreState = true` kann ein gespeicherter Essen-Stack wiederhergestellt werden, der schon `WeekPlanRoute` enthält.
    Deshalb `fun NavHostController.openWeekPlan() = navigate(WeekPlanRoute) { launchSingleTop = true }`, sonst entstehen zwei
    Essensplan-Einträge übereinander.
  - Session-Wechsel (abmelden/neu koppeln mit anderer Rolle): `MainHost` wird mit neuer `role` neu aufgebaut; `rememberNavController()`
    liegt in `MainHost` und wird beim Wechsel `SignedOut → SignedIn` ohnehin neu erzeugt.
  - Kover: `MainDestinations`/`AppNavigationBar` sind testbar und **nicht** ausschließen; `PlaceholderScreens` fällt unter `*Route*` nicht —
    durch `AppNavHostTest` abdecken.
- **VALIDATE**: `MainDestinationsTest` + erweiterter `AppNavHostTest` grün.

### Task 5: Test-Build der App
- **ACTION**: Schalter in `app/build.gradle.kts`, Manifest-Platzhalter.
- **IMPLEMENT**:
  ```kotlin
  // ADR-0015: Test-Build gegen die Testinstanz; installierbar neben der Familien-App.
  val testInstance = config("fp.testInstance", "FP_TEST_INSTANCE").toBoolean()
  val defaultServerUrl = if (testInstance) "https://planner.home.arpa:8097/" else "https://planner.home.arpa:8095/"
  val serverUrl = config("fp.serverUrl", "FP_SERVER_URL") ?: defaultServerUrl
  …
  defaultConfig {
      versionCode = 9
      versionName = "0.8.0"
      manifestPlaceholders["appLabel"] = if (testInstance) "Familienplaner Test" else "@string/app_name"
      if (testInstance) {
          applicationIdSuffix = ".test"
          versionNameSuffix = "-test"
      }
  }
  ```
  Manifest: `android:label="${appLabel}"`.
- **GOTCHA**: `config(...)` liefert `String?` → `.toBoolean()` auf `String?` ist in Kotlin `false` bei `null` (Erweiterung
  `String?.toBoolean()`); detekt-Regel prüfen. `namespace` bleibt `de.familyplanner.android` (nur `applicationId` ändert sich) —
  `BuildConfig`/`R` unverändert. `PlanOpenRequests`-Intents sind explizit (Activity-Klasse), keine Authorities im Manifest → keine
  Kollision zwischen beiden Apps.
- **VALIDATE**: `FP_TEST_INSTANCE=true ./gradlew :app:assembleRelease` (der Schalter liest `local.properties` oder Env, kein
  `-P`); `aapt2 dump badging app/build/outputs/apk/release/app-release.apk | head -3` zeigt
  `package: name='de.familyplanner.android.test'` und Label „Familienplaner Test“. Ohne Schalter unverändert `de.familyplanner.android`.

### Task 6: Testinstanz auf dem Pi
- **ACTION**: `deploy/docker-compose.yml`, `deploy/update.sh`, `deploy/README.md`.
- **IMPLEMENT**:
  - Compose:
    ```yaml
      # ADR-0015: Testinstanz für die Testhandys; startet nur mit COMPOSE_PROFILES=test (in .env).
      family-planner-test:
        image: ghcr.io/matthias1309/family-planner:stable
        container_name: family-planner-test
        profiles: [test]
        init: true
        restart: unless-stopped
        ports:
          - "8097:8095"
        env_file: [.env, .env.test]
        environment:
          FP_TLS_ENABLED: "true"
        volumes:
          - ./data-test:/app/data
          - ./certs:/app/certs:ro
    ```
  - `update.sh`: `CONTAINER` durch die Liste der laufenden Container ersetzen und auf **alle** `healthy` warten:
    ```bash
    containers() { docker compose ps --format '{{.Name}}'; }
    all_healthy() {
      local name
      for name in $(containers); do
        [ "$(docker inspect --format '{{.State.Health.Status}}' "$name" 2>/dev/null || echo missing)" = "healthy" ] || return 1
      done
    }
    ```
    Warteschleife: `all_healthy && { healthy=true; break; }`. Version weiter aus `family-planner` lesen (Konstante
    `MAIN_CONTAINER="family-planner"`). Rollback unverändert (betrifft beide, gleiches Image). `COMPOSE_PROFILES` liest
    `docker compose` selbst aus `.env` — `update.sh` muss nichts tun.
  - README, neuer Abschnitt „## Testinstanz (optional, ADR-0015)“:
    1. `sudo mkdir -p data-test && sudo chown 10001 data-test`
    2. `.env.test` mit eigenem `FP_SETUP_TOKEN=` (`openssl rand -hex 16`), `chmod 600`
    3. In `.env`: `COMPOSE_PROFILES=test`
    4. `docker compose up -d` → `curl -k https://planner.home.arpa:8097/health`
    5. Familie im Test-Build einrichten („Familie einrichten“ mit dem Test-Token), Testkind anlegen, zweites Handy koppeln
    6. Abschalten: `COMPOSE_PROFILES` entfernen, `docker compose up -d --remove-orphans`
    Hinweis: „Nur Port 8095 wird veröffentlicht“ → „8095 (Familie) und optional 8097 (Test)“. Uptime Kuma optional zweiter Monitor.
  - `deploy/.env.example` **nicht** anfassen (Geheimnis-Vorlage). `.env.test` ist schon durch `.gitignore:22` (`.env.*`)
    ausgeschlossen (geprüft 2026-10-24); `data-test/` existiert nur auf dem Pi.
- **GOTCHA**: Port 8097 ist frei (belegt: 53, 1883, 2222, 3000–3002, 8000, 8080, 8081, 8086, 8090, 8123, 8443, 8095). Der interne
  Health-Port 8096 wird in keinem Container veröffentlicht → keine Kollision. Backups landen getrennt in `data-test/backups/`.
- **VALIDATE**: `docker compose -f deploy/docker-compose.yml config` (lokal, mit Dummy-`.env`/`.env.test` in einem Temp-Ordner) zeigt
  ohne Profil 1 Service, mit `COMPOSE_PROFILES=test` 2 Services; `bash -n deploy/update.sh`; `shellcheck deploy/update.sh`, falls installiert.

### Task 7: Tests anpassen und ergänzen
- **ACTION**: `AppNavHostTest`, `ScreensTest` (auth), neue `MainDestinationsTest`, `TodayMealTest`.
- **IMPLEMENT**: siehe Testing Strategy. Im `AppNavHostTest` das Koin-Modul um
  `viewModel { TodayMealViewModel(LoadWeekPlanUseCase(planRepository, planCache, sessions)) }` ergänzen. Bestehende Tests:
  - `with session the home screen offers attendance` / `… offers the meal plan` → umschreiben: Tipp auf „Essen“ in der Leiste, dann
    „Anwesenheit & Fristen“ bzw. „Essensplan“ sichtbar (Namen mit `REQ-TASK-001` voranstellen).
  - `REQ-MEAL-050 notification tap opens the meal plan …`: weiterhin „Zurück“ sichtbar; zusätzlich: nach Klick auf „Zurück“ ist
    „Essensplan“ (Button der Essen-Übersicht) sichtbar.
- **GOTCHA**: „Essensplan“ kommt nach dem Umbau auf „Heute“ nicht mehr vor, aber „Essen“ (Leiste) und „Heute gibt es“ (Karte) —
  `onNodeWithText("Essen")` ist eindeutig, `onNodeWithText("Heute")` kollidiert mit „Heute gibt es“ → `useUnmergedTree`/`substring = false`
  (Standard ist exakt) reicht; bei Mehrdeutigkeit `onAllNodesWithText(...)[0]` vermeiden, lieber `onNode(hasText("Heute") and hasClickAction())`.
- **VALIDATE**: `./gradlew :app:testDebugUnitTest :feature:auth:testDebugUnitTest :feature:meals:testDebugUnitTest`.

### Task 8: Version, Doku, Traceability, Abnahme-Checkliste
- **ACTION**: Abschluss.
- **IMPLEMENT**:
  - Version 0.8.0 / 9 (Task 5).
  - `docs/03-design/core.md`: Abschnitt „## App-Navigation (TASK Phase 1)“ — Hauptbereiche je Rolle, Sichtbarkeit der Leiste,
    Slots (`todayContent`), Deep-Links über „Essen“, Test-Build-Schalter; Unit-Testfälle.
  - `docs/04-verification/traceability.md`: Zeilen
    `| REQ-TASK-001 | Untere Navigationsleiste | [core](../03-design/core.md), [ADR-0014](../02-architecture/adr/0014-app-main-navigation.md) | App: `MainDestinationsTest` | App: `AppNavHostTest` | manuell: Abnahme TASK Phase 1 ([android/README.md](../../android/README.md)) | umgesetzt (App) |`
    `| REQ-TASK-002 | Startseite „Heute“ | [core](../03-design/core.md) | App: `TodayMealTest`, `ScreensTest` (auth) | App: `AppNavHostTest` | manuell: Abnahme TASK Phase 1 | teilweise umgesetzt (Essen; Aufgaben folgen in Phase 3) |`
  - `android/README.md`: Abschnitt „## Test-Build (ADR-0015)“ (`FP_TEST_INSTANCE=true ./gradlew assembleRelease`, Installation neben der
    Familien-App, Kopplung gegen die Testinstanz) und „## Abnahme-Checkliste TASK Phase 1 (manuell, Testhandys gegen die Testinstanz)“ =
    Manual Validation unten.
  - `docs/01-requirements/tasks.prd.md`: Phase 1 „PRP Plan“-Spalte um Bericht ergänzen (Status bleibt `in-progress` bis Geräte-Abnahme).
- **VALIDATE**: Full Test Suite.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `REQ-TASK-001 child gets today, meals, tasks and me` (`MainDestinationsTest`) | `Role.CHILD` | `[TODAY, MEALS, TASKS, ME]` | |
| `REQ-TASK-001 parent gets family instead of me` | `Role.PARENT` | `[TODAY, MEALS, TASKS, FAMILY]` | |
| `REQ-TASK-001 sub pages belong to no main area` | Destination mit Route `WeekPlanRoute` | `topLevelOf(...) == null` | ja |
| `REQ-TASK-002 today shows the dish of the current day` (`TodayMealTest`, VM) | `Clock.fixed` Mi 2026-10-28, Plan mit Gericht am 28. | `day.dish.name == "Lauch-Carbonara"` | |
| `REQ-TASK-002 today without dish shows nothing planned` | Tag ohne Gericht | Karte „Heute ist noch nichts geplant.“ | |
| `REQ-TASK-002 rejected child dish counts as nothing planned` | `DishStatus.REJECTED` | „Heute ist noch nichts geplant.“ | ja |
| `REQ-TASK-002 submitted dish is marked as not yet approved` | `DishStatus.SUBMITTED` | Name + „Noch nicht freigegeben.“ | ja |
| `REQ-TASK-002 cached plan is shown when offline` | Repo `AppError.Network`, Cache hat Woche | Gericht sichtbar | ja |
| `REQ-TASK-002 unreachable plan without cache says unavailable` | Repo `Network`, Cache leer | „Essensplan gerade nicht erreichbar.“ | ja |
| `REQ-TASK-002 sunday belongs to the week starting monday` | `Clock.fixed` So 2026-11-01 | lädt Woche 2026-10-26 | ja |
| `REQ-TASK-002 home shows greeting, role and today slot` (auth `ScreensTest`) | Kind-Session, Slot „X“ | „Hallo Lea 👋“, „Kind“, „X“; kein „Familie verwalten“ | |
| `family screen as main page has no back button` | `onBack = null` | „Zurück“ existiert nicht | |
| `REQ-TASK-001 child sees four main areas` (`AppNavHostTest`) | Kind-Session | „Heute“, „Essen“, „Aufgaben“, „Ich“ sichtbar; „Familie“ nicht | |
| `REQ-TASK-001 parent sees family as main area` | Eltern-Session | „Familie“ sichtbar, Tipp → Familien-Screen ohne „Zurück“ | |
| `REQ-TASK-001 meals area offers plan, shopping, recipes and attendance` | Tipp „Essen“ | vier Buttons sichtbar | |
| `REQ-TASK-001 sub page hides the bar and back returns to meals` | Essen → Essensplan → Zurück | Leiste fehlt auf Essensplan; danach Essen-Übersicht | ja |
| `REQ-TASK-001 tasks and me show coming soon` | Tipp „Aufgaben“ / „Ich“ | Platzhaltertexte | |
| `REQ-MEAL-050 notification tap opens the meal plan …` (angepasst) | `planRequests.request()` | Essensplan, Zurück → Essen-Übersicht | |
| `REQ-MEAL-061 shared link opens the recipe editor …` (unverändert grün) | Inbox | „Neues Rezept“ | |
| `session switch moves between welcome and home` (unverändert grün) | | | |

Fakes: im `TodayMealTest` die vorhandenen Meals-Fakes (`fakes/`, z. B. `FakeMealPlanRepository`, In-Memory-`PlanCache`) wiederverwenden;
fehlt ein In-Memory-`PlanCache`, als kleines `object : PlanCache` im Test wie in `AppNavHostTest`.

### Edge Cases Checklist
- [x] Kein Gericht / abgelehntes Gericht / noch nicht freigegeben
- [x] Offline mit und ohne Cache
- [x] Sonntag (Wochenzuordnung)
- [x] Unterseite → keine Leiste, Zurück zur Übersicht
- [x] Deep-Link (Benachrichtigung, Teilen) bei geöffneter App und bei Kaltstart
- [x] Rollenwechsel durch Neu-Koppeln
- [ ] Testinstanz ohne `.env.test` → Compose bricht ab (gewollt, README erwähnt es)

---

## Validation Commands

### Static Analysis
```bash
cd android && ./gradlew ktlintCheck detekt lintDebug
```
EXPECT: keine Funde

### Unit Tests
```bash
cd android && ./gradlew :app:testDebugUnitTest :feature:auth:testDebugUnitTest :feature:meals:testDebugUnitTest
```
EXPECT: alle grün

### Full Test Suite (wie CI)
```bash
cd android && ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerifyDebug assembleDebug assembleRelease
```
EXPECT: grün, Kover ≥ 80 %; Release (R8) baut

### Test-Build
```bash
cd android && FP_TEST_INSTANCE=true ./gradlew :app:assembleRelease
```
EXPECT: APK mit `applicationId` `de.familyplanner.android.test`, Label „Familienplaner Test“, Server `…:8097/`

### Deploy-Dateien
```bash
bash -n deploy/update.sh
```
EXPECT: keine Syntaxfehler; `docker compose config` mit/ohne `COMPOSE_PROFILES=test` zeigt 2 bzw. 1 Service

### Database Validation
N/A — keine Schemaänderung. Gegenprobe: `git diff main -- api/ backend/` ist leer.

### Manual Validation (Abnahme TASK Phase 1 – zwei Testhandys gegen die Testinstanz)
- [ ] Pi: Testinstanz nach README eingerichtet, `https://planner.home.arpa:8097/health` antwortet; Familien-Instanz (8095) läuft unverändert
- [ ] Nach dem nächsten `stable`-Update starten beide Container neu und sind `healthy` (`journalctl -u family-planner-update.service`)
- [ ] Test-Build 0.8.0-test auf beiden Testhandys installiert, **neben** der Familien-App (zwei Symbole, „Familienplaner Test“)
- [ ] Testhandy 1: „Familie einrichten“ mit Test-Token, Testkind anlegen; Testhandy 2 als Testkind koppeln
- [ ] Kind: Leiste Heute · Essen · Aufgaben · Ich; Eltern: Heute · Essen · Aufgaben · Familie
- [ ] „Heute“ zeigt das heutige Gericht (vorher in der Testinstanz eins planen) bzw. „noch nichts geplant“; Tipp öffnet den Essensplan
- [ ] Essen → Essensplan, Einkaufsliste, Rezepte, Anwesenheit & Fristen funktionieren wie bisher; „Zurück“ führt zur Essen-Übersicht
- [ ] System-Zurück auf Essen/Aufgaben/Ich/Familie → Heute; auf Heute → App schließt
- [ ] Rezept aus dem Browser teilen → Test-App öffnet den Editor (Auswahl „Familienplaner Test“ im Teilen-Menü)
- [ ] Benachrichtigung (z. B. Freigabe) tippen → Essensplan
- [ ] Offline (WLAN aus): „Heute“ zeigt Offline-Hinweis und das Gericht aus dem Cache
- [ ] Familien-App auf einem Familienhandy unverändert (kein Testgericht sichtbar)

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt, alle Validation Commands grün
- [ ] REQ-TASK-001 und REQ-TASK-002 (Essen-Teil) haben Tests mit REQ-ID im Namen
- [ ] Leiste nur auf Hauptseiten, Ziele je Rolle wie oben; Deep-Links funktionieren
- [ ] Test-Build installierbar neben der Familien-App; Testinstanz mit getrennten Daten
- [ ] `api/` und `backend/` unverändert; einzige neue Abhängigkeit von `:app` ist `material-icons-core` (schon im Katalog)
- [ ] ADR-0014/0015, Design, Traceability, README-Checklisten aktualisiert

## Completion Checklist
- [ ] Code folgt den Mustern (Routen als `data object`, Route/Screen-Trennung, Slots statt Modulabhängigkeiten, State-ViewModel)
- [ ] Fehlerbehandlung: Heute-Karte zeigt „nicht erreichbar“ statt Fehler; nie Absturz
- [ ] Kein Logging nötig; keine Inhalte/Token geloggt
- [ ] Tests nach den bestehenden Mustern (Robolectric, Fakes, `runTest`, feste `Clock`)
- [ ] Keine Magic Numbers/Strings (Ports in Build-Datei/Compose kommentiert, Texte in `strings.xml`)
- [ ] Funktionen < 50 Zeilen, Dateien < 400 Zeilen (`AppNavHost.kt` aufgeteilt)
- [ ] Kein Scope-Zuwachs (siehe NOT Building)

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Doppelter Inset-Abstand unten (Surface `safeDrawingPadding` + Scaffold) | M | L | `contentWindowInsets = WindowInsets(0)`; auf Gerät prüfen |
| Deep-Link bei Kaltstart navigiert, bevor der Graph steht | L | M | Bestehende `LaunchedEffect`-Logik beibehalten (lief bisher); Test `REQ-MEAL-050 …` |
| `restoreState` stellt einen alten Essen-Stack wieder her und `openWeekPlan` stapelt doppelt | M | L | `launchSingleTop` in `openWeekPlan()`; Test „Zurück → Essen-Übersicht“ |
| `update.sh`-Änderung bricht Familien-Updates | L | H | `bash -n`, kleiner Diff, gleicher Ablauf für den Fall „nur ein Container“; Abnahme prüft Update-Log |
| Gleiches Image → ein kaputtes Image trifft beide Instanzen | L | M | Bestehender Rollback; Testinstanz soll Daten trennen, nicht Versionen |
| Ein Testhandy hat noch die Familien-App mit Familien-Session | M | L | Test-App hat eigene `applicationId` und eigene Session; Familien-App bleibt unberührt |

## Notes
- Die Leiste nur auf Hauptseiten zu zeigen, ist eine bewusste Vereinfachung; falls die Tests auf den Testhandys zeigen, dass sie auf
  Unterseiten fehlt, kann sie später über verschachtelte Graphen je Tab (ADR-0014, Alternative 3) überall gezeigt werden.
- TASK Phase 2 (Board Backend) kann parallel laufen: berührt nur `api/`, `backend/` und ein neues ADR (0016 Domänenereignisse).
  Achtung ADR-Nummern: dieser Plan belegt 0014 und 0015.
- Umsetzung laut CLAUDE.md in einer **neuen Session** mit `/ecc:prp-implement .claude/PRPs/plans/navigation-testinstanz.plan.md`.
