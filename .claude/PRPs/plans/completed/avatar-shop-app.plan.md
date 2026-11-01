# Plan: Avatar & Shop – App (GAME Phase 4b)

## Summary
Die App bekommt einen kombinierten Bildschirm „Avatar & Shop“ (Vorschau, Rubriken je Slot, kostenlose und kaufbare Teile,
Kauf mit Bestätigung und Sparziel-Hinweis, Speichern), einen Avatar-Bereich auf „Ich“ und „Heute“ (Kind) sowie Avatare in
Rangliste (Ich + Familie) und Board-Lane. Backend und Vertrag (OpenAPI 0.10.0) und der Renderer in `core:ui` sind aus 4a
fertig – 4b ist reine App-Arbeit, **keine** API-Änderung.

## User Story
Als Kind möchte ich meinen Comic-Avatar in der App gestalten und mit meinem Guthaben Teile kaufen, damit ich in Rangliste,
Board und auf „Heute“ erkennbar bin und meine Punkte sichtbar etwas wert sind.

## Problem → Solution
Rangliste und Board zeigen nur Anfangsbuchstaben bzw. Namen; die Avatar-Endpunkte werden von der App nicht genutzt →
Kind richtet den Avatar ein, kauft Teile, und überall erscheint der gerenderte Avatar (offline aus lokalem Stand).

## Metadata
- **Complexity**: Large
- **Source PRD**: `docs/01-requirements/gamification.prd.md`
- **PRD Phase**: 4 „Avatar & Shop“ → Teil 4b App
- **Requirements**: REQ-GAME-030 (beide Kriterien), REQ-GAME-031 (App-Anteil: alle vier Kriterien), REQ-GAME-041 2. Kriterium
  (Kauf-Hinweis), REQ-GAME-014 1. Kriterium „mit Avatar“
- **Estimated Files**: ≈ 16 neu, ≈ 16 geändert

---

## UX Design

### Before
```
Ich (Kind)                         Board (Tabs)            Heute (Kind)
┌──────────────────────────┐       ┌───────────────────┐   ┌────────────────────┐
│ Ich                      │       │ Prüfen │ Lea │ Ben│   │ Hallo Lea          │
│ [Gesammelt] [Guthaben]   │       └───────────────────┘   │ Essen …            │
│ Sparziel …               │                               │ Aufgaben …         │
│ Rangliste: 1 (L) Lea 42  │                               │ Sparziel …         │
└──────────────────────────┘                               └────────────────────┘
```

### After
```
Ich (Kind)                         Avatar & Shop (ohne Leiste)
┌──────────────────────────┐       ┌──────────────────────────────────┐
│ Ich                      │       │ ‹ Zurück        Guthaben: 40 P   │
│ ┌──────────────────────┐ │       │        ( großer Avatar )         │
│ │ (Avatar) Avatar &    │ │       │ Haut│Frisur│Mütze│Haarfarbe│ … ▸ │
│ │ Shop  ›  / „Richte   │ │       │ ┌────┐┌────┐┌────┐┌────┐         │
│ │ deinen Avatar ein“   │ │       │ │ 🙂 ││ 🙂 ││🙂20P││🙂 ✓│         │
│ └──────────────────────┘ │       │ └────┘└────┘└────┘└────┘         │
│ [Gesammelt] [Guthaben]   │       │ „Bommelmütze“ · 30 P             │
│ Rangliste: 1 (🙂) Lea 42 │       │ [Kaufen für 30 P] / „Noch 12 P“  │
└──────────────────────────┘       │ [Speichern]                      │
                                   │ Avataaars von Pablo Stanley      │
Board-Tabs: Prüfen │ 🙂 Lea │ 🙂 Ben└──────────────────────────────────┘
Heute (Kind): Karte (Avatar) „Dein Avatar“ / „Richte deinen Avatar ein“ → Avatar & Shop
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| „Ich“ (Kind) | Kein Avatar | Karte oben: eigener Avatar (64 dp) + „Avatar & Shop“; ohne Avatar „Richte deinen Avatar ein“ | offline deaktiviert (wie „Belohnungen“) |
| Avatar & Shop | – | Rubrik-Tabs, Teil antippen = Vorschau; Kaufen mit Bestätigung; Speichern | nur online |
| Kauf-Bestätigung | – | „Bommelmütze kaufen?“ + „Kostet 30 Punkte, danach hast du 10.“ + ggf. Sparziel-Hinweis | REQ-GAME-041 |
| Rangliste (Ich, Familie) | Anfangsbuchstabe | `AvatarImage` (Initiale, solange kein Avatar) | REQ-GAME-014 |
| Board-Tabs | Name | Avatar 24 dp + Name | Slot aus der App, `tasks` kennt `gamification` nicht |
| „Heute“ (Kind) | – | Karte mit eigenem Avatar | Eltern: nichts |
| „Familie“ (Eltern) | – | Zeile „Avatare: Avataaars von Pablo Stanley“ am Ende des Punkte-Abschnitts | ADR-0017 Nr. 6 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `android/core/ui/src/main/kotlin/de/familyplanner/android/core/ui/avatar/AvatarImage.kt` | all | Renderer-Composable (`AvatarImage(look, name, modifier, size)`), Initiale ohne Look |
| P0 | `android/core/ui/src/main/kotlin/de/familyplanner/android/core/ui/avatar/AvatarLook.kt` | all | `AvatarLook` (13 Felder = Item-IDs) – wird als Domänentyp wiederverwendet |
| P0 | `android/feature/gamification/src/main/kotlin/de/familyplanner/android/feature/gamification/ui/rewards/RewardsViewModel.kt` | all | VM-Muster: State/Event, `isBusy`, Bestätigungsdialog, 409 → neu laden |
| P0 | `android/feature/gamification/src/main/kotlin/de/familyplanner/android/feature/gamification/ui/rewards/RewardsScreen.kt` | 30-122 | Screen-Muster: `Route` + `Screen`, `PullToRefreshBox`, `ConfirmDialog`, „Noch x Punkte“ |
| P0 | `android/feature/gamification/src/main/kotlin/de/familyplanner/android/feature/gamification/data/RemoteRewardRepository.kt` | all | `apiCall(json, { api.x() }) { it.data?.toDomain() }` |
| P0 | `android/feature/gamification/src/main/kotlin/de/familyplanner/android/feature/gamification/GamificationModule.kt` | all | Koin-Verdrahtung, DataStore je Datei als `single` |
| P0 | `android/app/src/main/kotlin/de/familyplanner/android/app/AppNavHost.kt` | 140-163 | `todayContent`/`familyContent`-Slots, `tasksGraph(navController)` |
| P1 | `android/feature/gamification/src/main/kotlin/de/familyplanner/android/feature/gamification/domain/RewardUseCases.kt` | 1-60 | paralleles Laden mit `async`, erster Fehler gewinnt |
| P1 | `android/feature/gamification/src/main/kotlin/de/familyplanner/android/feature/gamification/data/DataStorePointsCache.kt`, `PointsCacheSerializer.kt`, `StoredPoints.kt` 1-30 | all | lokaler Stand (ADR-0011): Version, `IOException` → `Log.w`, kaputte Datei = leer |
| P1 | `android/feature/gamification/src/main/kotlin/de/familyplanner/android/feature/gamification/ui/me/MeScreen.kt`, `MeViewModel.kt` | all | Einbau Avatar-Karte und Rangliste |
| P1 | `android/feature/gamification/src/main/kotlin/de/familyplanner/android/feature/gamification/ui/components/LeaderboardList.kt` | all | `Initial()` wird durch `AvatarImage` ersetzt |
| P1 | `android/feature/gamification/src/main/kotlin/de/familyplanner/android/feature/gamification/ui/family/FamilyPointsSection.kt`, `FamilyPointsViewModel.kt` | all | Rangliste Eltern, Namensnennung |
| P1 | `android/feature/gamification/src/main/kotlin/de/familyplanner/android/feature/gamification/ui/today/TodaySavingsCard.kt`, `TodaySavingsViewModel.kt` | all | „Heute“-Karte-Muster (lädt im `init`) |
| P1 | `android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/ui/board/BoardScreen.kt` | 42-125 | `BoardRoute`, `BoardTabs` (Tab-Text) |
| P1 | `android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/TasksNavigation.kt` | 42-70 | `tasksGraph` |
| P1 | `android/feature/gamification/src/test/kotlin/.../fakes/Fakes.kt`, `RewardFakes.kt` | all | `childSession` (c-1 „Lea“), `parentSession`, `noon`, `networkDown`, Fake-Stil |
| P1 | `android/feature/gamification/src/test/kotlin/.../data/RemoteRewardRepositoryTest.kt` | 1-60 | MockWebServer-Setup |
| P1 | `android/feature/gamification/src/test/kotlin/.../ui/RewardViewModelsTest.kt`, `RewardScreensTest.kt` | 1-70 | VM-/Robolectric-Teststil |
| P1 | `android/app/src/test/kotlin/de/familyplanner/android/app/AppNavHostTest.kt` | 220-300 | Koin-Module im App-Test müssen neue Abhängigkeiten bekommen |
| P2 | `api/openapi.yaml` | 1259-1350, 2611-2750 | Avatar-Endpunkte und Schemas |
| P2 | `docs/03-design/gamification.md` | 138-185, 229-250 | Fachregeln 4a, Format „App-Seite …“-Abschnitt |
| P2 | `docs/02-architecture/adr/0017-avatar-bausteine.md` | all | Namensnennung, Fallback-Regeln |

## External Documentation
No external research needed — feature uses established internal patterns (AndroidSVG-Rendering ist aus dem 4a-Spike erprobt).

---

## Contract (bereits umgesetzt, 0.10.0 – nicht ändern)

Generierter Client `de.familyplanner.api.client.GamificationApi`:
```kotlin
suspend fun getAvatarCatalog(): Response<AvatarCatalogResponse>          // data: AvatarCatalog(slots: List<AvatarSlotInfo>)
suspend fun listAvatars(): Response<MemberAvatarListResponse>             // data: List<MemberAvatar(memberId, avatar: AvatarConfig, updatedAt)>
suspend fun getAvatarProfile(memberId: String): Response<AvatarProfileResponse>   // AvatarProfile(memberId, ownedItemIds, avatar: AvatarConfig? = null)
suspend fun saveAvatar(memberId: String, avatarConfig: AvatarConfig): Response<AvatarProfileResponse>
suspend fun purchaseAvatarItem(itemId: String): Response<AvatarPurchaseResponse>  // 201, AvatarPurchase(item: AvatarItem, balance: Int)
```
Modelle (`de.familyplanner.api.model`): `AvatarConfig` (skin, top, hairColor, hatColor, eyes, eyebrows, mouth, clothes, clothesColor,
background: String; clothesGraphic, accessories, facialHair: String? = null), `AvatarItem(id, slot: AvatarSlot, category: ShopCategory, title, price)`,
`AvatarSlotInfo(slot, optional, items)`, Enum `AvatarSlot` = `SKIN, TOP, HAIR_COLOR, HAT_COLOR, EYES, EYEBROWS, MOUTH, CLOTHES, CLOTHES_COLOR,
CLOTHES_GRAPHIC, ACCESSORIES, FACIAL_HAIR, BACKGROUND` (`.value` = `"hairColor"` usw.), Enum `ShopCategory` = `HAIR, HEADWEAR, FACE, CLOTHES, ACCESSORIES, COLORS`.

Server-Regeln (aus 4a): Profil lesen nur eigenes (Kind) / alle (Eltern); Speichern nur Kind selbst, Teile kostenlos oder gekauft (sonst 409);
Kauf nur Kinder; 409 bei kostenlos, schon gekauft, zu wenig Guthaben („Nicht genug Guthaben: x von y Punkten“); Kauf ändert den Avatar **nicht**.
`GET /avatars` liefert nur Kinder mit gespeichertem Avatar. Katalog: `top` enthält Frisuren (`HAIR`) **und** Kopfbedeckungen (`HEADWEAR`).

---

## Patterns to Mirror

### NAMING_CONVENTION
```kotlin
// SOURCE: feature/gamification/.../ui/rewards/RewardsViewModel.kt:19-44
data class RewardsUiState(val memberId: String?, val data: ChildRewards? = null, val isLoading: Boolean = true,
    val error: AppError? = null, val confirmRedeem: Reward? = null, val isBusy: Boolean = false)
sealed interface RewardsEvent { data object Refresh : RewardsEvent; data class AskRedeem(val reward: Reward) : RewardsEvent; … }
```
Strings: Präfix `game_` (gamification), `tasks_` (tasks), `core_` (core:ui). KDoc/Kommentare deutsch, Log-Texte englisch.

### ERROR_HANDLING (Aktion mit 409 → neu laden)
```kotlin
// SOURCE: feature/gamification/.../ui/rewards/RewardsViewModel.kt:96-112
_state.update { it.copy(isBusy = true, error = null) }
viewModelScope.launch {
    val result = call()
    _state.update { it.copy(isBusy = false, confirmRedeem = if (closeDialog) null else it.confirmRedeem) }
    when (result) {
        is ApiResult.Success -> refresh()
        is ApiResult.Failure -> {
            // Stand hat sich geändert (z. B. Belohnung deaktiviert): erst neu laden, dann den Fehler zeigen.
            if (result.error is AppError.Conflict) refresh()
            _state.update { it.copy(error = result.error, confirmRedeem = null) }
        }
    }
}
```

### LOGGING_PATTERN
```kotlin
// SOURCE: feature/gamification/.../data/DataStorePointsCache.kt:10-24
private const val TAG = "DataStorePointsCache"
} catch (e: IOException) { Log.w(TAG, "Points cache unreadable", e); null }
```
Nur Cache-/Renderfehler werden geloggt (`Log.w`), Nutzerfehler laufen über `AppError` → `ErrorText`.

### REPOSITORY_PATTERN
```kotlin
// SOURCE: feature/gamification/.../data/RemoteRewardRepository.kt:22-27
override suspend fun rewards(): ApiResult<List<Reward>> =
    apiCall(json, { api.listRewards(false) }) { response -> response.data?.map { it.toDomain() } }
override suspend fun redeem(rewardId: String): ApiResult<Redemption> =
    apiCall(json, { api.redeemReward(rewardId) }) { it.data?.toDomain() }
```
Mapper-Konvention: `internal fun ApiX.toDomain()` in `data/…Mappers.kt`; Enums per Namensgleichheit; Listen, die nicht vollständig
umwandelbar sind → `mapAllOrNull` (`data/Parsing.kt`) → `null` = Serverfehler.

### SERVICE_PATTERN (Use Case, paralleles Laden)
```kotlin
// SOURCE: feature/gamification/.../domain/RewardUseCases.kt:17-38
suspend operator fun invoke(memberId: String): ApiResult<ChildRewards> =
    coroutineScope {
        val rewards = async { repository.rewards() }
        val goal = async { repository.savingsGoal(memberId) }
        …
        val failure = listOf(rewardsResult, goalResult, …).filterIsInstance<ApiResult.Failure>().firstOrNull()
        if (failure != null) return@coroutineScope failure
        ApiResult.Success(ChildRewards(rewards = (rewardsResult as ApiResult.Success).data, …))
    }
```

### CACHE_PATTERN (ADR-0011)
```kotlin
// SOURCE: feature/gamification/.../data/PointsCacheSerializer.kt:13-30, GamificationModule.kt:54-62
json.decodeFromString<StoredPoints>(bytes.decodeToString()).takeIf { it.version == CACHE_VERSION } ?: defaultValue
// catch SerializationException / IllegalArgumentException → Log.w + defaultValue
single<PointsCache> { DataStorePointsCache(DataStoreFactory.create(serializer = PointsCacheSerializer(get()),
    produceFile = { androidContext().dataStoreFile(POINTS_CACHE_FILE) })) }
```

### SLOT_PATTERN (Modulgrenzen in der App)
```kotlin
// SOURCE: android/app/.../AppNavHost.kt:141-160 ; feature/auth/.../AuthNavigation.kt:51-59
mainGraph(todayContent = { TodayMealRoute(…); TodayTasksRoute(…); TodaySavingsRoute(…) }, familyContent = { FamilyPointsRoute(…) })
// auth kennt die anderen Module nicht; die App reicht Composables als Slots durch.
```
→ Board-Lane-Avatar kommt genauso als Slot in `tasksGraph`/`BoardRoute` (kein neues Interface in `core`).

### TEST_STRUCTURE
```kotlin
// SOURCE: feature/gamification/src/test/.../ui/RewardViewModelsTest.kt:39-65
class RewardViewModelsTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    private val repository = FakeRewardRepository()
    @Test fun `REQ-GAME-041 rewards view model loads rewards and goal`() { val vm = rewards(); vm.onEvent(RewardsEvent.Refresh); assertEquals(2, vm.state.value.data?.rewards?.size) }
}
// Robolectric: @RunWith(RobolectricTestRunner::class), compose.setContent { FamilyPlannerTheme { … } }, onNodeWithText(...).assertIsDisplayed()
// Repository: MockWebServer + createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json), enqueue(code, body)
```

---

## Design-Entscheidungen

| Thema | Entscheidung | Begründung |
|---|---|---|
| Editor vs. Shop | **Ein** Bildschirm „Avatar & Shop“: kostenlose, gekaufte und kaufbare Teile in denselben Rubriken | Antippen = Vorschau erfüllt „vor dem Kauf meinen Avatar mit dem Item sehen“ (REQ-GAME-031, 4. Kriterium) ohne zweiten Screen; KISS |
| Rubriken | Je Slot eine Rubrik, `top` geteilt nach `ShopCategory` in „Frisur“ (`HAIR`) und „Kopfbedeckung“ (`HEADWEAR`) → 14 Rubriken in Katalogreihenfolge | Design 4a („zwei Shop-Rubriken über `ShopCategory`“) |
| Domänentyp Avatar | `core.ui.avatar.AvatarLook` wird direkt verwendet; Slot-Zugriff über Erweiterungen in `domain/avatar/AvatarLooks.kt` | keine zweite 13-Felder-Klasse; Domäne nutzt `core.ui` bereits (`BerlinZone`) |
| Standardauswahl | erstes kostenloses Teil je Pflicht-Slot, optionale Slots `null` | Design 4a, Server liefert `avatar = null` |
| Kaufen ≠ Tragen | Kauf bucht nur ab; das Teil bleibt in der Vorschau, getragen wird es erst mit „Speichern“ | Server-Semantik (PUT ersetzt Konfiguration) |
| Speichern | aktiv, wenn alle Teile tragbar (kostenlos/besessen) **und** (noch kein Avatar gespeichert **oder** Entwurf ≠ gespeichert) | sonst 409 vom Server |
| Sparziel-Hinweis | `goalWarning = reward != null && !reward.archived && balance − price < reward.price` (⇔ Fortschritt `min(balance, ziel)` sinkt) | REQ-GAME-041, 2. Kriterium; Hinweis, kein Verbot |
| Guthaben im Editor | aus `SavingsGoal.balance` (Endpunkt `savings-goal` liefert Guthaben auch ohne Ziel); nach Kauf `AvatarPurchase.balance` | kein zusätzlicher Konto-Aufruf |
| Avatare aller Kinder | `AvatarDirectory` (single): `StateFlow<Map<memberId, AvatarLook>>`, lädt `GET /avatars`, schreibt lokalen Stand `game_avatars.json`, offline aus Stand | eine Quelle für Rangliste, Board, „Heute“ (Design 4a) |
| Board-Lane | `tasksGraph(navController, laneAvatar)` mit Default `{ _, _ -> }`; App übergibt `MemberAvatar(memberId, name, 24.dp)` aus `gamification` | Feature-Module kennen sich nicht (CLAUDE.md), Muster `todayContent` |
| Rendering | `AvatarImage` rendert künftig **abseits des Main-Threads** (`produceState` + `Dispatchers.Default`), behält das letzte Bild bis zum neuen | Raster mit ~10 Vorschau-Avataren à ~15 ms würde sonst ruckeln |
| Raster-Kacheln | Farb-Rubriken (`COLORS`-Slots: skin, hairColor, hatColor, clothesColor, background) als Farbkreis; andere Rubriken als Mini-Avatar mit diesem Teil (56 dp) | Farben brauchen kein SVG |
| Namensnennung | Fußzeile im Editor + Zeile am Ende von `FamilyPointsSection` | ADR-0017 Nr. 6; Kinder sehen „Familie“ nicht |
| Rechte in der App | Editor/Ich-Karte/Heute-Karte nur für Kinder; Eltern sehen nur Avatare | Server: Speichern/Kauf nur Kind |

## Files to Change

| File | Action | Justification |
|---|---|---|
| `android/core/ui/.../avatar/AvatarImage.kt` | UPDATE | asynchrones Rendern |
| `android/core/ui/src/main/res/values/strings.xml` | UPDATE | `core_avatar_credit` |
| `android/feature/gamification/.../domain/avatar/AvatarModels.kt` | CREATE | `AvatarSlot`, `ShopCategory`, `AvatarItem`, `AvatarSlotInfo`, `AvatarCatalog`, `AvatarProfile`, `AvatarPurchase`, `AvatarRubric` |
| `android/feature/gamification/.../domain/avatar/AvatarLooks.kt` | CREATE | `AvatarLook.itemIn(slot)`, `AvatarLook.wearing(slot, id)`, `defaultLook(catalog)` |
| `android/feature/gamification/.../domain/avatar/AvatarRules.kt` | CREATE | `rubrics()`, `isWearable`, `purchaseOption`, `goalWarning`, `canSave` |
| `android/feature/gamification/.../domain/avatar/AvatarPorts.kt` | CREATE | `AvatarRepository`, `AvatarCache` |
| `android/feature/gamification/.../domain/avatar/AvatarDirectory.kt` | CREATE | gemeinsame Avatar-Quelle |
| `android/feature/gamification/.../domain/avatar/AvatarUseCases.kt` | CREATE | `LoadAvatarEditorUseCase`, `AvatarActions` |
| `android/feature/gamification/.../data/RemoteAvatarRepository.kt` | CREATE | REST-Adapter |
| `android/feature/gamification/.../data/AvatarMappers.kt` | CREATE | API ↔ Domäne |
| `android/feature/gamification/.../data/StoredAvatars.kt` | CREATE | Dateiformat lokaler Stand |
| `android/feature/gamification/.../data/AvatarCacheSerializer.kt` | CREATE | DataStore-Serializer |
| `android/feature/gamification/.../data/DataStoreAvatarCache.kt` | CREATE | `AvatarCache`-Implementierung |
| `android/feature/gamification/.../ui/avatar/AvatarEditorViewModel.kt` | CREATE | Editor-State |
| `android/feature/gamification/.../ui/avatar/AvatarEditorScreen.kt` | CREATE | Editor-UI + Kauf-Dialog |
| `android/feature/gamification/.../ui/avatar/AvatarItemGrid.kt` | CREATE | Rubrik-Raster (Datei < 200 Zeilen halten) |
| `android/feature/gamification/.../ui/avatar/MemberAvatar.kt` | CREATE | öffentliches Composable für App-Slots (Board) |
| `android/feature/gamification/.../ui/avatar/MyAvatarCard.kt` | CREATE | Karte auf „Ich“ und „Heute“ |
| `android/feature/gamification/.../ui/today/TodayAvatarRoute.kt` | CREATE | „Heute“-Einstieg + `TodayAvatarViewModel` |
| `android/feature/gamification/.../ui/components/LeaderboardList.kt` | UPDATE | `looks`-Parameter, `AvatarImage` |
| `android/feature/gamification/.../ui/me/MeScreen.kt`, `MeViewModel.kt` | UPDATE | Avatar-Karte, `looks` |
| `android/feature/gamification/.../ui/family/FamilyPointsSection.kt`, `FamilyPointsViewModel.kt` | UPDATE | `looks`, Namensnennung |
| `android/feature/gamification/.../GamificationModule.kt` | UPDATE | Koin |
| `android/feature/gamification/.../GamificationNavigation.kt` | UPDATE | `AvatarEditorRoute`, `openAvatarEditor()` |
| `android/feature/gamification/src/main/res/values/strings.xml` | UPDATE | Texte |
| `android/feature/tasks/.../TasksNavigation.kt`, `ui/board/BoardScreen.kt` | UPDATE | `laneAvatar`-Slot |
| `android/app/.../AppNavHost.kt` | UPDATE | Slots verdrahten, Heute-Karte, Navigation |
| `android/app/build.gradle.kts` | UPDATE | `versionCode 14`, `versionName "0.13.0"` |
| Tests (siehe Testing Strategy) | CREATE/UPDATE | |
| `docs/03-design/gamification.md`, `docs/04-verification/traceability.md`, `docs/01-requirements/gamification.prd.md`, `docs/02-architecture/adr/0017-avatar-bausteine.md` | UPDATE | Doku |

`...` = `src/main/kotlin/de/familyplanner/android/<modul-pfad>` (z. B. `feature/gamification/src/main/kotlin/de/familyplanner/android/feature/gamification`).

## NOT Building

- Backend-/OpenAPI-Änderungen (Vertrag 0.10.0 bleibt).
- Eltern-Ansicht auf Besitz/Profil eines Kindes, Eltern passen Preise an (Could).
- Separater Shop-Bildschirm, Suche/Filter, Sortierung nach Preis.
- „Ungespeicherte Änderungen verwerfen?“-Dialog beim Verlassen (Entwurf wird verworfen; Käufe sind ohnehin gebucht).
- Offline-Editor/-Kauf (Editor nur online; offline nur Anzeige aus lokalem Stand).
- Avatar in Aufgaben-Prüfliste, Verlauf, Essensplan oder Hinweisen.
- Hinweis/Benachrichtigung bei Kauf an Eltern.
- Änderungen an `Leaderboard`-Modell oder Punkte-Cache-Version.

---

## Step-by-Step Tasks

### Task 1: `AvatarImage` asynchron rendern (core:ui)
- **ACTION**: `AvatarImage.kt` so umbauen, dass SVG-Bau + Parse auf `Dispatchers.Default` läuft.
- **IMPLEMENT**:
  ```kotlin
  val rendered by produceState<SVG?>(initialValue = null, look, definition) {
      val loaded = definition
      value = if (look == null || loaded == null) null else withContext(Dispatchers.Default) {
          runCatching { SVG.getFromString(AvatarSvg.render(loaded, look.toSelection())) }
              .onFailure { Log.w(LOG_TAG, "Avatar could not be rendered", it) }.getOrNull()
      }
  }
  ```
  `produceState` merkt den Wert ohne Keys → bis das neue Bild fertig ist, bleibt das alte stehen (kein Flackern im Editor).
  Ohne Look → sofort `null` → Initiale. Ein `look != null` mit noch nicht gerendertem Bild: Initiale zeigen (erster Aufbau).
  Zusätzlich neuer String `core_avatar_credit` = „Avatare: „Avataaars“ von Pablo Stanley“ in `core/ui/.../res/values/strings.xml`
  und öffentliches `@Composable fun AvatarCredit(modifier: Modifier = Modifier)` (Text, `bodySmall`) in `AvatarImage.kt`.
- **MIRROR**: bestehende `produceState`-Nutzung für `definition` in derselben Datei.
- **IMPORTS**: unverändert + `androidx.compose.ui.text` nicht nötig; `MaterialTheme.typography.bodySmall`.
- **GOTCHA**: `AvatarImageSpikeTest` nutzt `compose.waitForIdle()` – reicht das bei `Dispatchers.Default` nicht (Robolectric wartet nicht
  auf fremde Dispatcher), im Test nach `setContent` `compose.waitUntil(timeoutMillis = 5_000) { onAllNodes(hasContentDescription(...)) … }`
  bzw. auf eine Bitmap ≠ Initiale warten. Lösung im Test, nicht synchron zurückbauen. `CancellationException` nicht schlucken:
  `runCatching` liegt **innerhalb** von `withContext`, `produceState` bricht sauber ab, wenn sich der Key ändert.
- **VALIDATE**: `cd android && ./gradlew :core:ui:testDebugUnitTest` grün, PNGs unter `core/ui/build/avatar-spike/` weiterhin sinnvoll.

### Task 2: Domäne – Modelle, Look-Zugriff, Regeln (TDD: zuerst `AvatarRulesTest`)
- **ACTION**: Paket `domain/avatar` anlegen.
- **IMPLEMENT** `AvatarModels.kt`:
  ```kotlin
  /** Reihenfolge = Editor; [key] = Wert im Vertrag. */
  enum class AvatarSlot(val key: String, val isColor: Boolean) {
      SKIN("skin", true), TOP("top", false), HAIR_COLOR("hairColor", true), HAT_COLOR("hatColor", true), EYES("eyes", false),
      EYEBROWS("eyebrows", false), MOUTH("mouth", false), CLOTHES("clothes", false), CLOTHES_COLOR("clothesColor", true),
      CLOTHES_GRAPHIC("clothesGraphic", false), ACCESSORIES("accessories", false), FACIAL_HAIR("facialHair", false), BACKGROUND("background", true),
  }
  enum class ShopCategory { HAIR, HEADWEAR, FACE, CLOTHES, ACCESSORIES, COLORS }
  data class AvatarItem(val id: String, val slot: AvatarSlot, val category: ShopCategory, val title: String, val price: Int) { val isFree get() = price == 0 }
  data class AvatarSlotInfo(val slot: AvatarSlot, val optional: Boolean, val items: List<AvatarItem>)
  data class AvatarCatalog(val slots: List<AvatarSlotInfo>)
  /** [look] `null`, bis das Kind einmal gespeichert hat; [owned] = gekaufte Teile (ohne Grundsortiment). */
  data class AvatarProfile(val memberId: String, val look: AvatarLook?, val owned: Set<String>)
  data class AvatarPurchase(val item: AvatarItem, val balance: Int)
  /** Rubrik im Editor: ein Slot, beim Slot `top` getrennt nach Kategorie (Frisur/Kopfbedeckung). */
  data class AvatarRubric(val slot: AvatarSlot, val category: ShopCategory, val optional: Boolean, val items: List<AvatarItem>)
  ```
  `AvatarLooks.kt`: `fun AvatarLook.itemIn(slot: AvatarSlot): String?` (`when` über alle 13, kein `else`) und
  `fun AvatarLook.wearing(slot: AvatarSlot, itemId: String?): AvatarLook` (`copy`; für Pflicht-Slots `requireNotNull(itemId)`),
  `fun defaultLook(catalog: AvatarCatalog): AvatarLook` (erstes `isFree`-Teil je nicht-optionalem Slot; fehlt eins → `error(...)`,
  Server-Katalog garantiert es – im Use Case als `AppError.Server` behandeln, s. Task 4).
  `AvatarRules.kt`:
  ```kotlin
  fun AvatarCatalog.rubrics(): List<AvatarRubric>  // je Slot in Reihenfolge; Items gruppiert nach category in Erstauftreten-Reihenfolge
  fun AvatarItem.isWearable(owned: Set<String>) = isFree || id in owned
  sealed interface PurchaseOption { data object Wearable : PurchaseOption; data class Buyable(val price: Int) : PurchaseOption; data class Missing(val points: Int) : PurchaseOption }
  fun purchaseOption(item: AvatarItem, owned: Set<String>, balance: Int): PurchaseOption
  fun goalWarning(goal: SavingsGoal?, balance: Int, price: Int): Boolean  // reward != null && !archived && balance - price < reward.price
  fun canSave(draft: AvatarLook, saved: AvatarLook?, catalog: AvatarCatalog, owned: Set<String>): Boolean
      // alle getragenen IDs im Katalog bekannt und tragbar; und (saved == null || draft != saved)
  ```
  Unbekannte Item-ID im gespeicherten Look (vom Server schon repariert, ADR-0017) → `canSave` false nur, wenn der **Entwurf** sie trägt.
- **MIRROR**: `domain/Rewards.kt` (kleine Datenklassen + KDoc), `domain/Badges.kt` (reine Funktionen).
- **IMPORTS**: `de.familyplanner.android.core.ui.avatar.AvatarLook`, `...domain.SavingsGoal`.
- **GOTCHA**: `AvatarSlot` der Domäne ≠ generiertes `de.familyplanner.api.model.AvatarSlot` – in `domain` nie API-Typen importieren.
  Konstantennamen identisch halten (Mapping per `name`).
- **VALIDATE**: `AvatarRulesTest` grün (Fälle in Testing Strategy).

### Task 3: Ports, Remote-Adapter, Mapper (TDD: `RemoteAvatarRepositoryTest`)
- **ACTION**: `AvatarPorts.kt`, `RemoteAvatarRepository.kt`, `AvatarMappers.kt`.
- **IMPLEMENT**:
  ```kotlin
  interface AvatarRepository {
      suspend fun catalog(): ApiResult<AvatarCatalog>
      /** Gespeicherte Avatare aller Kinder: memberId → Look. */
      suspend fun avatars(): ApiResult<Map<String, AvatarLook>>
      suspend fun profile(memberId: String): ApiResult<AvatarProfile>
      suspend fun save(memberId: String, look: AvatarLook): ApiResult<AvatarProfile>
      suspend fun purchase(itemId: String): ApiResult<AvatarPurchase>
  }
  /** Lokaler Stand der Avatare (ADR-0011); Fehler beim Speichern sind nie ein Nutzerfehler. */
  interface AvatarCache { suspend fun read(): Map<String, AvatarLook>?; suspend fun write(looks: Map<String, AvatarLook>); suspend fun clear() }
  ```
  Mapper: `ApiAvatarConfig.toDomain() = AvatarLook(skin, top, …, facialHair)`, `AvatarLook.toApi()`,
  `ApiAvatarItem.toDomain() = AvatarItem(id, AvatarSlot.valueOf(slot.name), ShopCategory.valueOf(category.name), title, price)`,
  `ApiAvatarProfile.toDomain() = AvatarProfile(memberId, avatar?.toDomain(), ownedItemIds.toSet())`,
  Liste `/avatars` → `associate { it.memberId to it.avatar.toDomain() }`.
  Repository: `apiCall(json, { api.getAvatarCatalog() }) { it.data?.toDomain() }` usw.; `purchase` → `api.purchaseAvatarItem(itemId)`.
- **MIRROR**: REPOSITORY_PATTERN; Import-Aliase wie `import de.familyplanner.api.model.Reward as ApiReward`.
- **IMPORTS**: `de.familyplanner.api.model.AvatarConfig as ApiAvatarConfig`, `AvatarItem as ApiAvatarItem`, `AvatarProfile as ApiAvatarProfile`,
  `AvatarCatalog as ApiAvatarCatalog`, `AvatarPurchase as ApiAvatarPurchase`.
- **GOTCHA**: `apiCall` bildet 409 auf `AppError.Conflict(message)` ab – der Servertext („Nicht genug Guthaben …“) erscheint so im `ErrorText`.
  `valueOf` kann bei neuem Server-Enum werfen → `runCatching`/`entries.firstOrNull { it.name == … }` und ganze Antwort als Serverfehler (`null`) behandeln (wie `ApiRedemption.toDomain`).
- **VALIDATE**: `./gradlew :feature:gamification:testDebugUnitTest --tests '*RemoteAvatarRepositoryTest'`.

### Task 4: Lokaler Stand + `AvatarDirectory` + Use Cases (TDD)
- **ACTION**: `StoredAvatars.kt`, `AvatarCacheSerializer.kt`, `DataStoreAvatarCache.kt`, `AvatarDirectory.kt`, `AvatarUseCases.kt`.
- **IMPLEMENT**:
  - `@Serializable data class StoredAvatars(val version: Int = AVATAR_CACHE_VERSION, val looks: List<StoredLook> = emptyList())`,
    `StoredLook(memberId + 13 Felder, optionale = null)`, `internal const val AVATAR_CACHE_VERSION = 1`. Serializer/Cache 1:1 wie Punkte
    (Datei `game_avatars.json`, `TAG`-Konstanten, `IOException` → `Log.w`). `read()` liefert `null` bei leerem Default (nie geschrieben).
  - `AvatarDirectory`:
    ```kotlin
    /** Avatare aller Kinder für Rangliste, Board und „Heute“; offline aus dem lokalen Stand (ADR-0011). */
    class AvatarDirectory(private val repository: AvatarRepository, private val cache: AvatarCache) {
        private val _looks = MutableStateFlow<Map<String, AvatarLook>>(emptyMap())
        val looks: StateFlow<Map<String, AvatarLook>> = _looks.asStateFlow()
        private val mutex = Mutex(); private var loaded = false
        /** Lädt einmal je Prozess; weitere Aufrufe sind billig. */
        suspend fun ensureLoaded() { if (!loaded) refresh() }
        suspend fun refresh() = mutex.withLock {
            when (val result = repository.avatars()) {
                is ApiResult.Success -> { _looks.value = result.data; cache.write(result.data); loaded = true }
                is ApiResult.Failure -> {
                    if (result.error == AppError.Unauthorized) cache.clear()
                    else if (_looks.value.isEmpty()) cache.read()?.let { _looks.value = it }
                }
            }
        }
        /** Nach eigenem Speichern sofort überall sichtbar. */
        suspend fun update(memberId: String, look: AvatarLook) = mutex.withLock {
            _looks.value = _looks.value + (memberId to look); cache.write(_looks.value)
        }
    }
    ```
  - `LoadAvatarEditorUseCase(avatars: AvatarRepository, rewards: RewardRepository)`: `invoke(memberId)` lädt parallel `catalog()`,
    `profile(memberId)`, `rewards.savingsGoal(memberId)` → `ApiResult<AvatarEditorData(catalog, profile, goal: SavingsGoal)>`; erster Fehler gewinnt.
    Katalog ohne kostenloses Teil in einem Pflicht-Slot → `ApiResult.Failure(AppError.Server)` (statt `defaultLook`-Exception).
  - `AvatarActions(repository, directory)`: `save(memberId, look)` → bei Erfolg `directory.update(memberId, saved.look ?: look)`;
    `purchase(itemId)` → `repository.purchase(itemId)`.
- **MIRROR**: CACHE_PATTERN, SERVICE_PATTERN; `SyncRewardNoticesUseCase` (single + Mutex).
- **IMPORTS**: `kotlinx.coroutines.sync.Mutex`, `withLock`, `kotlinx.coroutines.flow.*`.
- **GOTCHA**: `loaded` nur nach Erfolg setzen, sonst wird offline nie wieder geladen. Kein `!!`.
- **VALIDATE**: `DataStoreAvatarCacheTest`, `AvatarDirectoryTest`, `AvatarUseCasesTest` grün.

### Task 5: Editor-ViewModel (TDD: `AvatarViewModelsTest`)
- **ACTION**: `ui/avatar/AvatarEditorViewModel.kt`.
- **IMPLEMENT**:
  ```kotlin
  data class AvatarEditorUiState(
      val memberId: String?, val data: AvatarEditorData? = null, val draft: AvatarLook? = null,
      val owned: Set<String> = emptySet(), val balance: Int = 0, val selectedRubric: Int = 0,
      val focused: AvatarItem? = null, val confirmPurchase: AvatarItem? = null,
      val isLoading: Boolean = true, val isBusy: Boolean = false, val error: AppError? = null, val saved: Boolean = false,
  ) {
      val rubrics: List<AvatarRubric> get() = data?.catalog?.rubrics().orEmpty()
      val canSave: Boolean get() = … canSave(draft, data.profile.look, data.catalog, owned) && !isBusy
      val purchaseOption: PurchaseOption? get() = focused?.let { purchaseOption(it, owned, balance) }
      val showGoalWarning: Boolean get() = confirmPurchase?.let { goalWarning(data?.goal, balance, it.price) } ?: false
  }
  sealed interface AvatarEditorEvent {
      data object Refresh; data class SelectRubric(val index: Int); data class Pick(val rubric: AvatarRubric, val item: AvatarItem?)  // null = „Nichts“ (optional)
      data object AskPurchase; data object ConfirmPurchase; data object DismissPurchase; data object Save; data object DismissSaved
  }
  class AvatarEditorViewModel(sessions: SessionStore, private val load: LoadAvatarEditorUseCase, private val actions: AvatarActions) : ViewModel()
  ```
  Regeln: Refresh behält einen vorhandenen `draft` (nach Kauf-409 nicht die Auswahl verlieren), sonst `draft = profile.look ?: defaultLook(catalog)`;
  `owned = profile.owned`, `balance = goal.balance`. `Pick` setzt `draft = draft.wearing(slot, item?.id)` und `focused = item`.
  `AskPurchase` nur bei `PurchaseOption.Buyable`. `ConfirmPurchase` → `actions.purchase` → Erfolg: `owned + id`, `balance = purchase.balance`,
  Dialog zu, **kein** Reload nötig; 409 → Refresh + Fehler (ERROR_HANDLING). `Save` → `actions.save` → Erfolg: `data.profile` = Antwort,
  `saved = true` (Screen zeigt Snackbar/Text „Gespeichert“ und ruft `onBack` **nicht** automatisch).
  Kind-Check: `sessions.current?.role != Role.CHILD` → `error = AppError.Forbidden`, nichts laden.
- **MIRROR**: `RewardsViewModel` (Struktur, `loadJob`, `isBusy`-Sperre).
- **GOTCHA**: `ViewModel` ≤ ~150 Zeilen; Ableitungen als `get()` im State (wie `MeUiState.myRank`), nicht im VM speichern.
  Ableitungen nutzen nur reine Domänenfunktionen → testbar ohne Compose.
- **VALIDATE**: `AvatarViewModelsTest` grün.

### Task 6: Editor-UI + Navigation
- **ACTION**: `AvatarEditorScreen.kt`, `AvatarItemGrid.kt`, Route in `GamificationNavigation.kt`.
- **IMPLEMENT**:
  - `@Serializable data object AvatarEditorRoute`; `composable<AvatarEditorRoute> { AvatarEditorRoute(onBack = { navController.popBackStack() }) }`;
    `fun NavHostController.openAvatarEditor() = navigate(AvatarEditorRoute)`. `MeRoute` erhält `onOpenAvatar`.
  - Screen (`PullToRefreshBox` + `LazyColumn`, Padding 24 dp wie Rewards): Zurück-Button, Titel `game_avatar_title` „Avatar & Shop“,
    `game_rewards_balance` (Guthaben), `ErrorText`, große Vorschau `AvatarImage(draft, name = sessionName, size = 160.dp)` zentriert,
    `PrimaryScrollableTabRow` über `rubrics` mit `rubricLabel(rubric)` (Labels-Funktion in `ui/Labels.kt`), Raster, Aktionszeile, `AvatarCredit()`.
  - `AvatarItemGrid`: `FlowRow` (keine verschachtelte Lazy-Liste in `LazyColumn`!) mit Kacheln 64 dp: optionaler Slot zuerst Kachel „Nichts“;
    Farb-Slot (`slot.isColor`) → Kreis `Color(android.graphics.Color.parseColor("#" + id.substringAfter('.')))`; sonst
    `AvatarImage(draft.wearing(slot, item.id), item.title, size = 56.dp)`. Markierung: gewählt = Rand `primary`; `price > 0 && !owned` → „20 P“;
    besessen → „✓“. `contentDescription` = Titel + Preis/„gehört dir“ (für Tests und TalkBack).
  - Aktionszeile unter dem Raster für `focused`: Titel; bei `Buyable` Button `game_avatar_buy` „Kaufen für %d P“; bei `Missing`
    Text `game_rewards_missing` („Noch %d Punkte“) + deaktivierter Kauf-Button; `Button(Speichern, enabled = state.canSave)`; nach Speichern Text `game_avatar_saved`.
  - Kauf-Dialog: `ConfirmDialog(title = game_avatar_buy_title („„%s“ kaufen?“), text = game_avatar_buy_text („Kostet %1$d Punkte. Danach hast du noch %2$d.“)
    + bei `showGoalWarning` "\n\n" + game_avatar_goal_warning („Achtung: Dann dauert es länger bis „%s“.“), confirmLabel = game_avatar_buy_confirm „Kaufen“)`.
- **MIRROR**: `RewardsScreen` (Route/Screen-Split, `LifecycleEventEffect(ON_RESUME) { Refresh }`), `BoardTabs` (Tab-Row).
- **IMPORTS**: `androidx.compose.foundation.layout.FlowRow` (`@OptIn(ExperimentalLayoutApi::class)`, falls in der Compose-Version nötig).
- **GOTCHA**: Farbe nur aus Katalog-IDs parsen; ungültiges Hex → `runCatching` → graue Fläche (nie Absturz). Viele `AvatarImage`-Kacheln:
  dank Task 1 asynchron. Dateien < 200 Zeilen, Funktionen < 50 Zeilen (detekt `LongMethod`).
- **VALIDATE**: `AvatarScreensTest` (Editor-Fälle) grün; Compose-Preview optional.

### Task 7: Avatare überall (Rangliste, Ich, Heute, Familie, Board)
- **ACTION**: Anzeigen anbinden.
- **IMPLEMENT**:
  - `LeaderboardList(entries, highlightId, onClick, looks: Map<String, AvatarLook> = emptyMap())`; `Initial()` entfernen, stattdessen
    `AvatarImage(looks[child.memberId], child.name, size = AvatarSize)`.
  - `MeViewModel(loadPoints, sessions, avatars: AvatarDirectory)`: `MeUiState` + `looks: Map<String, AvatarLook>`; im `init` `avatars.looks`
    in den State sammeln (`viewModelScope.launch { avatars.looks.collect { l -> _state.update { it.copy(looks = l) } } }`); `Refresh` startet
    zusätzlich `launch { avatars.refresh() }`. `MeScreen`: erste Karte `MyAvatarCard(look = state.looks[memberId], name, enabled = !isOffline, onClick = onOpenAvatar)`
    nur wenn `snapshot.account != null` (= Kind); Rangliste mit `looks = state.looks`.
  - `FamilyPointsViewModel` analog (`looks`), `FamilyPointsSection` reicht `looks` an `LeaderboardList` und zeigt am Ende `AvatarCredit()`.
  - `MyAvatarCard`: `Card(onClick)` mit Row: `AvatarImage(look, name, size = 64.dp)` + Text `game_avatar_open` „Avatar & Shop“ bzw. ohne Look
    `game_avatar_setup` „Richte deinen Avatar ein“; `enabled = false` → nicht antippbar (Muster `SavingsGoalCard`).
  - `TodayAvatarRoute(onOpenAvatar)` + `TodayAvatarViewModel(sessions, avatars)`: nur Kind (`Role.CHILD`), sonst nichts rendern; `init` →
    `avatars.ensureLoaded()`; zeigt `MyAvatarCard`.
  - `MemberAvatar(memberId: String, name: String, size: Dp)` (öffentlich): `val directory = koinInject<AvatarDirectory>()`,
    `val looks by directory.looks.collectAsStateWithLifecycle()`, `LaunchedEffect(Unit) { directory.ensureLoaded() }`, `AvatarImage(looks[memberId], name, size = size)`.
  - Tasks: `BoardRoute(…, laneAvatar: @Composable (memberId: String, name: String) -> Unit = { _, _ -> })` → `BoardScreen` → `BoardTabs`:
    `BoardTab.Lane` → `Tab(text = { Row(verticalAlignment = CenterVertically, horizontalArrangement = spacedBy(6.dp)) { laneAvatar(tab.lane.memberId, tab.lane.name); Text(tab.lane.name) } })`.
    `tasksGraph(navController, laneAvatar = …)` mit gleichem Default.
  - App `AppNavHost`: `tasksGraph(navController, laneAvatar = { id, name -> MemberAvatar(id, name, LaneAvatarSize) })` (`private val LaneAvatarSize = 24.dp`),
    `todayContent` + `TodayAvatarRoute(onOpenAvatar = { navController.navigateToTopLevel(TopLevelDestination.ME); navController.openAvatarEditor() })`
    als **erste** Karte, `gamificationGraph` reicht `openAvatarEditor` an `MeRoute`.
- **MIRROR**: SLOT_PATTERN, `TodaySavingsRoute`/`ViewModel`.
- **GOTCHA**: `BoardScreen`-Tests und `ScreensTest` rufen die Composables ohne neuen Parameter → Defaults setzen. `MeViewModel`/`FamilyPointsViewModel`-
  Konstruktor ändert sich → `ViewModelsTest`, `RewardViewModelsTest`, `GamificationModuleTest`, `AppNavHostTest` (Zeilen ~226-235) anpassen.
  `koinInject` in `MemberAvatar` → im `AppNavHostTest` `single { AvatarDirectory(fakeRepo, fakeCache) }` registrieren.
- **VALIDATE**: `./gradlew :feature:gamification:testDebugUnitTest :feature:tasks:testDebugUnitTest :app:testDebugUnitTest`.

### Task 8: Koin + Strings + Version
- **ACTION**: `GamificationModule.kt`, `strings.xml`, `app/build.gradle.kts`.
- **IMPLEMENT**:
  ```kotlin
  private const val AVATAR_CACHE_FILE = "game_avatars.json"
  single<AvatarRepository> { RemoteAvatarRepository(get(), get()) }
  single<AvatarCache> { DataStoreAvatarCache(DataStoreFactory.create(serializer = AvatarCacheSerializer(get()),
      produceFile = { androidContext().dataStoreFile(AVATAR_CACHE_FILE) })) }
  single { AvatarDirectory(get(), get()) }
  factory { LoadAvatarEditorUseCase(get(), get()) }
  factory { AvatarActions(get(), get()) }
  viewModel { AvatarEditorViewModel(get(), get(), get()) }
  viewModel { TodayAvatarViewModel(get(), get()) }
  viewModel { MeViewModel(get(), get(), get()) }; viewModel { FamilyPointsViewModel(get(), get(), get(), get()) }
  ```
  Strings (deutsch): `game_avatar_title`, `game_avatar_open`, `game_avatar_setup`, `game_avatar_mine` („Dein Avatar“), `game_avatar_buy`,
  `game_avatar_buy_title`, `game_avatar_buy_text`, `game_avatar_buy_confirm`, `game_avatar_goal_warning`, `game_avatar_save`, `game_avatar_saved`,
  `game_avatar_owned` („gehört dir“), `game_avatar_none` („Nichts“), Rubriken `game_rubric_skin` „Hautfarbe“, `game_rubric_hair` „Frisur“,
  `game_rubric_headwear` „Kopfbedeckung“, `game_rubric_hair_color` „Haarfarbe“, `game_rubric_hat_color` „Mützenfarbe“, `game_rubric_eyes` „Augen“,
  `game_rubric_eyebrows` „Augenbrauen“, `game_rubric_mouth` „Mund“, `game_rubric_clothes` „Kleidung“, `game_rubric_clothes_color` „Kleidungsfarbe“,
  `game_rubric_graphic` „Motiv“, `game_rubric_accessories` „Brille“, `game_rubric_facial_hair` „Bart“, `game_rubric_background` „Hintergrund“.
  `versionCode = 14`, `versionName = "0.13.0"`.
- **GOTCHA**: Reihenfolge der Koin-Deklarationen egal; `GamificationModuleTest.testOverrides` braucht `single<AvatarCache> { InMemoryAvatarCache() }`.
- **VALIDATE**: `GamificationModuleTest` grün (neue VMs ergänzt).

### Task 9: Tests vervollständigen + Qualität
- **ACTION**: Alle Tests aus der Teststrategie, Fakes `fakes/AvatarFakes.kt` (`FakeAvatarRepository` mit `var …Result`-Feldern und `saveCalls`/`purchaseCalls`,
  `InMemoryAvatarCache`, `catalog()`-Fixture mit kleinem Katalog: skin (2 frei), top (HAIR: shortFlat frei, bigHair 20; HEADWEAR: hijab frei, winterHat02 30),
  hairColor/hatColor/eyes/eyebrows/mouth/clothes/clothesColor/background je 1 frei, accessories (optional: wayfarers 25), clothesGraphic, facialHair optional leer).
- **VALIDATE**: `cd android && ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerifyDebug assembleDebug`.

### Task 10: Doku
- **ACTION**:
  - `docs/03-design/gamification.md`: neuer Abschnitt „App-Seite Avatar & Shop (Phase 4b)“ im Format von 3b (Tabelle Bildschirm/Inhalt, Regeln Speichern/Kauf/Hinweis,
    lokaler Stand `game_avatars.json` v1, Board-Slot, Tests); Satz in „Avatar & Shop (Phase 4a)“ „folgen in Phase 4b“ → Verweis auf neuen Abschnitt; Kopf „Die App-Seite ist …“ ergänzen.
  - `docs/04-verification/traceability.md`: REQ-GAME-030/031 App-Tests eintragen, Status „umgesetzt (Backend 4a + App 4b; Abnahme Testhandy offen)“;
    REQ-GAME-041 Hinweis „2. Kriterium folgt mit Phase 4“ ersetzen (`AvatarRulesTest`, `AvatarViewModelsTest`, `AvatarScreensTest`); REQ-GAME-014 `ScreensTest` (Avatar).
  - PRD Phase 4: Status `complete` erst nach Abnahme auf Testhandy – nach Implementierung: „4b: umgesetzt, Plan `.claude/PRPs/plans/completed/avatar-shop-app.plan.md`,
    Bericht `.claude/PRPs/reports/avatar-shop-app-report.md` (Abnahme Testhandy offen)“.
  - ADR-0017 Nr. 6: „(Familie → Über)“ → „(Editor-Fußzeile und Familie → Punkte)“.
- **VALIDATE**: Links prüfen (`grep -n "avatar-shop-app" docs -r`).

---

## Testing Strategy

### Unit Tests

| Test (Datei) | Input | Expected Output | Edge? |
|---|---|---|---|
| `AvatarRulesTest` `REQ-GAME-030 default look takes first free item per required slot` | Fixture-Katalog | skin/top/… = erstes kostenloses, optionale `null` | |
| `AvatarRulesTest` `REQ-GAME-030 rubrics split top into hair and headwear` | Fixture | Rubriken in Slot-Reihenfolge, `top` zweimal (HAIR, HEADWEAR) | |
| `AvatarRulesTest` `REQ-GAME-031 balance 10 item 8 is buyable` | owned ∅, balance 10, price 8 | `Buyable(8)` | |
| `AvatarRulesTest` `REQ-GAME-031 balance 5 item 8 shows 3 missing` | balance 5 | `Missing(3)` | ✓ |
| `AvatarRulesTest` `REQ-GAME-031 owned or free item is wearable not buyable` | id ∈ owned / price 0 | `Wearable` | ✓ |
| `AvatarRulesTest` `REQ-GAME-041 warning when purchase lowers goal progress` | Ziel 200, balance 120, price 30 | `true`; balance 260 price 30 → `false`; kein Ziel / archiviert → `false` | ✓ |
| `AvatarRulesTest` `REQ-GAME-031 cannot save draft with unowned item` | Entwurf trägt bigHair, owned ∅ | `false`; nach owned+bigHair → `true` | ✓ |
| `AvatarRulesTest` `REQ-GAME-030 first save allowed with default look; unchanged saved look not` | saved null / saved == draft | `true` / `false` | ✓ |
| `AvatarRulesTest` `wearing and itemIn cover all slots` | jeder Slot | Roundtrip gleich | |
| `RemoteAvatarRepositoryTest` catalog/avatars/profile(null avatar)/save body/purchase 201/purchase 409 → `Conflict` mit Servertext/unknown enum → `Server` | MockWebServer | wie beschrieben | ✓ |
| `DataStoreAvatarCacheTest` write→read, falsche Version → `null`, kaputtes JSON → `null` | | | ✓ |
| `AvatarDirectoryTest` refresh ok schreibt Cache; Netzwerkfehler → Cache; 401 → Cache leer; `ensureLoaded` lädt nur einmal nach Erfolg; `update` mischt ein | | | ✓ |
| `AvatarUseCasesTest` Editor lädt parallel, erster Fehler gewinnt; Katalog ohne freies Pflicht-Teil → `Server`; `save` aktualisiert Directory | | | ✓ |
| `AvatarViewModelsTest` `REQ-GAME-030 child without avatar gets default draft`; `REQ-GAME-031 picking item previews it`; `REQ-GAME-031 purchase lowers balance and owns item` (12 → Kauf 10 → 2, owned); `REQ-GAME-031 409 reloads and keeps draft`; `REQ-GAME-030 save updates directory`; `REQ-GAME-041 confirm shows goal warning`; Eltern → `Forbidden` | Fakes | | ✓ |
| `AvatarScreensTest` (Robolectric) Editor zeigt Rubrik-Tabs + „Noch 3 Punkte“; Kauf-Dialog mit Warntext; Speichern deaktiviert bei ungekauftem Teil; `MyAvatarCard` „Richte deinen Avatar ein“; `TodayAvatarCard` nur Kind; Fußzeile „Avataaars“ | | | |
| `ScreensTest` `REQ-GAME-014 leaderboard shows avatar per child` | `looks` für c-1 | Knoten mit `contentDescription` „Avatar von Lea“ | |
| `BoardScreenTest` (tasks) `lane tab renders avatar slot` | `laneAvatar = { _, n -> Text("A:$n") }` | „A:Lea“ sichtbar | |
| `GamificationModuleTest` | Koin | `AvatarEditorViewModel`, `TodayAvatarViewModel`, `AvatarDirectory` auflösbar | |
| `AppNavHostTest` | bestehende Fälle | weiterhin grün (Koin ergänzt) | |
| `AvatarImageSpikeTest` (core:ui) | asynchrones Rendern | weiterhin Bitmaps ≠ Initiale | |

### Edge Cases Checklist
- [x] Kind ohne gespeicherten Avatar (Standard-Entwurf, erstes Speichern erlaubt)
- [x] Guthaben genau gleich Preis (`Buyable`), Guthaben 0
- [x] Optionaler Slot „Nichts“
- [x] Doppelter Kauf / Guthaben inzwischen zu niedrig (409 → Reload, Entwurf bleibt)
- [x] Unbekannte Item-ID im gespeicherten Look (Renderer-Fallback, `canSave` nur bei Entwurf)
- [x] Offline: Ich/Heute/Rangliste aus `game_avatars.json`, Editor-Einstieg deaktiviert
- [x] Eltern: kein Editor, keine Ich/Heute-Karte, Avatare sichtbar
- [x] Geräte-Abmeldung (401) leert Avatar-Stand

---

## Validation Commands

### Static Analysis
```bash
cd android && ./gradlew ktlintCheck detekt lintDebug
```
EXPECT: keine Befunde

### Unit Tests
```bash
cd android && ./gradlew :core:ui:testDebugUnitTest :feature:gamification:testDebugUnitTest :feature:tasks:testDebugUnitTest :app:testDebugUnitTest
```
EXPECT: alle grün

### Full Test Suite (wie CI `android.yml`)
```bash
cd android && ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerifyDebug assembleDebug
```
EXPECT: grün, Coverage-Schwelle gehalten

### Database Validation
N/A – keine Migration (Backend unverändert).

### Manual Validation (Testinstanz oder lokal `:app:run` + Emulator/Testhandy)
- [ ] Kind öffnet „Ich“ → „Richte deinen Avatar ein“ → Editor zeigt Standard-Avatar; Rubrik wechseln, Teil antippen → Vorschau ändert sich
- [ ] Speichern → zurück auf „Ich“: Avatar in Karte und Rangliste; Board-Tab des Kindes zeigt Avatar; „Heute“ zeigt Karte
- [ ] Kaufbares Teil antippen → Preis, „Kaufen für …“; mit Sparziel und knappem Guthaben erscheint der Hinweis; Kauf → Guthaben sinkt, Gesammelt/Rangliste unverändert, Verlauf „Shop: …“
- [ ] Teil zu teuer → „Noch x Punkte“, Kauf-Button deaktiviert
- [ ] Gekauftes Teil ablegen/anlegen und speichern ohne erneuten Kauf
- [ ] Flugmodus: Rangliste/Board/Heute zeigen Avatare, Editor-Einstieg deaktiviert
- [ ] Elternhandy: Rangliste unter „Familie“ mit Avataren, Namensnennung am Ende des Punkte-Abschnitts, kein Editor

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt
- [ ] CI-Befehl grün
- [ ] Tests mit REQ-IDs im Namen
- [ ] Keine API-Änderung, `tasks` importiert nichts aus `gamification`
- [ ] UX wie oben

## Completion Checklist
- [ ] Muster (State/Event, `apiCall`, Cache, Slots) eingehalten
- [ ] Fehler über `AppError`/`ErrorText`, Cache-Fehler nur `Log.w`
- [ ] Keine hartcodierten Texte (Strings), Größen als `private val …Size`
- [ ] Doku (Design, Traceability, PRD, ADR) aktualisiert
- [ ] Self-contained

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Asynchrones Rendern bricht `AvatarImageSpikeTest`/Robolectric-Screenshots | M | M | Test wartet per `waitUntil`; Fallback im Test, nicht im Code |
| Ruckeln im Raster bei vielen Mini-Avataren | M | M | Task 1 (off-main), Farbkreise statt SVG, Kacheln 56 dp |
| Konstruktoränderungen `MeViewModel`/`FamilyPointsViewModel` brechen App-Test-Koin | H | L | Task 7 GOTCHA: `AppNavHostTest` gezielt ergänzen |
| `FlowRow` in `LazyColumn` mit ~30 Kacheln (Frisuren) | L | L | `FlowRow` ist nicht lazy, aber begrenzt (≤ 35 Teile je Rubrik) |
| Kind verlässt Editor nach Kauf ohne Speichern | M | L | Teil bleibt gekauft; Hinweistext nach Kauf „Zum Tragen speichern“ (`game_avatar_saved` nur nach Speichern) – bewusst kein Dialog |

## Notes
- Aufteilung 4a/4b mit Nutzer am 2026-11-01 entschieden; 4a lieferte Vertrag 0.10.0, Renderer und Katalog (157 Teile, 60 kostenpflichtig, 10–30 Punkte).
- Umsetzung in neuer Session (Sonnet) mit `/ecc:prp-implement .claude/PRPs/plans/avatar-shop-app.plan.md`.
- Nach Umsetzung: Plan nach `.claude/PRPs/plans/completed/` verschieben, Bericht `.claude/PRPs/reports/avatar-shop-app-report.md`.
