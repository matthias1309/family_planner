# android

Android-App: Kotlin, Jetpack Compose, Clean Architecture. Entscheidungen: [ADR-0002](../docs/02-architecture/adr/0002-kotlin-stack.md),
[ADR-0009](../docs/02-architecture/adr/0009-android-app-architecture.md).

## Module

`app` · `core:api` (aus `api/openapi.yaml` generierter Retrofit-Client) · `core:data` (Netzwerk, Session, Fehlerabbildung) ·
`core:ui` (Theme, Bausteine) · `core:testing` (Test-Helfer) · `feature:auth` (Einrichten, Koppeln, Familie verwalten) · `feature:meals` (Anwesenheit & Fristen, Essensplan – offline lesbar, Einreichen, Freigeben, Absagen, Wochenstatus, Rezepte – Liste, Ansicht, Editor, Gericht aus Rezept).

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
./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerifyDebug assembleDebug   # wie die CI
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

Abgenommen am 2026-10-18; der Offline-Punkt wurde mit Phase 3b nachgeholt.

- [x] Release-APK auf dem Elternhandy installieren, „Familie einrichten“ mit dem Setup-Token
- [x] Mitglieder anlegen, Kopplungscode je Kind erzeugen, Codes auf den Handys einlösen (alle fünf gekoppelt)
- [x] Kinder sehen „Hallo <Name>“ und keine „Familie verwalten“-Schaltfläche
- [x] Gerät entfernen → die App dieses Geräts zeigt beim nächsten Aufruf „abgemeldet“
- [ ] Außerhalb des WLANs: App startet, zeigt den angemeldeten Nutzer und „offline“
- [x] Gegenprobe Pinning: Server mit anderem Zertifikat → „Sichere Verbindung fehlgeschlagen“

## Abnahme-Checkliste Phase 2b (manuell, echte Geräte gegen den Pi)

Abgenommen am 2026-10-18; der Offline-Punkt wurde mit Phase 3b nachgeholt.

- [x] Release-APK 0.2.0 (gleiche Signatur) per `adb install -r` auf Elternhandy und einem Kinderhandy aktualisieren; Kopplung bleibt erhalten
- [x] Eltern: für jedes Kind „kocht“ und Muster setzen (Wechselmodell A = Di–Do, B = Di–So, „Diese Woche ist A/B“ nach echter Lage)
- [x] Wochenübersicht der aktuellen und der folgenden 3 Planwochen: Eltern bestätigen die Anwesenheitstage gegen ihren echten Kalender
- [x] Kind-Fristen: in A-Woche Donnerstag 16:00, in B-Woche = Bestellfrist; dauerhaft anwesendes Kind = Bestellfrist
- [x] Ausnahme (z. B. Ferien) anlegen → Tage in der Übersicht markiert, Frist ändert sich; zweite überlappende Ausnahme → Meldung „Zeitraum überschneidet sich …“; Ausnahme löschen → Muster gilt wieder
- [x] Fristen ändern → „Vorgemerkt ab Planwoche …“ sichtbar, die nächste Planwoche zeigt noch die alte Bestellfrist; danach wieder auf So 16:00 zurückstellen
- [x] Kinderhandy: Wochenübersicht sichtbar, keine „Anwesenheit verwalten“-Schaltfläche
- [x] WLAN aus: Übersicht zeigt „Server nicht erreichbar. Bist du im Heim-WLAN?“, nach WLAN an + Pull-to-Refresh wieder Daten

## Abnahme-Checkliste Phase 3b (manuell, echte Geräte gegen den Pi)

Abgenommen am 2026-10-18 durch den Anwender, alle Punkte.

- [x] Release-APK 0.3.0 (gleiche Signatur) per `adb install -r` auf Elternhandy und einem Kinderhandy; Kopplung bleibt erhalten
- [x] Home zeigt „Essensplan“ und „Anwesenheit & Fristen“
- [x] Eltern: für die kommende Woche an mehreren Tagen Gerichte mit/ohne Zutaten anlegen, eines ändern, eines auf einen anderen Tag verschieben, eines löschen
- [x] Belegter Tag ist im Editor nicht wählbar; zweites Elternhandy legt zeitgleich am selben Tag an → Meldung „An diesem Tag ist schon ein Gericht geplant“
- [x] Kinderhandy: Plan dieser Woche mit Gericht und Koch/Köchin je Tag, offene Tage „noch offen“, „Heute“ markiert; am Sonntag mit › zur kommenden Woche; keine Bearbeitung möglich
- [x] Kinderhandy im WLAN öffnen, dann WLAN aus (mobile Daten an): Plan dieser **und** der nächsten Woche mit „Offline – Stand: …“ sichtbar
- [x] Elternhandy offline: Plan lesbar, Hinweis „Bearbeiten ist nur im Heim-WLAN möglich.“
- [x] Zurück im WLAN + Pull-to-Refresh: Hinweis verschwindet, aktueller Stand
- [x] Nachtrag 2b: WLAN aus → Wochenübersicht zeigt „Server nicht erreichbar. Bist du im Heim-WLAN?“ (offener Punkt der 2b-Checkliste)

## Abnahme-Checkliste Phase 4b (manuell, echte Geräte gegen den Pi)

Abgenommen am 2026-10-18 durch den Anwender, alle Punkte.

- [x] Pi läuft mit Image inkl. Migration V4 (4a); Release-APK 0.4.0 (gleiche Signatur) per `adb install -r` auf Eltern- und Kinderhandy
- [x] Kinderhandy (kochendes Kind) an Sa/So: Essensplan zeigt „Zur Planwoche … ›“; dort „Mein Kochtag“ mit Frist
- [x] Kind reicht ein: nur Anwesenheitstage wählbar, ohne Zutaten Meldung; danach „wartet auf Freigabe“ in Karte und Plan
- [x] Elternhandy: Wochenstatus zeigt offene Tage, Kind „wartet auf Freigabe“, anderes Kind „noch nicht eingereicht, bis …“
- [x] Eltern lehnen mit Begründung ab → Kinderhandy (Pull-to-Refresh) zeigt „Abgelehnt: …“; Kind überarbeitet → wieder „wartet“
- [x] Eltern geben frei → „Freigegeben“; Kind ändert → wieder „wartet auf Freigabe“
- [x] Eltern tragen für das zweite Kind über „Eintragen“ ein → direkt freigegeben, nur dessen Anwesenheitstage wählbar
- [x] Eltern legen für das Kind eine Abwesenheits-Ausnahme auf dessen Kochtag an → Plan „⚠ Tag ungültig“; Gericht-Screen
      „Gericht verschieben“: Felder gesperrt, kein Freigeben; auf gültigen Tag verschieben → freigegeben; Variante: löschen
- [x] Kind sagt mit Begründung ab → Gericht verschwindet, Status „abgesagt: …“; Eltern/Kind nehmen die Absage zurück
- [x] Alle Tage belegt und freigegeben → „✓ Bereit zur Bestellung“
- [x] Zweites Elternhandy gibt gleichzeitig frei → Meldung „Das Gericht wartet nicht auf Freigabe“
- [x] WLAN aus: Plan mit Badges lesbar, „Wochenstatus nur im Heim-WLAN.“, keine Karten

## Abnahme-Checkliste Phase 5b (manuell, echte Geräte gegen den Pi)

- [x] Pi läuft mit Image inkl. Migration V5 (5a); Release-APK 0.5.0 (gleiche Signatur) per `adb install -r` auf Eltern- und Kinderhandy
- [x] Elternhandy: Startseite → „Rezepte“ → „Neues Rezept“: Lauch-Carbonara mit 4 Portionen und Zutaten als Zeilen
      („500 g Vollkornspaghetti“, „Salz“, „2 Stange(n) Lauch“, …) und Schritten anlegen; ohne Zutaten/mit 0 Portionen gibt es eine Meldung
- [x] Gleicher Name noch einmal → „Ein Rezept mit diesem Namen gibt es schon“
- [x] Rezeptliste: Suche „lauch“ findet das Rezept, „xyz“ zeigt „Kein Rezept passt …“
- [x] Kinderhandy: Rezepte sichtbar, kein „Neues Rezept“, in der Ansicht kein Bearbeiten/Löschen
- [x] Kind reicht über „Mein Kochtag“ ein: „Aus Rezept wählen“ → Carbonara; Portionen vorbelegt mit den Anwesenden des Tages; auf 5 stellen →
      „625 g Vollkornspaghetti“, „2,5 Stange(n) Lauch“, „Salz“ ohne Menge; einreichen
- [x] Essensplan (beide Handys): Gericht mit „Rezept ›“; Ansicht zeigt „Für 5 Portionen“, die Gerichtsmengen und die Schritte
- [x] Eltern ändern danach das Rezept (z. B. 600 g) → das eingereichte Gericht bleibt bei 625 g
- [x] Eltern löschen das Rezept → Gericht bleibt mit seinen Zutaten, „Rezept ›“ verschwindet nach Neuladen
- [x] Elterngericht ohne Rezept mit „500g Mehl“ speichern → Plan zeigt „500 g Mehl“
- [x] WLAN aus: Plan ohne „Rezept ›“; Rezeptliste zeigt „Server nicht erreichbar …“

## Abnahme-Checkliste Phase 6b (manuell, echte Geräte gegen den Pi)

- [x] Pi läuft mit Image inkl. Migration V6 (6a); Release-APK 0.6.0 (gleiche Signatur) per `adb install -r` auf Eltern- und Kinderhandy
- [x] Planwoche mit mindestens zwei freigegebenen Gerichten mit gemeinsamer Zutat (z. B. „2 Stange(n) Lauch“ in beiden) und einem
      Kindergericht „wartet auf Freigabe“
- [x] Elternhandy: Startseite → „Einkaufsliste“ öffnet die Planwoche; Lauch steht einmal mit addierter Menge und beiden Gerichten als
      Herkunft; „Salz“ ohne Menge einmal; Zutaten des wartenden Gerichts fehlen
- [x] „200 g Sahne“ und „1 Becher Sahne“ bleiben getrennte Einträge
- [x] Essensplan → Wochenstatus → „Einkaufsliste ›“ öffnet dieselbe Woche
- [x] Abhaken → Eintrag wandert durchgestrichen nach „Erledigt“; erneut tippen → wieder offen
- [x] „2 kg Kartoffeln“ hinzufügen → erscheint als „2 kg Kartoffeln“ mit „Eigener Eintrag“; löschen → verschwindet
- [x] Leeres Feld → „Hinzufügen“ deaktiviert
- [x] Zweites Elternhandy sieht nach Pull-to-Refresh dieselben Häkchen
- [x] Kind gibt danach ein weiteres Gericht frei bzw. Eltern geben das wartende Gericht frei → neue Zutaten erscheinen; ein abgehakter
      Eintrag mit geänderter Menge ist wieder offen
- [x] Kinderhandy: Liste sichtbar, keine Checkboxen, kein Eingabefeld, kein Löschen; erledigte Einträge mit „✓“
- [x] WLAN aus: „Server nicht erreichbar. Bist du im Heim-WLAN?“
- [ ] Erfolgssignal Phase 6: Sonntagsbestellung bei Picnic nur anhand der Liste

## Abnahme-Checkliste Phase 7 (manuell, echte Geräte gegen den Pi)

Abgenommen am 2026-10-24 durch den Anwender; das Erfolgssignal wird im Pilot (Phase 8) geprüft.

- [x] Release-APK 0.7.0 (gleiche Signatur) per `adb install -r` auf Eltern- und Kinderhandy; beim Start Benachrichtigungen erlauben
- [x] Akku-Optimierung der App auf den Kinderhandys „nicht eingeschränkt“ (Herstellereinstellung), Ergebnis notieren
- [x] Kinderhandy, Kind kocht in der Planwoche, nichts eingereicht: `adb shell dumpsys alarm | grep -A3 de.familyplanner.android` zeigt die künftigen Erinnerungen (72/48/24 h)
- [x] Erinnerung kommt zur Frist-Uhrzeit (± 1 h) — mindestens eine im **Mobilnetz/außer Haus**; Text nennt Frist und WhatsApp-Hinweis
- [x] Tippen öffnet den Essensplan; außer Haus erscheint die Karte „Nicht zu Hause?“; „Per WhatsApp schicken“ öffnet WhatsApp mit Vorlage
- [x] Kind reicht ein → keine weiteren Erinnerungen dieser Woche
- [x] Elternhandy: nach Einreichung (≤ 30 min im WLAN) Meldung „Ben hat ein Gericht eingereicht“; Tippen öffnet Essensplan
- [x] Eltern lehnen mit Begründung ab → Kinderhandy meldet „wurde abgelehnt“ mit Grund; nächste Erinnerung heißt „überarbeiten“
- [x] Eltern geben frei → Kind erhält „ist freigegeben“
- [x] Eltern tragen stellvertretend ein → Eltern erhalten keine Meldung
- [x] Kinderhandy neu starten (außer Haus) → Erinnerung kommt trotzdem
- [x] Kind sagt ab → keine Erinnerungen mehr
- [ ] Erfolgssignal Phase 7: Kinder erhalten Erinnerungen auch außer Haus pünktlich (eine Pilotwoche)

## Abnahme-Checkliste Phase 5c (manuell, echtes Gerät mit Internet)

- [x] Release-APK 0.5.1 per `adb install -r` auf Eltern- und Kinderhandy
- [x] Elternhandy, Chrome: `rewe.de/rezepte/spaghetti-lauch-carbonara-mit-ohne-speck/` → Teilen → Family Planner → Editor mit Name, 4 Portionen,
      11 Zutaten („500 g Vollkornspaghetti“, „2 Stange(n) Lauch“, „Salz“ ohne Menge), Schritten und Quelle
- [x] Zurück ohne Speichern → Rezept ist nicht in der Liste
- [x] Erneut teilen, eine Zutat korrigieren, Speichern → Rezept in der Liste, Ansicht zeigt die Korrektur
- [x] „Neues Rezept“ → Link `rewe.de/rezepte/bunte-gemuese-quesadilla/` einfügen → „Übernehmen“ → „2 EL Rapsöl“, „1 Dose(n) Kidney-Bohnen“,
      „200 g Gouda gerieben“, „Saft von 1 Limette“ als Name; speichern
- [x] Teilen aus der REWE-App (falls installiert) funktioniert ebenso
- [x] Link zu einer Seite ohne Rezept (z. B. `https://www.rewe.de/`) → „Auf dieser Seite wurde kein Rezept gefunden …“, Formular nutzbar
- [x] Flugmodus + WLAN aus → „Die Seite ließ sich nicht laden …“
- [x] Kinderhandy: Link teilen → „Nur für Eltern.“
- [x] Abgemeldetes Gerät (falls verfügbar, sonst entfällt): Link teilen → nach Anmeldung öffnet der Editor
- [x] Erfolgssignal Phase 5: Kind reicht eines der importierten Rezepte mit umgerechneten Mengen ein

## Test-Build (ADR-0015)

Läuft neben der Familien-App (eigene `applicationId` `de.familyplanner.android.test`, Name „Familienplaner Test“) und spricht
mit der Testinstanz auf Port 8097:

```bash
FP_TEST_INSTANCE=true ./gradlew assembleRelease
```

Der Schalter kann auch als `fp.testInstance=true` in `local.properties` stehen. Kopplung: „Familie einrichten“ mit dem Token aus
`.env.test` der Testinstanz (siehe [deploy/README.md](../deploy/README.md)).

## Abnahme-Checkliste TASK Phase 1 (manuell, Testhandys gegen die Testinstanz)

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

