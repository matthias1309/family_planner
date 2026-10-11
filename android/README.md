# android

Android-App: Kotlin, Jetpack Compose, Clean Architecture. Entscheidungen: [ADR-0002](../docs/02-architecture/adr/0002-kotlin-stack.md),
[ADR-0009](../docs/02-architecture/adr/0009-android-app-architecture.md).

## Module

`app` · `core:api` (aus `api/openapi.yaml` generierter Retrofit-Client) · `core:data` (Netzwerk, Session, Fehlerabbildung) ·
`core:ui` (Theme, Bausteine) · `core:testing` (Test-Helfer) · `feature:auth` (Einrichten, Koppeln, Familie verwalten).

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

- [ ] Release-APK auf dem Elternhandy installieren, „Familie einrichten“ mit dem Setup-Token
- [ ] Mitglieder anlegen, Kopplungscode je Kind erzeugen, Codes auf den Handys einlösen (alle fünf gekoppelt)
- [ ] Kinder sehen „Hallo <Name>“ und keine „Familie verwalten“-Schaltfläche
- [ ] Gerät entfernen → die App dieses Geräts zeigt beim nächsten Aufruf „abgemeldet“
- [ ] Außerhalb des WLANs: App startet, zeigt den angemeldeten Nutzer und „offline“
- [ ] Gegenprobe Pinning: Server mit anderem Zertifikat → „Sichere Verbindung fehlgeschlagen“
