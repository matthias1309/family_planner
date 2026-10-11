# Implementation Report: Fundament & AUTH – Android-App (Phase 1b)

## Summary
Android-Grundgerüst (Gradle-Multi-Modul, Compose, Koin) mit generiertem Retrofit-Client, verschlüsselter Session (DataStore + Tink),
gepinntem Zertifikat, AUTH-Screens (Einrichten, Koppeln, Home, Familie verwalten) und Android-CI.

## Assessment vs Reality

| Metric | Predicted | Actual |
|---|---|---|
| Complexity | Large | Large |
| Files Changed | ~60 | ~95 (inkl. Wrapper, Ressourcen, Tests) |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | ktlintCheck, detekt, lintDebug |
| Unit Tests | Pass | 76 Tests (JVM, Robolectric für Compose) |
| Coverage | Pass | 88,5 % Line (Kover, Schwelle 80 %) |
| Build | Pass | assembleDebug + minifiziertes assembleRelease (unsigniert) |
| Integration / Edge | Pass | Emulator + lokales Backend, **minifiziertes** Debug-Build: Einrichten, Mitglied anlegen, Doppelname-Konflikt, Kopplungscode (Ablaufzeit Berlin), falscher/richtiger Code, eigenes Gerät entfernen → Abmeldung, Kind ohne Verwaltung |
| Backend unverändert | Pass | `backend ./gradlew check` grün |

## Deviations from Plan
- AGP 9.4.1, Kotlin 2.4.21 (kompatibel), compileSdk/targetSdk 37 (`android-37.0` installiert), Robolectric auf `sdk=36` + `--add-exports java.base/jdk.internal.access`.
- Generierter Code: `supportingFiles = CollectionFormats.kt` nötig; Quellordner per `androidComponents.onVariants` statt `sourceSets` (AGP 9); jeder Task hängt an `openApiGenerate` (implizite Abhängigkeiten in Lint/Annotationen).
- Zusätzliche Convention `familyplanner.quality` (ktlint/detekt/kover) für Library und App; `.editorconfig` für `@Composable`-Namen.
- `SessionStore` liefert `SessionState` (`Loading`/`SignedOut`/`SignedIn`) statt `Session?`, damit kein Flackern zur Welcome-Seite entsteht.
- Manifest-Klassen relativ zum Namespace (`.app.MainActivity`) — vom `AppNavHostTest` gefunden.
- Kein HTTP-Logging-Interceptor (YAGNI).
- Emulator: `10.0.2.2` war im Sandbox-Netz nicht erreichbar → `adb reverse` + `localhost` (README erwähnt beides).

## Offen
- Geräte-Abnahme auf den fünf Handys gegen den Pi (Checkliste in `android/README.md`); PRD-Phase 1 erst danach `complete`.
- Pinning-Gegenprobe und Release-Signatur nicht lokal geprüft.
