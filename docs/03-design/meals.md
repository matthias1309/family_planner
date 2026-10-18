# Moduldesign: `meals` – Anwesenheit & Fristen (Phase 2a/2b)

## Abgedeckte Anforderungen

REQ-MEAL-010, REQ-MEAL-011, REQ-MEAL-012, REQ-MEAL-013 — siehe [meals.prd.md](../01-requirements/meals.prd.md).
Konventionen für Feature-Module: [ADR-0010](../02-architecture/adr/0010-backend-feature-modules.md).
Fristen ohne Fernzugriff: [ADR-0007](../02-architecture/adr/0007-meal-deadlines-without-remote-access.md).
Die Eltern-UI in der App (Phase 2b) steht im Abschnitt „App-Seite (Phase 2b)“.

## Domänenmodell & Regeln

| Begriff | Definition |
|---|---|
| `ZONE` | `Europe/Berlin` — alle Kalender-/Uhrzeitberechnungen |
| Planwoche (`PlanWeek`) | Montag einer Woche Mo–So; kein Montag → `Validation` |
| Laufende Woche zu Planwoche P | `P − 7 Tage` … `P − 1 Tag` (Fristen liegen hier) |
| Aktuelle Planwoche (zum Zeitpunkt *now*) | `monday(heute in ZONE) + 7 Tage` |
| `AttendancePattern.Always` | an allen Tagen anwesend |
| `AttendancePattern.Alternating` | Wochentyp A/B mit je einer Tagesmenge und einer Referenzwoche (Montag einer A-Woche). Typ von Datum d: `floorMod(WEEKS.between(referenceWeek, monday(d)), 2) == 0` → A, sonst B (gilt auch vor der Referenzwoche). Mindestens ein Tag in A ∪ B. |
| `AttendanceProfile` | `memberId`, `cooks`, `pattern`; nur für Rolle CHILD. Ohne gespeichertes Profil: `cooks=false`, `Always`. |
| `AttendanceException` | Zeitraum `from ≤ to` (inklusive, max. 366 Tage), `PRESENT`/`ABSENT`, Notiz 0–60 Zeichen. Überlappung mit bestehender Ausnahme desselben Kindes → 409. |
| Anwesenheit an Tag d | Ausnahme, die d enthält (`source=EXCEPTION`), sonst Muster (`source=PATTERN`) |
| `DeadlineSettings` | Bestellfrist-Wochentag (Default So), Bestellfrist-Uhrzeit (16:00), Kind-Uhrzeit (16:00), `effectiveFrom` |
| Gültige Einstellungen für P | Eintrag mit größtem `effectiveFrom ≤ P`, sonst Defaults |
| Ändern der Einstellungen (REQ-MEAL-013) | `effectiveFrom = aktuelle Planwoche + 7 Tage`; die gerade geplante Woche behält ihre Frist. Erneutes Ändern überschreibt. |
| Bestellfrist für P | Wochentag der Einstellung in der laufenden Woche, zur eingestellten Uhrzeit, in `ZONE` |
| Kind-Frist für P (REQ-MEAL-012) | nur wenn `cooks`; in P an keinem Tag anwesend → keine Frist; sonst `min(letzter Anwesenheitstag der laufenden Woche zur Kind-Uhrzeit, Bestellfrist)`; in der laufenden Woche nie anwesend → Bestellfrist |

Das Anwesenheitsmodell verallgemeinert den PRD-Wortlaut „Ankunft Di, Abreise Do/So": Wechselmodell mit Woche A = Di–Do und
Woche B = Di–So. Beispiel: Laufende Woche Typ A → letzter Tag Do → Frist **Do 16:00**; Typ B oder `Always` → So → **Bestellfrist**.

„Ab der nächsten Planwoche" (REQ-MEAL-013) heißt: die gerade geplante Woche (Montag nach heute) behält ihre Frist,
neue Werte gelten ab der Woche danach.

## Schnittstellen

Endpunkte siehe `api/openapi.yaml` (Tag `meals`), Lesen für alle Mitglieder, Schreiben nur Eltern:

- `GET /api/v1/meals/profiles`, `PUT /api/v1/meals/profiles/{memberId}`
- `GET|POST /api/v1/meals/profiles/{memberId}/exceptions`, `DELETE /api/v1/meals/exceptions/{exceptionId}`
- `GET|PUT /api/v1/meals/settings`
- `GET /api/v1/meals/weeks/{weekStart}/attendance` (Schnittstelle für Phase 3/4 und die Erinnerungen in Phase 6)

## DB-Tabellen

Migration `V2__meals_attendance.sql`: `meal_attendance_profiles` (Tagesmasken: Bit `dayOfWeek − 1`, Mo = Bit 0),
`meal_attendance_exceptions`, `meal_deadline_settings`. Daten als ISO-Text `yyyy-MM-dd`, Zeitstempel als Epoch-Millis.

## Unit-Testfälle

Siehe `backend/meals/src/test` (Namen beginnen mit der REQ-ID): Always/Alternating inkl. Parität vor der Referenzwoche und
über den Jahreswechsel, Ausnahmen (Überschreiben, Randtage, Überlappung, Länge, Notiz), Fristen (Do/So, Maximum Bestellfrist,
ganz abwesend, nicht kochend, Sommer-/Winterzeit 25.10. und 29.03.), Einstellungen (Defaults, Wirksamkeit ab nächster
Planwoche, Überschreiben, Berliner Zeit), Persistenz gegen echte SQLite.

## Eingabegrenzen

Daten müssen im Jahr 2000–2100 liegen (Datumstexte werden lexikografisch verglichen, siehe `RequestParsing.kt`); Uhrzeiten
genau im Format `HH:mm`. Verletzungen ergeben 400. Eine Frist-Uhrzeit in der Sommerzeit-Lücke (Sonntag 02:00–03:00) wird
von `ZonedDateTime` nach vorn verschoben; praktisch irrelevant.

## App-Seite (Phase 2b)

Modul `android/feature/meals` (Pakete `domain/attendance`, `data/attendance`, `ui/{week,profiles,profile,settings}`),
Schichten und Muster wie `feature:auth` ([ADR-0009](../02-architecture/adr/0009-android-app-architecture.md)).
`feature:meals` und `feature:auth` kennen sich nicht; nur `app` verbindet sie.

| Screen | Rolle | Inhalt |
|---|---|---|
| Wochenübersicht | alle | Anwesenheit Mo–So je Kind, Ausnahmetage markiert, Kind-Frist („Einreichen bis …“), Bestellfrist, Woche vor/zurück (2000–2100); Eltern: Einstieg in „Anwesenheit verwalten“ und „Fristen einstellen“ |
| Anwesenheit verwalten | Eltern | Kinder mit „kocht“ und Muster-Zusammenfassung (Tippen öffnet das Profil) |
| Profil bearbeiten | Eltern | „kocht“, immer da / Wechselmodell, Tage Woche A/B, „Diese Woche ist A/B“, Ausnahmen anlegen/löschen |
| Fristen | Eltern | Familienweit (nicht pro Kind): Bestellfrist (Wochentag, Uhrzeit), Einreichfrist der Kinder (Uhrzeit), Anzeige „gilt jetzt“ / „vorgemerkt ab Planwoche …“ |

Regeln:

- **Planwoche:** Die „aktuelle“ Planwoche ist der Montag nach heute in Berlin (`currentPlanWeek`); Sonntag 23:30 Berlin
  zählt noch zur laufenden Woche, Montag 00:30 schon zur nächsten.
- **Referenzwoche:** Eltern wählen „Diese Woche (Datum–Datum) ist Woche A|B“; die App rechnet daraus die Referenzwoche
  (`referenceWeekFor`) und beim Laden zurück (`weekTypeOf`, `floorMod`, damit die Parität auch vor der Referenzwoche
  stimmt). Ein unverändert gespeichertes Profil behält dadurch dieselbe Parität.
- **Keine Client-Berechnung** von Anwesenheit oder Fristen: Die Wochenübersicht zeigt, was der Server liefert.
- **Fehler:** 409 (Ausnahme überschneidet sich) zeigt die Servermeldung im Dialog; 403 „Nur für Eltern.“; offline
  „Server nicht erreichbar. Bist du im Heim-WLAN?“ mit Pull-to-Refresh. Ein Kind ohne Namen (gerade gelöscht) erscheint als „?“.
- **Datumsauswahl:** Der Material3-Picker liefert UTC-Mitternacht; die Umrechnung läuft über `ZoneOffset.UTC`
  (`pickerMillisToDate`), nie über die Systemzone.
- **Kein Cache:** Offline-Lesen kommt mit REQ-MEAL-002 (Phase 3).
- **Einstieg:** `feature:auth` bietet auf der Startseite den Slot `homeActions`; `app` füllt ihn mit `MealsHomeActions`.
- **Formatierung** (`formatDate`, `formatTime`, `formatDateTime`, `BerlinZone`) liegt zentral in `core:ui`.
