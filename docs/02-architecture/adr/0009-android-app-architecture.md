# ADR-0009: Android-App-Architektur

- **Status:** Akzeptiert
- **Datum:** 2026-10-11

## Kontext

Die App ist Greenfield (Phase 1b). Sie spricht nur mit dem eigenen Backend im Heimnetz (ADR-0006), nutzt den
generierten Vertrag (ADR-0004) und soll später um Feature-Module (`meals`, `tasks`, …) wachsen (ADR-0001).
Es gibt fünf Nutzer; verteilt wird ohne Play Store.

## Entscheidung

1. **Module:** `app`, `core:api` (generierter Client), `core:data` (Netzwerk, Session, `ApiResult`), `core:ui`
   (Theme, Fehlertexte, Bausteine), `core:testing` (geteilte Test-Helfer), `feature:<name>`. Feature-Module kennen sich
   nicht gegenseitig. Innerhalb eines Features trennen Pakete `domain` / `data` / `ui` die Schichten.
2. **DI:** Koin (wie im Backend), ein Koin-Modul je Gradle-Modul.
3. **HTTP:** Aus `api/openapi.yaml` generierte **Retrofit**-Interfaces (`jvm-retrofit2`, kotlinx.serialization) auf
   OkHttp. Der generierte `ApiClient` wird nicht benutzt; Retrofit/OkHttp werden in `core:data` selbst gebaut.
4. **Token-Speicher:** DataStore mit **Tink-AEAD** (AES-256-GCM), Keyset im Android Keystore. Die Session wird zusätzlich
   im Speicher gehalten, damit der OkHttp-Interceptor nie blockierend auf DataStore zugreift.
5. **Zertifikat:** Das öffentliche Server-Zertifikat liegt im Repo (`app/src/main/res/raw/planner_cert.crt`) und wird über
   die Network-Security-Config für den Host `planner.home.arpa` als einziger Trust-Anchor gepinnt (ADR-0006). Es ist kein
   Geheimnis und wird für CI-Builds gebraucht. Ein Zertifikatswechsel erfordert ein App-Update.
6. **Verteilung:** Signierte Release-APK, lokal gebaut und per `adb install` bzw. Datei installiert. Der Signatur-Keystore
   bleibt lokal (`*.jks` ist ignoriert); für Updates ist dieselbe Signatur nötig.
7. **SDK:** `minSdk 29` (alle Familiengeräte sind aktuell), `compileSdk`/`targetSdk` 37 (aktuelle stabile API).
   Navigation folgt der Session (`SessionStore`), nicht ViewModel-Events: abgemeldet → Auth-Screens, angemeldet →
   Hauptscreens; ein 401 auf einem Request mit Token meldet automatisch ab.

## Alternativen

- **Hilt:** mehr Codegen (KSP/kapt), anderes Muster als das Backend — verworfen.
- **Ktor-Client generiert:** umständliche Response-Wrapper; handgeschriebene Clients würden ADR-0004 verletzen — verworfen.
- **EncryptedSharedPreferences:** deprecated — verworfen.
- **Zertifikat nur lokal (nicht im Repo):** CI könnte nicht bauen — verworfen.
- **Play Store / automatische Updates:** unverhältnismäßig für fünf Geräte — verworfen.

## Konsequenzen

- Neue Features sind eigene Module mit derselben Schichtung; sie hängen nur von `core:*` ab.
- Vertragsänderungen wirken sich beim Build sofort auf die App aus (generierte Modelle).
- Backups sind abgeschaltet (`allowBackup=false`): ein wiederhergestelltes Gerät muss neu gekoppelt werden.
- Updates der App erfolgen manuell; das Zertifikat läuft 2031 ab und muss dann in Server und App getauscht werden.
