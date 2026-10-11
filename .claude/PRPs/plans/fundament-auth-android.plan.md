# Plan: Fundament & AUTH – Android-App (Phase 1b)

## Summary
Legt das Android-Grundgerüst an (Gradle-Multi-Modul, Jetpack Compose, Clean Architecture, Koin) mit aus
`api/openapi.yaml` generiertem Retrofit-Client, verschlüsselter Token-Ablage, HTTPS mit **gepinntem** selbstsigniertem
Zertifikat (ADR-0006) und den AUTH-Abläufen: Erst-Einrichtung, Gerät per Kopplungscode koppeln, „Angemeldet als …“,
sowie für Eltern die Verwaltung von Mitgliedern, Kopplungscodes und Geräten. Dazu eine Android-CI.
Danach ist Phase 1 des Essensplaners abgeschlossen.

## User Story
Als Familienmitglied möchte ich mein Handy einmalig per Kopplungscode mit dem Familienplaner verbinden, und als
Elternteil möchte ich Mitglieder anlegen, Kopplungscodes erzeugen und Geräte entfernen — ohne `curl`, direkt in der App.

## Problem → Solution
Backend läuft (Phase 1a), Bedienung nur per `curl`; `android/` enthält nur eine README →
Installierbare App, mit der sich alle fünf Familienhandys koppeln, Rollen in der Oberfläche wirken und jeder Commit
auf `android/**` in der CI gebaut, gelintet und getestet wird.

## Metadata
- **Complexity**: Large (Greenfield-Android, ~60 Dateien, jede klein)
- **Source PRD**: `docs/01-requirements/meals.prd.md` (Anforderungen: `docs/01-requirements/auth.prd.md`)
- **PRD Phase**: 1 – Fundament & AUTH, Teil **1b** (Android)
- **Estimated Files**: ~60 (inkl. Gradle-Wrapper, Ressourcen, Tests, Doku)

---

## UX Design

### Before
```
Elternhandy / Kinderhandy:  –  (keine App)
Einrichtung:                curl -k -X POST https://planner.home.arpa:8095/api/v1/setup …
Kind koppeln:               curl … /members, curl … /pairing-codes, curl … /devices/pair
```

### After
```
┌──────────────────────────┐   Start ohne Token
│ Familienplaner           │──────────────────────────────┐
│                          │                              ▼
│ [Mit Code verbinden]     │──▶ ┌──────────────────────────────┐
│  Familie einrichten ›    │    │ Kopplungscode   [ _ _ _ _ _ _ ]│
└──────────┬───────────────┘    │ Gerätename      [Pixel 8     ]│
           │                    │ [Verbinden]                   │
           ▼                    │ Fehler: „Code ungültig oder   │
┌──────────────────────────┐    │ abgelaufen“ / „Server nicht   │
│ Erst-Einrichtung         │    │ erreichbar – im Heim-WLAN?“   │
│ Setup-Token [•••••••••]  │    └──────────────┬───────────────┘
│ Dein Name   [Mama      ] │                   │ Erfolg → Token verschlüsselt speichern
│ Gerätename  [Pixel 8   ] │                   ▼
│ [Einrichten]             │──▶ ┌──────────────────────────────┐
└──────────────────────────┘    │ Hallo Lea 👋   (Kind)         │
                                │ Weitere Funktionen folgen.    │
                                │ [Familie verwalten] (nur Eltern)
                                └──────────────┬───────────────┘
                                               ▼ (Eltern)
                                ┌──────────────────────────────┐
                                │ Familie                       │
                                │  Mama (Elternteil)            │
                                │  Lea (Kind)     [Code]        │──▶ Dialog: „483920 – gültig bis 18:42“
                                │  [+ Mitglied]                 │──▶ Dialog: Name, Rolle
                                │ Geräte                        │
                                │  Pixel 8 (Lea)  zuletzt …  [🗑]│──▶ Bestätigung „Gerät entfernen?“
                                └──────────────────────────────┘
Token später ungültig (401, z. B. Gerät entfernt) → Session löschen → Startseite mit Hinweis
„Dieses Gerät wurde abgemeldet. Bitte neu koppeln.“
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Erst-Einrichtung | curl mit Setup-Token | Screen „Familie einrichten“ | Server antwortet 409, wenn schon eingerichtet → Hinweis „Bereits eingerichtet – bitte mit Code verbinden“ |
| Gerät koppeln | curl | Screen „Mit Code verbinden“ | Gerätename vorbelegt mit `Build.MODEL` |
| Wer bin ich | curl `/me` | Startseite „Hallo <Name>“ + Rolle | `/me` bei jedem App-Start (still) |
| Mitglied anlegen | curl | Dialog in „Familie“ (Eltern) | 409 → „Name existiert bereits“ |
| Kopplungscode | curl | Button je Mitglied → Code + Ablaufzeit | Neuer Code macht alten ungültig (Hinweistext) |
| Gerät entfernen | curl | Papierkorb + Bestätigung (Eltern) | Eigenes Gerät: zusätzliche Warnung |
| Abgemeldet | – | Automatischer Rücksprung zur Startseite | bei 401 auf geschütztem Endpunkt |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `CLAUDE.md` | all | Stack, API-first, Doku-/V-Modell, Port 8095 |
| P0 | `api/openapi.yaml` | all | Quelle der Wahrheit; alle Endpunkte/Schemas, die die App nutzt |
| P0 | `docs/01-requirements/auth.prd.md` | all | REQ-AUTH-001/002/003 inkl. Abnahmekriterien |
| P0 | `docs/02-architecture/adr/0006-network-access-lan-only.md`, `0008-device-pairing.md` | all | TLS-Pinning, Server-URL aus Build-Config, Kopplungsablauf |
| P0 | `.claude/rules/ecc/kotlin/*.md` | all | kein `!!`, sealed + exhaustive `when`, Fakes statt Mocks, Turbine, Token sicher speichern, Timeouts |
| P1 | `backend/api-contract/build.gradle.kts` | all | **Exakt dieselbe Generator-Konfiguration** spiegeln (typeMappings, dateLibrary, enumPropertyNaming) |
| P1 | `backend/core/src/main/kotlin/de/familyplanner/core/error/AppException.kt` | all | ErrorCode ↔ HTTP-Status, den die App zurückübersetzt |
| P1 | `backend/buildSrc/src/main/kotlin/familyplanner.kotlin-jvm.gradle.kts`, `backend/config/detekt/detekt.yml` | all | Convention-Plugin- und Detekt-Muster spiegeln |
| P1 | `.github/workflows/backend.yml` | all | CI-Muster (paths-Filter, concurrency, Report-Upload) |
| P2 | `deploy/gen-cert.sh`, `deploy/README.md` | all | Woher `planner.crt` kommt (CN/SAN = `planner.home.arpa` + IP) |
| P2 | `docs/04-verification/traceability.md`, `docs/03-design/core.md` | all | Matrix + Design ergänzen |
| P2 | `.claude/PRPs/reports/fundament-auth-backend-report.md` | all | Abweichungen aus 1a (UUID → String, kein `ErrorResponse.data`) |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| AGP 9 | https://developer.android.com/build/releases/about-agp | Aktuelle Linie 9.x (Beispiele nutzen 9.2.0). **AGP 9 hat eingebautes Kotlin** → Plugin `org.jetbrains.kotlin.android` wird in Android-Modulen *nicht* mehr angewendet; nur `org.jetbrains.kotlin.plugin.compose` und `…plugin.serialization`. Versionen beim Implementieren in den Release Notes prüfen, keine dynamischen Versionen. |
| Token-Speicher | https://blog.includesecurity.com/2026/08/encryptedsharedpreferences-is-dead-heres-what-you-should-use-instead/ , https://mas.owasp.org/MASTG/best-practices/MASTG-BEST-0050/ | `androidx.security:security-crypto` (EncryptedSharedPreferences) ist **deprecated**. Ersatz: **DataStore + Tink** (`androidx.datastore:datastore-tink` mit `AeadSerializer`; Keyset per `AndroidKeysetManager` im Android Keystore). Ist `datastore-tink` noch alpha → Tink `Aead` direkt um einen eigenen `Serializer` wickeln (gleiches Ergebnis). |
| OpenAPI Generator `jvm-retrofit2` | https://openapi-generator.tech/docs/generators/kotlin/ | `library=jvm-retrofit2`, `serializationLibrary=kotlinx_serialization`, `useCoroutines=true` → `suspend`-Methoden mit `retrofit2.Response<T>`. Generierte Infrastruktur (`ApiClient`) **nicht** verwenden; Retrofit selbst bauen. Ausgabe nach dem ersten Lauf ansehen. |
| Retrofit + kotlinx.serialization | https://github.com/square/retrofit (Modul `converter-kotlinx-serialization`) | Offizieller Converter: `Json.asConverterFactory("application/json".toMediaType())`. |
| Network Security Config | https://developer.android.com/privacy-and-security/security-config | `domain-config` mit `<trust-anchors><certificates src="@raw/planner_cert"/></trust-anchors>` vertraut nur diesem Zertifikat für den Host; `cleartextTrafficPermitted="false"` in `base-config`. Datei je Build-Typ überschreibbar (`src/debug/res/xml/…`). |
| Navigation Compose | https://developer.android.com/guide/navigation/design/type-safety | Typsichere Routen als `@Serializable object/data class`. |

---

## Patterns to Mirror

> Die App ist Greenfield. Gespiegelt werden (a) die **realen Backend-Muster** (SOURCE = bestehende Datei) und (b) die
> hier **neu festgelegten Android-Muster** (SOURCE = Datei, die dieser Plan anlegt). Spätere Feature-Module
> (`feature:meals`) spiegeln genau diese.

### NAMING_CONVENTION
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/pairing/PairingService.kt:18-24 (real)
```kotlin
class PairingService(
    private val members: MemberRepository,
    private val codes: PairingCodeRepository,
    private val secrets: SecretGenerator,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
```
Android-Übertragung:
- Basis-Package `de.familyplanner.android`; je Modul `de.familyplanner.android.<modul>` (`core.api` ist generiert:
  `de.familyplanner.api` / `de.familyplanner.api.model` — **identisch zum Backend**).
- In `feature:auth` Unterpakete nach Schicht: `domain` (Modelle, Repository-Interface, UseCases), `data`
  (`RemoteAuthRepository`), `ui.<screen>` (`PairScreen.kt`, `PairViewModel.kt`, `PairUiState.kt`).
- Interfaces ohne Präfix (`AuthRepository`, `SessionStore`), Implementierung mit Technik-Präfix
  (`RemoteAuthRepository`, `DataStoreSessionStore`) — wie `ExposedMemberRepository` im Backend.
- Composables `PascalCase` (`PairScreen`), Routen `@Serializable data object PairRoute`.
- UI-Texte deutsch in `res/values/strings.xml`, Code/Bezeichner englisch.

### ERROR_HANDLING
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/error/AppException.kt:3-14 (real — Gegenstück auf Serverseite)
```kotlin
enum class ErrorCode(val httpStatus: Int) {
    VALIDATION_FAILED(400), UNAUTHORIZED(401), INVALID_PAIRING_CODE(401), FORBIDDEN(403),
    NOT_FOUND(404), CONFLICT(409), RATE_LIMITED(429), SETUP_DISABLED(503), INTERNAL(500),
}
```
// SOURCE: android/core/data/src/main/kotlin/de/familyplanner/android/core/data/ApiResult.kt (neu)
```kotlin
sealed interface AppError {
    data object Network : AppError              // IOException, Host nicht erreichbar → „Im Heim-WLAN?“
    data object Certificate : AppError          // SSLPeerUnverifiedException / SSLHandshakeException
    data object Unauthorized : AppError         // 401 UNAUTHORIZED → Session löschen
    data object InvalidPairingCode : AppError   // 401 INVALID_PAIRING_CODE
    data object Forbidden : AppError
    data object NotFound : AppError
    data object RateLimited : AppError
    data object SetupDisabled : AppError
    data class Validation(val message: String) : AppError
    data class Conflict(val message: String) : AppError
    data object Server : AppError               // 5xx/INTERNAL, unlesbare Antwort
}

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: AppError) : ApiResult<Nothing>
}
```
// SOURCE: android/core/data/src/main/kotlin/de/familyplanner/android/core/data/ApiCall.kt (neu)
```kotlin
suspend fun <E, T> apiCall(
    json: Json,
    call: suspend () -> Response<E>,
    unwrap: (E) -> T?,
): ApiResult<T> =
    try {
        val response = call()
        val body = response.body()
        val data = body?.let(unwrap)
        when {
            response.isSuccessful && data != null -> ApiResult.Success(data)
            response.isSuccessful -> ApiResult.Failure(AppError.Server)
            else -> ApiResult.Failure(response.toAppError(json))   // liest ErrorResponse aus errorBody()
        }
    } catch (e: CancellationException) {
        throw e                                                    // Regel: nie verschlucken
    } catch (e: SSLException) {
        ApiResult.Failure(AppError.Certificate)
    } catch (e: IOException) {
        ApiResult.Failure(AppError.Network)
    } catch (e: SerializationException) {
        ApiResult.Failure(AppError.Server)
    }
```
- `toAppError` mappt den **generierten** `de.familyplanner.api.model.ErrorCode` aus `ErrorResponse.error.code`
  (exhaustive `when`, kein `else`); ist der Body unlesbar → nach HTTP-Status (401 → `Unauthorized`, 5xx → `Server`).
- Unit-Variante für `204 No Content` (`revokeDevice`): `apiCallNoContent(json) { api.revokeDevice(id) }` → `ApiResult<Unit>`.
- Jede `AppError` hat genau **eine** Nutzermeldung: `fun AppError.messageRes(): Int` in `core:ui`
  (`R.string.error_network` = „Server nicht erreichbar. Bist du im Heim-WLAN?“ usw.). `Validation`/`Conflict`
  zeigen die (deutsche) Servermeldung.

### API_ENVELOPE
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/web/Responses.kt:11-17 (real)
```kotlin
suspend fun ApplicationCall.respondError(code: ErrorCode, message: String) = respond(
    HttpStatusCode.fromValue(code.httpStatus),
    ErrorResponse(success = false, error = ApiError(code = ApiErrorCode.valueOf(code.name), message = message)),
)
```
App-Seite: Erfolgsantworten sind `XxxResponse(success, data?, error?)` → `unwrap = { it.data }`; Fehlerantworten
sind `ErrorResponse(success=false, error)` im `errorBody()`.

### LOGGING_PATTERN
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/pairing/PairingService.kt:25,32 (real)
```kotlin
private val log = LoggerFactory.getLogger(PairingService::class.java)
log.info("Pairing code issued for member {}", memberId.value)
```
Android: kein Logging-Framework (YAGNI). `android.util.Log` nur in `data`-Schicht bei unerwarteten Fehlern
(`Log.w(TAG, "…", e)`), **niemals** Token, Setup-Token oder Kopplungscode loggen; OkHttp-`HttpLoggingInterceptor`
nur in Debug und mit `redactHeader("Authorization")` und Level `BASIC`.

### REPOSITORY_PATTERN
// SOURCE: android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/domain/AuthRepository.kt (neu)
```kotlin
interface AuthRepository {
    suspend fun setup(setupToken: String, parentName: String, deviceName: String): ApiResult<Session>
    suspend fun pair(code: String, deviceName: String): ApiResult<Session>
    suspend fun me(): ApiResult<Me>
}
```
// SOURCE: android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/data/RemoteAuthRepository.kt (neu)
```kotlin
class RemoteAuthRepository(
    private val api: AuthApi,          // generiert (core:api)
    private val json: Json,
) : AuthRepository {
    override suspend fun pair(code: String, deviceName: String): ApiResult<Session> =
        apiCall(json, { api.pairDevice(PairDeviceRequest(code = code, deviceName = deviceName)) }) { it.data?.toSession() }
}
```
- Mapper generiert → Domäne als Extension in `data/Mappers.kt` (`PairingResult.toSession()`, `Member.toDomain()`).
- Die Domäne kennt **keine** generierten Typen (Rolle als eigenes `enum class Role { PARENT, CHILD }`).

### SESSION_PATTERN
// SOURCE: android/core/data/src/main/kotlin/de/familyplanner/android/core/data/session/SessionStore.kt (neu)
```kotlin
data class Session(val token: String, val memberId: String, val memberName: String, val role: Role, val deviceId: String)

interface SessionStore {
    val session: Flow<Session?>
    suspend fun save(session: Session)
    suspend fun clear()
}
```
- `DataStoreSessionStore`: DataStore mit Tink-`Aead`-verschlüsseltem Serializer (JSON des `Session`-Objekts),
  Keyset im Android Keystore (`AndroidKeysetManager`, Master-Key-URI `android-keystore://fp_session_key`).
- `AuthInterceptor` (OkHttp) liest das Token **synchron aus einem In-Memory-Cache** (`StateFlow` im
  `SessionStore`-Wrapper), nie `runBlocking` auf DataStore pro Request.
- `UnauthorizedInterceptor`: Antwort 401 auf einem Request **mit** Token → `SessionStore.clear()` +
  Ereignis `SessionEvent.SignedOut` (SharedFlow), das die Navigation zur Startseite mit Hinweis auslöst.
  401 bei `/devices/pair` und `/setup` (ohne Token) löst das **nicht** aus.

### VIEWMODEL_PATTERN
// SOURCE: android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/ui/pair/PairViewModel.kt (neu)
```kotlin
data class PairUiState(
    val code: String = "",
    val deviceName: String = "",
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    val codeError: Boolean = false,
) {
    val canSubmit: Boolean get() = PAIRING_CODE_REGEX.matches(code) && deviceName.isNotBlank() && !isSubmitting
}

sealed interface PairEvent {
    data class CodeChanged(val value: String) : PairEvent
    data class DeviceNameChanged(val value: String) : PairEvent
    data object Submit : PairEvent
}

class PairViewModel(private val pairDevice: PairDeviceUseCase, defaultDeviceName: String) : ViewModel() {
    private val _state = MutableStateFlow(PairUiState(deviceName = defaultDeviceName))
    val state = _state.asStateFlow()

    fun onEvent(event: PairEvent) {
        when (event) {
            is PairEvent.CodeChanged -> _state.update { it.copy(code = event.value.filter(Char::isDigit).take(6), error = null) }
            is PairEvent.DeviceNameChanged -> _state.update { it.copy(deviceName = event.value.take(DEVICE_NAME_MAX)) }
            PairEvent.Submit -> submit()
        }
    }
}
```
- Erfolg führt **nicht** über ein Navigations-Event aus dem ViewModel: die App-Navigation beobachtet
  `SessionStore.session` (null → Auth-Graph, sonst Home). Das hält ViewModels navigationsfrei.
- Eingabelimits wie im Vertrag: Name 1–40, Gerätename 1–60, Code `^[0-9]{6}$`, Setup-Token ≥ 16 → Konstanten in
  `feature/auth/domain/AuthLimits.kt`.

### USECASE_PATTERN
// SOURCE: android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/domain/PairDeviceUseCase.kt (neu)
```kotlin
class PairDeviceUseCase(private val repository: AuthRepository, private val sessions: SessionStore) {
    suspend operator fun invoke(code: String, deviceName: String): ApiResult<Session> =
        repository.pair(code.trim(), deviceName.trim()).also { result ->
            if (result is ApiResult.Success) sessions.save(result.data)
        }
}
```

### DI_PATTERN
// SOURCE: android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/AuthModule.kt (neu)
```kotlin
val authModule = module {
    single<AuthRepository> { RemoteAuthRepository(get(), get()) }
    single<FamilyRepository> { RemoteFamilyRepository(get(), get()) }
    factory { PairDeviceUseCase(get(), get()) }
    viewModel { PairViewModel(get(), defaultDeviceName = Build.MODEL) }
}
```
Koin-Module je Gradle-Modul (`networkModule`, `sessionModule` in `core:data`; `authModule` in `feature:auth`),
gestartet in `FamilyPlannerApp : Application`.

### TEST_STRUCTURE
// SOURCE: backend/core/src/test/kotlin/de/familyplanner/core/pairing/PairingServiceTest.kt:21-40 (real)
```kotlin
class PairingServiceTest {
    private val clock = MutableClock(Instant.parse("2026-10-12T10:00:00Z"))
    private val store = InMemoryStore()
    ...
    @Test
    fun `REQ-AUTH-002 pairing code has six digits and expires in 15 minutes`() =
        runTest {
            // Arrange
            val child = members.create(aMember(role = Role.CHILD))
            // Act
            val issued = service.createCode(child.id)
            // Assert
            assertTrue(Regex("^\\d{6}$").matches(issued.code))
        }
```
Android-Übertragung (JVM-Unit-Tests unter `src/test/kotlin`, JUnit 4 + `kotlin.test`-Assertions):
- Testnamen beginnen mit REQ-ID, wo eine Anforderung verifiziert wird (`REQ-AUTH-002 invalid code shows pairing error`).
- AAA-Kommentare, Fakes statt Mocks (`FakeAuthRepository`, `InMemorySessionStore` in `src/test/.../fakes/`; geteilte
  Fakes für mehrere Module in einem `testFixtures`-artigen Modul `:core:testing`).
- ViewModels: `Turbine` + `MainDispatcherRule` (setzt `Dispatchers.setMain(UnconfinedTestDispatcher())`).
- Data-Schicht: **MockWebServer** (`mockwebserver3`) + echter Retrofit/OkHttp-Stack, Antworten als JSON aus dem
  Vertrag (z. B. `{"success":false,"error":{"code":"INVALID_PAIRING_CODE","message":"…"}}`).
- Compose-Screens: Robolectric + `createComposeRule()` (JVM, kein Emulator in CI).

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `docs/02-architecture/adr/0009-android-app-architecture.md` | CREATE | Neue Entscheidungen: Modulschnitt, Koin, Retrofit-Generierung, DataStore+Tink, Zertifikat im Repo, APK-Verteilung, minSdk |
| `docs/02-architecture/README.md` | UPDATE | ADR-0009 verlinken (falls dort gelistet) |
| `docs/03-design/core.md` | UPDATE | Abschnitt „App-Seite (Phase 1b)“: Session, Fehlerabbildung, Screens |
| `docs/04-verification/traceability.md` | UPDATE | REQ-AUTH-001..003: App-Tests + E2E (manuell) eintragen |
| `docs/01-requirements/meals.prd.md` | UPDATE | Phase-1-Zeile: 1b-Plan verlinken; nach Umsetzung `complete` |
| `android/README.md` | UPDATE | Build, Server-URL, Zertifikat tauschen, Signieren, Installieren |
| `android/settings.gradle.kts` | CREATE | Module `:app`, `:core:api`, `:core:data`, `:core:ui`, `:core:testing`, `:feature:auth` |
| `android/build.gradle.kts` | CREATE | Plugins `apply false` |
| `android/gradle.properties` | CREATE | `kotlin.code.style=official`, `android.useAndroidX=true`, jvmargs, caching |
| `android/gradle/libs.versions.toml` | CREATE | Versionskatalog (Android-eigener) |
| `android/gradlew`, `gradlew.bat`, `gradle/wrapper/*` | CREATE | Wrapper (Version passend zu AGP) |
| `android/buildSrc/settings.gradle.kts`, `build.gradle.kts` | CREATE | wie `backend/buildSrc` |
| `android/buildSrc/src/main/kotlin/familyplanner.android-library.gradle.kts` | CREATE | compileSdk/minSdk, JVM 21 Toolchain, ktlint, detekt, kover, Test-Deps |
| `android/buildSrc/src/main/kotlin/familyplanner.android-compose.gradle.kts` | CREATE | Compose-Compiler-Plugin + BOM + Robolectric-Test-Deps |
| `android/config/detekt/detekt.yml` | CREATE | Kopie von Backend + Compose-Ausnahmen (`FunctionNaming` für `@Composable`) |
| `android/core/api/build.gradle.kts` | CREATE | OpenAPI-Generator `jvm-retrofit2` |
| `android/core/api/src/test/.../GeneratedApiTest.kt` | CREATE | Generierte Modelle/Enums serialisieren wie Backend |
| `android/core/data/build.gradle.kts` | CREATE | Retrofit, OkHttp, DataStore, Tink, Koin |
| `android/core/data/src/main/kotlin/.../core/data/ApiResult.kt` | CREATE | `ApiResult`, `AppError` |
| `android/core/data/src/main/kotlin/.../core/data/ApiCall.kt` | CREATE | `apiCall`, `apiCallNoContent`, `Response.toAppError` |
| `android/core/data/src/main/kotlin/.../core/data/network/NetworkModule.kt` | CREATE | OkHttp (Timeouts, Interceptors), Retrofit, generierte APIs, `Json` |
| `android/core/data/src/main/kotlin/.../core/data/network/AuthInterceptor.kt` | CREATE | Bearer-Header aus Session-Cache |
| `android/core/data/src/main/kotlin/.../core/data/network/UnauthorizedInterceptor.kt` | CREATE | 401 mit Token → Session löschen + Event |
| `android/core/data/src/main/kotlin/.../core/data/network/ServerConfig.kt` | CREATE | `baseUrl` aus `BuildConfig`-Wert, injiziert |
| `android/core/data/src/main/kotlin/.../core/data/session/Session.kt`, `Role.kt` | CREATE | Domänenmodell Session/Rolle |
| `android/core/data/src/main/kotlin/.../core/data/session/SessionStore.kt` | CREATE | Interface + `SessionEvent` |
| `android/core/data/src/main/kotlin/.../core/data/session/DataStoreSessionStore.kt` | CREATE | DataStore + Tink-Aead, In-Memory-Cache |
| `android/core/data/src/main/kotlin/.../core/data/session/SessionSerializer.kt` | CREATE | verschlüsselter DataStore-Serializer |
| `android/core/data/src/test/kotlin/...` | CREATE | `ApiCallTest`, `AuthInterceptorTest`, `UnauthorizedInterceptorTest`, `SessionSerializerTest` |
| `android/core/ui/build.gradle.kts` | CREATE | Compose-Bibliothek |
| `android/core/ui/src/main/kotlin/.../core/ui/theme/{Theme,Color,Type}.kt` | CREATE | Material-3-Theme (dynamische Farben ab Android 12) |
| `android/core/ui/src/main/kotlin/.../core/ui/ErrorMessages.kt` | CREATE | `AppError.messageRes()` |
| `android/core/ui/src/main/kotlin/.../core/ui/components/{SubmitButton,ErrorText,ConfirmDialog}.kt` | CREATE | wiederverwendbare Bausteine |
| `android/core/ui/src/main/res/values/strings.xml` | CREATE | Fehlermeldungen |
| `android/core/testing/build.gradle.kts` + `src/main/kotlin/.../MainDispatcherRule.kt`, `InMemorySessionStore.kt` | CREATE | geteilte Test-Helfer |
| `android/feature/auth/build.gradle.kts` | CREATE | Feature-Modul |
| `android/feature/auth/src/main/kotlin/.../feature/auth/domain/*.kt` | CREATE | `AuthRepository`, `FamilyRepository`, `Me`, `FamilyMember`, `FamilyDevice`, `IssuedPairingCode`, `AuthLimits`, UseCases (`SetupFamily`, `PairDevice`, `RefreshMe`, `LoadFamily`, `CreateMember`, `CreatePairingCode`, `RevokeDevice`) |
| `android/feature/auth/src/main/kotlin/.../feature/auth/data/{RemoteAuthRepository,RemoteFamilyRepository,Mappers}.kt` | CREATE | Repos auf generierten APIs |
| `android/feature/auth/src/main/kotlin/.../feature/auth/ui/welcome/WelcomeScreen.kt` | CREATE | Start ohne Session (+ Hinweis „abgemeldet“) |
| `android/feature/auth/src/main/kotlin/.../feature/auth/ui/setup/{SetupScreen,SetupViewModel,SetupUiState}.kt` | CREATE | Erst-Einrichtung |
| `android/feature/auth/src/main/kotlin/.../feature/auth/ui/pair/{PairScreen,PairViewModel,PairUiState}.kt` | CREATE | Kopplung |
| `android/feature/auth/src/main/kotlin/.../feature/auth/ui/home/{HomeScreen,HomeViewModel}.kt` | CREATE | „Hallo <Name>“, `/me`-Refresh |
| `android/feature/auth/src/main/kotlin/.../feature/auth/ui/family/{FamilyScreen,FamilyViewModel,FamilyUiState,AddMemberDialog,PairingCodeDialog}.kt` | CREATE | Eltern-Verwaltung |
| `android/feature/auth/src/main/kotlin/.../feature/auth/AuthModule.kt`, `AuthNavigation.kt` | CREATE | Koin-Modul, Nav-Graph-Erweiterungen |
| `android/feature/auth/src/main/res/values/strings.xml` | CREATE | Texte |
| `android/feature/auth/src/test/kotlin/...` | CREATE | UseCase-, ViewModel-, Repository- (MockWebServer) und Robolectric-Screen-Tests |
| `android/app/build.gradle.kts` | CREATE | Application, `BuildConfig.FP_SERVER_URL`, Signing aus `local.properties`/Env, R8 |
| `android/app/proguard-rules.pro` | CREATE | Keep-Regeln für kotlinx.serialization/Retrofit-Interfaces |
| `android/app/src/main/AndroidManifest.xml` | CREATE | `INTERNET`, `networkSecurityConfig`, `allowBackup="false"` |
| `android/app/src/main/res/xml/network_security_config.xml` | CREATE | Cleartext aus, `planner.home.arpa` nur mit gepinntem Zertifikat |
| `android/app/src/debug/res/xml/network_security_config.xml` | CREATE | wie main + Cleartext nur für `10.0.2.2`/`localhost` (lokales Backend im Emulator) |
| `android/app/src/main/res/raw/planner_cert.crt` | CREATE | öffentliches Zertifikat aus `deploy/certs/planner.crt` des Pi |
| `android/app/src/main/res/values/{strings,themes}.xml`, `mipmap-*`/adaptive Icon | CREATE | App-Name „Familienplaner“, Icon |
| `android/app/src/main/kotlin/.../app/{FamilyPlannerApp,MainActivity,AppNavHost}.kt` | CREATE | Koin-Start, Single-Activity, Session-gesteuerte Navigation |
| `android/app/src/test/kotlin/.../app/AppNavHostTest.kt` | CREATE | Robolectric: Session null → Welcome, Session → Home |
| `.github/workflows/android.yml` | CREATE | Lint, Tests, Kover, `assembleDebug` |
| `.gitignore` | UPDATE | `android/keystore.properties` ergänzen (falls nicht durch `local.properties` abgedeckt) |

## NOT Building

- Offline-Cache / Room (kommt mit REQ-MEAL-002 in Phase 3).
- Benachrichtigungen, WorkManager, Hintergrund-Sync (Phase 6).
- Mitglieder umbenennen/löschen, Rollen ändern (auth.prd.md: YAGNI).
- Server-URL zur Laufzeit ändern / QR-Code-Kopplung — URL kommt aus Build-Config (ADR-0006).
- Play-Store, App-Bundle, automatische Verteilung/Updates der APK — Installation per `adb`/Datei (ADR-0009).
- Instrumentierte Tests auf Emulator in der CI (nur Robolectric/JVM); E2E manuell auf Geräten.
- Backend- oder Vertragsänderungen — **keine**; falls doch nötig: erst `api/openapi.yaml` (Skill `contract-first`).
- iOS, Tablet-Layouts, Dark-/Light-Feinschliff über Material-3-Standard hinaus.
- `feature:meals`-Modul (Phase 2/3).

---

## Step-by-Step Tasks

### Task 0: Voraussetzungen lokal
- **ACTION**: Android-SDK bereitstellen (lokal ist **keins** installiert: kein `ANDROID_HOME`, kein `adb`).
- **IMPLEMENT**: **Entschieden: Android Studio** (`brew install --cask android-studio`, am 2026-10-11 installiert).
  Der Nutzer führt einmal den Setup-Assistenten aus (SDK nach `~/Library/Android/sdk`, Emulator). Fehlende Pakete
  per `~/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-<compileSdk>"
  "build-tools;<aktuell>" "emulator" "system-images;android-<compileSdk>;google_apis;arm64-v8a"` nachinstallieren,
  Lizenzen akzeptieren, `android/local.properties` mit `sdk.dir=/Users/matthias/Library/Android/sdk` (git-ignoriert).
  JDK 21 ist vorhanden.
  Zertifikat: **liegt auf dem Pi bereits vor** (`/opt/containers/apps/family-planner/certs/planner.crt`). Der Nutzer
  kopiert es nach `android/app/src/main/res/raw/planner_cert.crt` (z. B. `scp dockerhome:/opt/containers/apps/family-planner/certs/planner.crt
  android/app/src/main/res/raw/planner_cert.crt`). Vor dem Commit prüfen: `openssl x509 -in … -noout -subject -ext subjectAltName`
  zeigt `planner.home.arpa` und **kein** `PRIVATE KEY`-Block in der Datei.
- **GOTCHA**: `.claude/settings.json` verbietet `Read(./deploy/certs/**)` — Zertifikat nicht von dort lesen, sondern
  vom Nutzer an den Zielort kopieren lassen; das `.crt` ist öffentlich, `.key`/`.p12` nie anfassen.
- **VALIDATE**: `sdkmanager --list_installed` zeigt Plattform + Build-Tools; `adb version` läuft.

### Task 1: ADR & Doku (V-Modell links)
- **ACTION**: `docs/02-architecture/adr/0009-android-app-architecture.md` nach `0000-template.md` schreiben.
- **IMPLEMENT**: Entscheidungen + Alternativen:
  1. Module `app`, `core:api`, `core:data`, `core:ui`, `core:testing`, `feature:<name>`; Feature-Module kennen sich
     nicht (analog ADR-0001). Schichten im Feature-Modul als Pakete `domain`/`data`/`ui`.
  2. DI **Koin** (wie Backend; Alt.: Hilt — mehr Codegen/kapt/ksp).
  3. HTTP: aus OpenAPI generierte **Retrofit**-Interfaces (`jvm-retrofit2`) + OkHttp (Alt.: Ktor-Client generiert —
     umständliche Response-Wrapper; handgeschrieben — verletzt ADR-0004).
  4. Token: **DataStore + Tink**, Schlüssel im Android Keystore (Alt.: EncryptedSharedPreferences — deprecated).
  5. **Öffentliches Server-Zertifikat liegt im Repo** (`res/raw/planner_cert.crt`), da kein Geheimnis und für CI-Builds
     nötig; Wechsel = App-Update (ADR-0006).
  6. Verteilung als signierte **Release-APK**, lokal gebaut, per `adb install`/Datei installiert; Signatur-Keystore
     nur lokal (`*.jks` ignoriert) — gleiche Signatur nötig für Updates.
  7. `minSdk 29` (Android 10), `targetSdk = compileSdk` = aktuelle stabile API (bei Umsetzung prüfen).
  `docs/02-architecture/README.md`: ADR-Liste ergänzen. `docs/03-design/core.md`: Abschnitt „App-Seite (Phase 1b)“
  (Session-Lebenszyklus, Fehlerabbildung ErrorCode→AppError, Screens/Rollen).
- **MIRROR**: Aufbau von `0008-device-pairing.md`.
- **VALIDATE**: ADR verlinkt, Status „Akzeptiert“, Datum 2026-10-11 oder Umsetzungsdatum.

### Task 2: Gradle-Gerüst
- **ACTION**: Eigenständiger Gradle-Build unter `android/` (unabhängig vom Backend-Build).
- **IMPLEMENT**: `settings.gradle.kts` (Repos `google()`, `mavenCentral()`, `gradlePluginPortal()` für Plugins;
  `include(":app", ":core:api", ":core:data", ":core:ui", ":core:testing", ":feature:auth")`), Wrapper erzeugen
  (`gradle wrapper --gradle-version <passend zu AGP>` — global installiertes Gradle 9.8.1 ist vorhanden),
  Versionskatalog (AGP 9.x, Kotlin = Backend-Version 2.4.21 falls AGP-kompatibel, Compose-BOM, Navigation Compose,
  Lifecycle, Koin (`koin-android`, `koin-androidx-compose`), Retrofit 3 + `converter-kotlinx-serialization`,
  OkHttp 5 + `mockwebserver3`, DataStore, Tink (`tink-android`), kotlinx-serialization 1.11.0, coroutines 1.11.0,
  Turbine, Robolectric, JUnit 4, openapi-generator 7.14.0, ktlint-gradle 14.2.0, detekt 1.23.8, kover 0.9.11).
  Convention-Plugins in `buildSrc` wie im Backend:
  - `familyplanner.android-library`: `com.android.library`, `kotlin("plugin.serialization")`, ktlint, detekt, kover;
    `compileSdk`, `minSdk = 29`, Java/Kotlin-Toolchain 21, `testOptions.unitTests.isIncludeAndroidResources = true`,
    Test-Deps (junit4, kotlin-test-junit, coroutines-test, turbine).
  - `familyplanner.android-compose`: `org.jetbrains.kotlin.plugin.compose`, `buildFeatures.compose = true`,
    Compose-BOM, `ui`, `material3`, `ui-tooling-preview`, Test: `ui-test-junit4`, robolectric, `ui-test-manifest` (debug).
  `app` nutzt `com.android.application` + Compose-Convention.
- **MIRROR**: `backend/buildSrc/src/main/kotlin/familyplanner.kotlin-jvm.gradle.kts` (Katalogzugriff via
  `extensions.getByType<VersionCatalogsExtension>().named("libs")`).
- **GOTCHA**: AGP 9 bringt eingebautes Kotlin — `org.jetbrains.kotlin.android` **nicht** anwenden (Build-Fehler
  „Kotlin plugin already applied“). Passt Kotlin 2.4.21 nicht zum AGP, die von AGP geforderte Version nehmen und im
  Report notieren. Detekt 1.23.8 lief im Backend mit Kotlin 2.4 — falls es mit AGP-Modulen scheitert, `detekt` nur
  auf `src/main/kotlin` konfigurieren; nicht abschalten ohne Notiz.
- **VALIDATE**: `cd android && ./gradlew help` und `./gradlew :app:assembleDebug` (leere Activity) grün.

### Task 3: Generierter API-Client (`core:api`) — ADR-0004
- **ACTION**: Retrofit-Interfaces + Modelle aus `../api/openapi.yaml` generieren.
- **IMPLEMENT**:
  ```kotlin
  openApiGenerate {
      generatorName.set("kotlin")
      library.set("jvm-retrofit2")
      inputSpec.set("$rootDir/../api/openapi.yaml")
      outputDir.set(generatedDir.map { it.asFile.absolutePath })
      packageName.set("de.familyplanner.api")
      apiPackage.set("de.familyplanner.api.client")
      modelPackage.set("de.familyplanner.api.model")
      globalProperties.set(mapOf("apis" to "", "models" to "", "modelDocs" to "false", "modelTests" to "false",
          "apiDocs" to "false", "apiTests" to "false"))
      typeMappings.set(mapOf("UUID" to "kotlin.String"))
      configOptions.set(mapOf("serializationLibrary" to "kotlinx_serialization", "useCoroutines" to "true",
          "dateLibrary" to "string", "enumPropertyNaming" to "UPPERCASE"))
  }
  ```
  Ergebnis: `AuthApi`, `MembersApi`, `DevicesApi`, `SystemApi` (nach Tags) mit `suspend fun …: Response<…>`.
- **MIRROR**: `backend/api-contract/build.gradle.kts` (gleiche `typeMappings`/`configOptions`; ktlint-Filter auf
  `generated`, detekt-Source leer; `compileKotlin`/`preBuild` dependsOn `openApiGenerate`).
- **GOTCHA**: Ohne `supportingFiles` fehlen u. U. Hilfsklassen, die generierte APIs importieren (z. B.
  `CollectionFormats`) → Ausgabe prüfen; falls nötig `"supportingFiles" to "CollectionFormats.kt"` (o. Ä.) ergänzen,
  aber **nicht** den generierten `ApiClient` nutzen. Bei Android-Library: `kotlin.srcDir` über
  `android.sourceSets["main"].kotlin.srcDir(...)` bzw. AGP-9-API `androidComponents` einbinden — beim ersten Lauf
  verifizieren, dass `de.familyplanner.api.client.AuthApi` kompiliert.
- **VALIDATE**: `GeneratedApiTest`: `ErrorResponse` mit Code `INVALID_PAIRING_CODE` deserialisiert; `Member`
  serialisiert identisch zum Backend-Test (`{"id":"id-1","name":"Lea","role":"CHILD","createdAt":"…"}`).

### Task 4: Netzwerk & Fehlerabbildung (`core:data`) — TDD
- **ACTION**: RED: `ApiCallTest` (MockWebServer) für jede Zeile der Fehlertabelle; dann GREEN.
- **IMPLEMENT**: `ApiResult`/`AppError`/`apiCall`/`apiCallNoContent` (siehe ERROR_HANDLING). `NetworkModule`:
  ```kotlin
  OkHttpClient.Builder()
      .connectTimeout(CONNECT_TIMEOUT_S, SECONDS)   // 5 s
      .readTimeout(READ_TIMEOUT_S, SECONDS)         // 10 s
      .callTimeout(CALL_TIMEOUT_S, SECONDS)         // 15 s
      .addInterceptor(AuthInterceptor(sessionCache))
      .addInterceptor(UnauthorizedInterceptor(sessionStore, sessionEvents))
  ```
  Retrofit mit `baseUrl(serverConfig.baseUrl)` + `json.asConverterFactory("application/json".toMediaType())`;
  `Json { ignoreUnknownKeys = true; explicitNulls = false }`; `single { get<Retrofit>().create(AuthApi::class.java) }`
  usw. `ServerConfig(baseUrl: String)` wird in `app` aus `BuildConfig.FP_SERVER_URL` gebaut.
- **MIRROR**: ERROR_HANDLING, SESSION_PATTERN.
- **GOTCHA**: `retrofit2.Response` wirft bei 4xx **nicht** — Fehlerbody über `errorBody()?.string()` mit `Json`
  dekodieren (in `runCatching`, Fehler → Status-Fallback). Retrofit-`baseUrl` muss mit `/` enden. `HttpException`
  tritt bei `Response<T>`-Rückgabe nicht auf. `CancellationException` immer weiterwerfen.
- **VALIDATE**: Tests: 200+data → Success; 200 ohne data → Server; 400 VALIDATION_FAILED → Validation(msg);
  401 UNAUTHORIZED → Unauthorized; 401 INVALID_PAIRING_CODE → InvalidPairingCode; 403/404/409/429/503 entsprechend;
  500 mit kaputtem Body → Server; Socket-Abbruch (`SocketPolicy.DisconnectAtStart`) → Network; 204 → Success(Unit).
  `AuthInterceptorTest`: Header `Authorization: Bearer tok` nur bei vorhandener Session.
  `UnauthorizedInterceptorTest`: 401 mit Token → Session gelöscht + `SignedOut` emittiert; 401 ohne Token → nichts.

### Task 5: Session-Speicher (`core:data`) — TDD
- **ACTION**: `SessionStore`, `DataStoreSessionStore`, `SessionSerializer` (Tink-`Aead`).
- **IMPLEMENT**: Serializer: `writeTo` = `aead.encrypt(json.encodeToString(dto).encodeToByteArray(), ASSOCIATED_DATA)`;
  `readFrom` = entschlüsseln, bei `GeneralSecurityException`/leerem Input → `defaultValue` (`null`-Session) und
  `Log.w` (kein Crash — Fall: App-Daten wiederhergestellt, Keystore-Schlüssel fehlt → neu koppeln).
  Aead-Erzeugung: `AeadConfig.register()`, `AndroidKeysetManager.Builder().withSharedPref(ctx, KEYSET_NAME, PREF_FILE)
  .withKeyTemplate(KeyTemplates.get("AES256_GCM")).withMasterKeyUri(MASTER_KEY_URI).build().keysetHandle
  .getPrimitive(RegistryConfiguration.get(), Aead::class.java)`. Falls `datastore-tink` stabil verfügbar ist,
  stattdessen dessen `AeadSerializer` verwenden. In-Memory-Cache: `stateIn(appScope, Eagerly, null)` für Interceptor.
  `InMemorySessionStore` in `core:testing`.
- **MIRROR**: SESSION_PATTERN.
- **GOTCHA**: Android Keystore existiert in Robolectric nicht → `SessionSerializerTest` mit Tink-Aead aus einem
  **im Test erzeugten** Cleartext-Keyset (`KeysetHandle.generateNew(...)`) testen; die Keystore-Verdrahtung nur
  manuell auf Gerät prüfen. Manifest: `android:allowBackup="false"` (sonst wiederhergestellte, nicht entschlüsselbare
  Daten).
- **VALIDATE**: Roundtrip speichert/liest Session; verschlüsselte Bytes enthalten den Token-Klartext **nicht**;
  manipulierte Bytes → `null` ohne Exception.

### Task 6: UI-Grundlagen (`core:ui`, `core:testing`)
- **ACTION**: Theme, Fehlertexte, Bausteine, Test-Helfer.
- **IMPLEMENT**: `FamilyPlannerTheme` (Material 3, `dynamicColorScheme` ab API 31, sonst feste Farben);
  `AppError.messageRes()` (exhaustive `when`) mit Texten: Network „Server nicht erreichbar. Bist du im Heim-WLAN?“,
  Certificate „Sichere Verbindung fehlgeschlagen (Zertifikat).“, Unauthorized „Dieses Gerät ist nicht (mehr)
  angemeldet.“, InvalidPairingCode „Code ungültig oder abgelaufen.“, Forbidden „Nur für Eltern.“, RateLimited
  „Zu viele Versuche – bitte eine Minute warten.“, SetupDisabled „Einrichtung auf dem Server nicht aktiviert.“,
  NotFound „Nicht gefunden.“, Server „Fehler auf dem Server.“; Validation/Conflict → Servermeldung.
  `SubmitButton(loading)`, `ErrorText`, `ConfirmDialog`. `MainDispatcherRule`.
- **VALIDATE**: `./gradlew :core:ui:testDebugUnitTest` (Test: jede `AppError`-Variante hat eine Ressource).

### Task 7: Feature AUTH – Domäne & Daten (`feature:auth`) — TDD
- **ACTION**: RED: UseCase- und Repository-Tests; dann GREEN.
- **IMPLEMENT**: Domänenmodelle `Me(memberId, name, role, deviceId)`, `FamilyMember(id, name, role)`,
  `FamilyDevice(id, memberId, name, lastSeenAt: Instant?)`, `IssuedPairingCode(code, expiresAt: Instant)`
  (Zeitstempel `Instant.parse` im Mapper; ungültig → `AppError.Server`).
  Repos: `AuthRepository` (setup, pair, me), `FamilyRepository` (members, createMember, createPairingCode, devices,
  revokeDevice). UseCases: `SetupFamilyUseCase` (speichert Session), `PairDeviceUseCase` (speichert Session),
  `RefreshMeUseCase` (aktualisiert Name/Rolle in Session), `LoadFamilyUseCase` (Mitglieder + Geräte parallel per
  `coroutineScope { async … }`; erster Fehler gewinnt), `CreateMemberUseCase`, `CreatePairingCodeUseCase`,
  `RevokeDeviceUseCase`. Client-Validierung vor dem Call (Name 1–40 getrimmt, Gerätename 1–60, Code 6 Ziffern,
  Setup-Token ≥ 16) → `AppError.Validation` mit deutscher Meldung, **ohne** Request.
- **MIRROR**: REPOSITORY_PATTERN, USECASE_PATTERN.
- **GOTCHA**: Kein Konfliktfall clientseitig für Namen (case-insensitiv) — Server ist maßgeblich (409).
- **VALIDATE**: Tests (Auswahl, Namen mit REQ-ID):
  `REQ-AUTH-002 pairing saves session on success`, `REQ-AUTH-002 invalid pairing code does not save session`,
  `REQ-AUTH-002 setup with existing family reports conflict`, `REQ-AUTH-001 create member rejects blank name without request`,
  `REQ-AUTH-003 revoke device calls delete endpoint`, `RemoteAuthRepository sends bearer-less pair request`.

### Task 8: Feature AUTH – Screens & ViewModels — TDD
- **ACTION**: ViewModels zuerst testgetrieben (Turbine), dann Compose-Screens.
- **IMPLEMENT**:
  - `WelcomeScreen`: zwei Aktionen, optionaler Hinweis „abgemeldet“ (aus `SessionEvent.SignedOut`).
  - `SetupViewModel/Screen`: Setup-Token als Passwortfeld (`PasswordVisualTransformation`, `KeyboardType.Password`),
    Name, Gerätename; 409 → Hinweis + Button „Mit Code verbinden“.
  - `PairViewModel/Screen`: Code-Feld (`KeyboardType.NumberPassword`, max 6), Gerätename (`Build.MODEL` vorbelegt).
  - `HomeViewModel/Screen`: zeigt Session (sofort, aus Cache), ruft `RefreshMeUseCase` beim Start; Network-Fehler
    nur als dezenter Hinweis „offline“ (kein Rauswurf); Button „Familie verwalten“ nur bei `Role.PARENT`.
  - `FamilyViewModel/Screen`: Liste Mitglieder + Geräte (Gerätename, Mitgliedsname, „zuletzt aktiv“), Pull-to-refresh,
    `AddMemberDialog` (Name, Rolle als SegmentedButton), `PairingCodeDialog` (Code groß, Ablaufzeit `HH:mm`
    in `Europe/Berlin`, Hinweis „Ein neuer Code macht den alten ungültig“), Entfernen mit `ConfirmDialog`
    (eigenes Gerät: Text „Du entfernst dein eigenes Gerät und wirst abgemeldet.“).
  - Kind-Session, die `FamilyRoute` erreicht (Deep-Link o. Ä.) → 403 → `Forbidden`-Text; Route wird für Kinder gar
    nicht angeboten.
- **MIRROR**: VIEWMODEL_PATTERN; Screens zustandslos (`XxxScreen(state, onEvent)`) + dünner `XxxRoute`-Wrapper
  mit `koinViewModel()` — so sind Screens ohne Koin testbar.
- **GOTCHA**: Setup-Token/Code nicht in `SavedStateHandle` oder Logs ablegen. Doppeltes Absenden verhindern
  (`isSubmitting`). Robolectric-Compose-Tests brauchen `@Config(sdk = [<unterstützte API>])` falls Robolectric die
  compileSdk noch nicht kennt.
- **VALIDATE**: ViewModel-Tests: Submit-Button erst aktiv bei gültiger Eingabe; Loading → Fehler mit korrektem
  `AppError`; Erfolg setzt Session (InMemorySessionStore). Robolectric: `PairScreen` zeigt Fehlermeldung bei
  `InvalidPairingCode`; `HomeScreen` zeigt „Familie verwalten“ nur für Eltern (`REQ-AUTH-002 child sees no parent actions`);
  `FamilyScreen` Entfernen fragt nach Bestätigung (`REQ-AUTH-003 …`).

### Task 9: App-Modul, Navigation, TLS-Pinning
- **ACTION**: `app` zusammensetzen.
- **IMPLEMENT**:
  - `build.gradle.kts`: `applicationId = "de.familyplanner.android"`, `versionCode`/`versionName`,
    `buildConfigField("String", "FP_SERVER_URL", …)` aus `local.properties` `fp.serverUrl`, Default
    `"https://planner.home.arpa:8095/"`; Debug-Default darf per `fp.serverUrl=http://10.0.2.2:8095/` überschrieben werden.
    Release: `isMinifyEnabled = true`, Signing aus `local.properties` (`fp.signing.storeFile`, `…storePassword`,
    `…keyAlias`, `…keyPassword`) bzw. Env-Variablen; fehlt die Konfiguration, bleibt Release unsigniert (kein Build-Fehler in CI).
  - `network_security_config.xml` (main):
    ```xml
    <network-security-config>
        <base-config cleartextTrafficPermitted="false" />
        <domain-config cleartextTrafficPermitted="false">
            <domain includeSubdomains="false">planner.home.arpa</domain>
            <trust-anchors>
                <certificates src="@raw/planner_cert" />
            </trust-anchors>
        </domain-config>
    </network-security-config>
    ```
    Debug-Variante zusätzlich `<domain-config cleartextTrafficPermitted="true"><domain>10.0.2.2</domain><domain>localhost</domain></domain-config>`.
  - `FamilyPlannerApp`: `startKoin { androidContext(this); modules(networkModule, sessionModule, uiModule, authModule, appModule) }`
    (`appModule` liefert `ServerConfig(BuildConfig.FP_SERVER_URL)`).
  - `MainActivity` (`enableEdgeToEdge`, `setContent { FamilyPlannerTheme { AppNavHost() } }`).
  - `AppNavHost`: beobachtet `SessionStore.session` (`collectAsStateWithLifecycle`); `null` → Graph `AuthGraph`
    (Welcome/Setup/Pair), sonst `MainGraph` (Home/Family). `SessionEvent.SignedOut` → Welcome mit Hinweis.
    Während die Session noch geladen wird: leerer Splash (kein Flackern zur Welcome-Seite).
  - `proguard-rules.pro`: kotlinx.serialization-Keep-Regeln für `de.familyplanner.api.model.**`, Retrofit-Interfaces
    `de.familyplanner.api.client.**` (Retrofit 3 bringt eigene R8-Regeln mit; nur Ergänzungen).
- **GOTCHA**: `<domain>` matcht Hostnamen; Zugriff per IP wird **nicht** gepinnt und wegen fehlender Trust-Anchors
  abgelehnt → URL immer mit Hostnamen. `planner_cert.crt` muss PEM sein; Dateiname in `res/raw` nur `[a-z0-9_]`.
  Release-Build unbedingt einmal **minifiziert** testen (R8 bricht Serialisierung still).
- **VALIDATE**: `AppNavHostTest` (Robolectric): ohne Session → Welcome, mit Session → Home „Hallo Lea“;
  `./gradlew :app:assembleRelease` (minifiziert) baut.

### Task 10: Qualität & Coverage
- **ACTION**: ktlint, detekt, Android Lint, Kover.
- **IMPLEMENT**: Kover-Aggregation in `app` (`dependencies { kover(project(":core:data")) … }`), Verify-Regel
  **≥ 80 % Line-Coverage** mit Ausschlüssen: generierter Code (`de.familyplanner.api.*`), `*ScreenKt` Preview-Funktionen
  (`@Preview`), `theme`, `MainActivity`, `FamilyPlannerApp`, `*Module*` (Koin-Verdrahtung), `BuildConfig`.
  Android Lint: `warningsAsErrors = false`, `abortOnError = true`, Baseline nicht anlegen.
- **VALIDATE**: `./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify` grün.

### Task 11: CI (`.github/workflows/android.yml`)
- **ACTION**: Eigener Workflow.
- **IMPLEMENT**:
  ```yaml
  name: android
  on:
    push: { branches: [main], paths: ['android/**', 'api/**', '.github/workflows/android.yml'] }
    pull_request: { paths: ['android/**', 'api/**', '.github/workflows/android.yml'] }
  concurrency: { group: android-${{ github.ref }}, cancel-in-progress: true }
  jobs:
    build:
      runs-on: ubuntu-latest
      defaults: { run: { working-directory: android } }
      steps:
        - uses: actions/checkout@v4
        - uses: actions/setup-java@v4
          with: { distribution: temurin, java-version: '21' }
        - uses: android-actions/setup-android@v3
        - uses: gradle/actions/setup-gradle@v4
        - run: ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug
        - uses: actions/upload-artifact@v4
          if: failure()
          with: { name: android-reports, path: "android/**/build/reports/\nandroid/**/build/test-results/" }
  ```
  (Report-Pfade wie im Backend-Workflow als Block-Scalar schreiben.)
- **MIRROR**: `.github/workflows/backend.yml`.
- **GOTCHA**: `api/**` triggert **beide** Workflows — gewollt (Vertragsänderung muss App und Backend bauen).
  Kein APK-Upload als Artefakt (Debug-Signatur pro Runner verschieden; Verteilung lokal, ADR-0009).
- **VALIDATE**: Lauf erst nach Push sichtbar; lokal identischer Befehl grün.

### Task 12: Doku, Traceability, PRD
- **ACTION**: V-Modell rechts schließen.
- **IMPLEMENT**: `android/README.md` (Voraussetzungen, `local.properties`-Schlüssel, Emulator gegen lokales Backend:
  `cd backend && FP_SETUP_TOKEN=… ./gradlew :app:run` + `fp.serverUrl=http://10.0.2.2:8095/`, Zertifikat tauschen,
  Release signieren + `adb install -r`). `deploy/README.md`: Abschnitt „Erst-Einrichtung“ auf App verweisen (curl als
  Fallback behalten). Traceability: REQ-AUTH-001..003 Spalte Unit um App-Tests ergänzen, Spalte E2E
  „manuell: Abnahme-Checkliste Phase 1b (android/README.md)“, Status „umgesetzt (Backend + App)“.
  PRD Phase 1: Plan/Report verlinken, Status `complete` erst nach erfolgreicher Geräte-Abnahme (5 Geräte).
- **VALIDATE**: Links funktionieren; keine REQ-ID ohne Testverweis.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `ApiCallTest` success | 200 `{success:true,data:{…}}` | `Success(data)` | |
| `ApiCallTest` envelope without data | 200 `{success:true}` | `Failure(Server)` | ✓ |
| `ApiCallTest` invalid pairing code | 401 `INVALID_PAIRING_CODE` | `Failure(InvalidPairingCode)` | |
| `ApiCallTest` unauthorized | 401 `UNAUTHORIZED` | `Failure(Unauthorized)` | |
| `ApiCallTest` validation | 400 `VALIDATION_FAILED`, msg | `Failure(Validation(msg))` | |
| `ApiCallTest` conflict / forbidden / not found / rate limited / setup disabled | 409/403/404/429/503 | jeweilige `AppError` | |
| `ApiCallTest` broken error body | 500 `<html>` | `Failure(Server)` | ✓ |
| `ApiCallTest` connection drop | Disconnect | `Failure(Network)` | ✓ |
| `ApiCallTest` no content | 204 | `Success(Unit)` | |
| `AuthInterceptorTest` | Session vorhanden / null | Header gesetzt / fehlt | ✓ |
| `UnauthorizedInterceptorTest` | 401 mit/ohne Token | Session gelöscht + Event / unverändert | ✓ |
| `SessionSerializerTest` | Session | Roundtrip; Ciphertext ohne Token; Manipulation → null | ✓ |
| `REQ-AUTH-002 pairing saves session on success` | gültiger Code | Session gespeichert | |
| `REQ-AUTH-002 invalid pairing code does not save session` | 401 | keine Session, Fehler | |
| `REQ-AUTH-002 setup with existing family reports conflict` | 409 | `Conflict`, keine Session | |
| `REQ-AUTH-002 setup token shorter than 16 is rejected locally` | 15 Zeichen | `Validation`, kein Request | ✓ |
| `REQ-AUTH-001 create member rejects blank or too long name` | „ “, 41 Zeichen | `Validation`, kein Request | ✓ |
| `REQ-AUTH-001 duplicate member name shows conflict` | 409 | Meldung „existiert bereits“ | |
| `REQ-AUTH-002 pairing code dialog shows code and expiry` | Code + `expiresAt` | „483920 · gültig bis 18:42“ (Europe/Berlin) | ✓ (Zeitzone) |
| `REQ-AUTH-003 revoking own device signs out` | eigenes deviceId | Bestätigung mit Warnung; danach Session leer | ✓ |
| `REQ-AUTH-002 child sees no parent actions` | Role.CHILD | kein „Familie verwalten“ | |
| `HomeViewModel keeps session when offline` | Network-Fehler | Session bleibt, Hinweis offline | ✓ |
| `PairViewModel filters non digits and caps at six` | „12a3456789“ | „123456“ | ✓ |
| `AppNavHostTest` | Session null / gesetzt | Welcome / Home | |

### Edge Cases Checklist
- [x] Leere Eingaben (Name, Code, Token) — lokal abgefangen
- [x] Maximallängen (40/60) — Eingabe begrenzt
- [x] Ungültige Typen (Buchstaben im Code) — gefiltert
- [x] Gleichzeitigkeit: Doppel-Submit — `isSubmitting`
- [x] Netzwerkfehler / falsches Zertifikat / außer Haus — `Network`/`Certificate`, Session bleibt
- [x] Permission denied — Kind auf Eltern-Endpunkt → `Forbidden`
- [x] Token widerrufen — 401 → automatische Abmeldung
- [x] Keystore-Schlüssel verloren (Backup-Restore) — Session `null`, neu koppeln

---

## Validation Commands

### Static Analysis
```bash
cd android && ./gradlew ktlintCheck detekt lintDebug
```
EXPECT: Keine Befunde

### Unit Tests
```bash
cd android && ./gradlew testDebugUnitTest
```
EXPECT: Alle Tests grün

### Full Test Suite + Coverage
```bash
cd android && ./gradlew testDebugUnitTest koverVerify
```
EXPECT: ≥ 80 % Line-Coverage (mit den Ausschlüssen aus Task 10)

### Build
```bash
cd android && ./gradlew assembleDebug assembleRelease
```
EXPECT: APKs unter `app/build/outputs/apk/`; Release minifiziert

### Backend unverändert
```bash
cd backend && ./gradlew check
```
EXPECT: grün (Vertrag nicht geändert)

### Manual Validation (Emulator, lokales Backend ohne TLS)
- [ ] Backend lokal: `cd backend && FP_SETUP_TOKEN=<16+ Zeichen> FP_DB_PATH=/tmp/fp-test.db ./gradlew :app:run`
- [ ] `fp.serverUrl=http://10.0.2.2:8095/` in `android/local.properties`, `./gradlew :app:installDebug`
- [ ] Erst-Einrichtung mit falschem Token → „nicht angemeldet“-Meldung; mit richtigem → „Hallo <Name>“
- [ ] Erneute Einrichtung (App-Daten löschen) → Hinweis „bereits eingerichtet“
- [ ] Familie: Kind anlegen, doppelter Name → Meldung; Kopplungscode erzeugen
- [ ] Zweiter Emulator/Gerät: Code einlösen → „Hallo <Kind>“, kein „Familie verwalten“
- [ ] Eltern entfernen Kind-Gerät → Kind-App zeigt beim nächsten Aufruf „abgemeldet“
- [ ] Backend stoppen → Home zeigt „offline“, bleibt angemeldet

### Abnahme auf echten Geräten (Pi, HTTPS, gepinnt)
- [ ] Release-APK mit Pi-Zertifikat, `fp.serverUrl` Default → auf Elternhandy installieren, Einrichtung
- [ ] Gegenprobe Pinning: App gegen Server mit anderem Zertifikat → „Sichere Verbindung fehlgeschlagen“
- [ ] Alle fünf Handys gekoppelt; Rollen wirken (Kinder sehen keine Verwaltung)
- [ ] Außerhalb des WLANs: App startet, zeigt angemeldeten Nutzer + „offline“

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt
- [ ] Alle Validierungsbefehle grün (lokal und in der CI)
- [ ] Tests geschrieben (REQ-IDs im Namen) und grün, Coverage ≥ 80 %
- [ ] Release-Build minifiziert lauffähig
- [ ] Zertifikat gepinnt, kein Cleartext außer Debug→Emulator
- [ ] UX entspricht „After“-Skizze
- [ ] ADR-0009, Design, Traceability, README, PRD aktualisiert

## Completion Checklist
- [ ] Muster aus „Patterns to Mirror“ eingehalten (Namen, `ApiResult`, Session, ViewModel)
- [ ] Fehler: jede `AppError` hat eine deutsche Nutzermeldung; `CancellationException` nie verschluckt
- [ ] Keine Secrets im Code/Log (Token, Setup-Token, Code, Signatur-Passwörter)
- [ ] Tests folgen AAA + Fakes statt Mocks
- [ ] Keine hartcodierten Werte (Timeouts, Limits, URL als Konstanten/Build-Config)
- [ ] Keine Scope-Erweiterungen über „NOT Building“ hinaus
- [ ] Backend/Vertrag unverändert

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Versionsmatrix AGP 9 / Kotlin 2.4 / Compose / detekt 1.23 passt nicht | M | M | Versionen bei Task 2 gegen Release Notes prüfen; AGP-Vorgabe gewinnt; Abweichung im Report |
| Generierter Retrofit-Code braucht Supporting Files / ist unpassend | M | L | Ausgabe nach Task 3 inspizieren; Fallback: nur Modelle generieren + handgeschriebene Retrofit-Interfaces (ADR-0009-Notiz) |
| `datastore-tink` noch alpha | M | L | Tink-`Aead` direkt im eigenen Serializer (Task 5) |
| Pi-Zertifikat passt nicht zum Hostnamen (SAN) | L | M | In Task 0 per `openssl x509 -ext subjectAltName` prüfen; sonst `gen-cert.sh` auf dem Pi neu ausführen |
| Android „Privates DNS“ mit festem Anbieter umgeht Pi-hole → `planner.home.arpa` nicht auflösbar | M | M | In README/Abnahme: Privates DNS auf „Automatisch“; Fehlermeldung `Network` erwähnt WLAN |
| R8 bricht Serialisierung im Release | L | H | Keep-Regeln + Release-Build manuell testen |
| Familienhandys < Android 10 | L | L | Laut Nutzer (2026-10-11) alle Handys recht aktuell → `minSdk 29` bleibt |
| Robolectric kennt neueste API noch nicht | M | L | `@Config(sdk = [...])` auf höchste unterstützte API |

## Notes
- Kein Vertrags- oder Backend-Änderungsbedarf erkannt: Alle benötigten Endpunkte (`setup`, `devices/pair`, `me`,
  `members`, `pairing-codes`, `devices`, `devices/{id}`) existieren.
- `core:api` generiert **dieselben Modellnamen** wie das Backend (`de.familyplanner.api.model`) — bewusst, damit
  Vertragsänderungen auf beiden Seiten gleich aussehen.
- Navigation folgt der Session statt ViewModel-Events: einfacher, und die 401-Abmeldung funktioniert überall
  automatisch.
- Spätere Module (`feature:meals`) bekommen ein eigenes Feature-Modul und hängen nur von `core:*` ab.
- Nach Umsetzung: Erfahrungen zum Android-Teil (Versionen, Generator) in `CLAUDE.md` „Stack“ ergänzen, falls relevant.
