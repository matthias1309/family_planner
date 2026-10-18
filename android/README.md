# android

Android-App: Kotlin, Jetpack Compose, Clean Architecture. Entscheidungen: [ADR-0002](../docs/02-architecture/adr/0002-kotlin-stack.md),
[ADR-0009](../docs/02-architecture/adr/0009-android-app-architecture.md).

## Module

`app` · `core:api` (aus `api/openapi.yaml` generierter Retrofit-Client) · `core:data` (Netzwerk, Session, Fehlerabbildung) ·
`core:ui` (Theme, Bausteine) · `core:testing` (Test-Helfer) · `feature:auth` (Einrichten, Koppeln, Familie verwalten) · `feature:meals` (Anwesenheit & Fristen).

## Voraussetzungen

- JDK 21, Android SDK (Plattform 37, Build-Tools) — Android Studio richtet beides ein.
- `android/local.properties` (git-ignoriert):

```properties
sdk.dir=/Users/<du>/Library/Android/sdk
# optional: anderer Server (Default https://planner.home.arpa:8095/), Debug erlaubt Klartext nur für 10.0.2.2/localhost
fp.serverUrl=http://localhost:8095/
# optional: Release signieren (sonst bleibt die Release-APK unsigniert)
fp.signing.storeFile=/pfad/zum/release.jks
fp.signing.storePassword=…
fp.signing.keyAlias=…
fp.signing.keyPassword=…
```

## Befehle

```bash
./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug   # wie die CI
./gradlew assembleRelease                                                            # minifizierte Release-APK
```

## Gegen ein lokales Backend (Emulator)

```bash
cd backend && FP_SETUP_TOKEN=<16+ Zeichen> FP_DB_PATH=/tmp/fp-test.db FP_TLS_ENABLED=false ./gradlew :app:run
adb reverse tcp:8095 tcp:8095       # Emulator -> Host (alternativ 10.0.2.2, falls das Netz des Emulators es zulässt)
# in local.properties: fp.serverUrl=http://localhost:8095/
cd android && ./gradlew :app:installDebug
```

## Zertifikat

Die App vertraut für `planner.home.arpa` **nur** dem Zertifikat in `app/src/main/res/raw/planner_cert.crt` (öffentlicher Teil,
kein Geheimnis). Wechsel: neue `planner.crt` vom Pi dorthin kopieren (PEM, ohne `PRIVATE KEY`) und die App neu bauen/installieren.
Der Hostname muss in der Server-URL stehen (Zugriff per IP wird nicht gepinnt). Android „Privates DNS“ auf „Automatisch“ lassen,
damit Pi-hole den Namen auflöst.

## Release installieren

```bash
./gradlew assembleRelease          # mit fp.signing.* in local.properties
adb install -r app/build/outputs/apk/release/app-release.apk
```

Updates nur mit derselben Signatur; der Signatur-Keystore bleibt lokal (`*.jks` ist ignoriert).

## Abnahme-Checkliste Phase 1b (manuell, echte Geräte)

Abgenommen am 2026-10-18 bis auf den Offline-Punkt (WLAN-Test steht aus).

- [x] Release-APK auf dem Elternhandy installieren, „Familie einrichten“ mit dem Setup-Token
- [x] Mitglieder anlegen, Kopplungscode je Kind erzeugen, Codes auf den Handys einlösen (alle fünf gekoppelt)
- [x] Kinder sehen „Hallo <Name>“ und keine „Familie verwalten“-Schaltfläche
- [x] Gerät entfernen → die App dieses Geräts zeigt beim nächsten Aufruf „abgemeldet“
- [ ] Außerhalb des WLANs: App startet, zeigt den angemeldeten Nutzer und „offline“
- [x] Gegenprobe Pinning: Server mit anderem Zertifikat → „Sichere Verbindung fehlgeschlagen“

## Abnahme-Checkliste Phase 2b (manuell, echte Geräte gegen den Pi)

Abgenommen am 2026-10-18 bis auf den Offline-Punkt (WLAN-Test steht aus).

- [x] Release-APK 0.2.0 (gleiche Signatur) per `adb install -r` auf Elternhandy und einem Kinderhandy aktualisieren; Kopplung bleibt erhalten
- [x] Eltern: für jedes Kind „kocht“ und Muster setzen (Wechselmodell A = Di–Do, B = Di–So, „Diese Woche ist A/B“ nach echter Lage)
- [x] Wochenübersicht der aktuellen und der folgenden 3 Planwochen: Eltern bestätigen die Anwesenheitstage gegen ihren echten Kalender
- [x] Kind-Fristen: in A-Woche Donnerstag 16:00, in B-Woche = Bestellfrist; dauerhaft anwesendes Kind = Bestellfrist
- [x] Ausnahme (z. B. Ferien) anlegen → Tage in der Übersicht markiert, Frist ändert sich; zweite überlappende Ausnahme → Meldung „Zeitraum überschneidet sich …“; Ausnahme löschen → Muster gilt wieder
- [x] Fristen ändern → „Vorgemerkt ab Planwoche …“ sichtbar, die nächste Planwoche zeigt noch die alte Bestellfrist; danach wieder auf So 16:00 zurückstellen
- [x] Kinderhandy: Wochenübersicht sichtbar, keine „Anwesenheit verwalten“-Schaltfläche
- [ ] WLAN aus: Übersicht zeigt „Server nicht erreichbar. Bist du im Heim-WLAN?“, nach WLAN an + Pull-to-Refresh wieder Daten
