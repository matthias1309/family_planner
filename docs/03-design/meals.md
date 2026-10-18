# Moduldesign: `meals` – Anwesenheit, Fristen & Wochenplan

## Abgedeckte Anforderungen

REQ-MEAL-010, REQ-MEAL-011, REQ-MEAL-012, REQ-MEAL-013 (Phase 2), REQ-MEAL-001, REQ-MEAL-032 und – auf der App-Seite
(Phase 3b) – REQ-MEAL-002 (Phase 3) — siehe [meals.prd.md](../01-requirements/meals.prd.md).
Konventionen für Feature-Module: [ADR-0010](../02-architecture/adr/0010-backend-feature-modules.md).
Fristen ohne Fernzugriff: [ADR-0007](../02-architecture/adr/0007-meal-deadlines-without-remote-access.md).
Die Eltern-UI in der App (Phase 2b) steht im Abschnitt „App-Seite (Phase 2b)“, Essensplan und Offline-Cache im Abschnitt
„App-Seite (Phase 3b)“ ([ADR-0011](../02-architecture/adr/0011-app-offline-cache.md)).

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
- `GET /api/v1/meals/weeks/{weekStart}/plan` (Wochenplan, alle Mitglieder)
- `POST /api/v1/meals/dishes`, `PUT|DELETE /api/v1/meals/dishes/{dishId}` (Elterngerichte, nur Eltern)

## DB-Tabellen

Migration `V2__meals_attendance.sql`: `meal_attendance_profiles` (Tagesmasken: Bit `dayOfWeek − 1`, Mo = Bit 0),
`meal_attendance_exceptions`, `meal_deadline_settings`. Daten als ISO-Text `yyyy-MM-dd`, Zeitstempel als Epoch-Millis.

Migration `V3__meals_plan.sql`: `meal_dishes` (eindeutiger **Index** auf `meal_date` = ein Gericht je Tag; als Index statt
Constraint, damit Phase 4 ihn ersetzen kann) und `meal_dish_ingredients` (`dish_id`, `position`, `text`; eine Zeile je Zutat).

## Unit-Testfälle

Siehe `backend/meals/src/test` (Namen beginnen mit der REQ-ID): Always/Alternating inkl. Parität vor der Referenzwoche und
über den Jahreswechsel, Ausnahmen (Überschreiben, Randtage, Überlappung, Länge, Notiz), Fristen (Do/So, Maximum Bestellfrist,
ganz abwesend, nicht kochend, Sommer-/Winterzeit 25.10. und 29.03.), Einstellungen (Defaults, Wirksamkeit ab nächster
Planwoche, Überschreiben, Berliner Zeit), Persistenz gegen echte SQLite.

## Eingabegrenzen

Daten müssen im Jahr 2000–2100 liegen (Datumstexte werden lexikografisch verglichen, siehe `RequestParsing.kt`); Uhrzeiten
genau im Format `HH:mm`. Verletzungen ergeben 400. Eine Frist-Uhrzeit in der Sommerzeit-Lücke (Sonntag 02:00–03:00) wird
von `ZonedDateTime` nach vorn verschoben; praktisch irrelevant.

## Wochenplan & Elterngerichte (Phase 3)

| Begriff | Definition |
|---|---|
| `Dish` | `id`, `date`, `cookId`, `name`, `ingredients` (Liste von Zeilen) |
| Ein Gericht je Tag | Ein Tag hat höchstens ein Gericht (eine Hauptmahlzeit); zweites Gericht oder Verschieben auf einen belegten Tag → 409 |
| `PlanDay` / `WeekPlan` | Woche = genau 7 Tage Mo–So, je Tag Gericht oder `null` („noch offen“, REQ-MEAL-001) |
| Woche lesen | jede Woche (Montag 2000–2100), alle Mitglieder; welche Woche die App zeigt, entscheidet die App (3b) |
| Name | getrimmt, 1–60 Zeichen |
| Zutaten | Freitext, eine Zeile je Zutat; Zeilen getrimmt, Leerzeilen entfernt, max. 80 Zeichen je Zeile, max. 30 Zeilen (nach dem Entfernen); roh höchstens 100 Zeilen inkl. Leerzeilen, damit die Eingabe begrenzt bleibt |
| Koch/Köchin | muss existieren (404) und – in Phase 3 – Elternteil sein (400); Phase 4 öffnet das für Kinder (Stellvertretung) |
| Ändern | ersetzt Datum, Koch/Köchin, Name, Zutaten vollständig |
| Vergangene Tage | erlaubt, keine Sperre |

Entscheidungen (Anwender 2026-10-18): ein Gericht je Tag; Zutaten bei Elterngerichten optional (Reste, Essen gehen) — Pflicht
erst für Kindergerichte (REQ-MEAL-020); Koch/Köchin frei unter den Eltern wählbar. Zutaten liegen in der DB als eigene Zeilen
(Reihenfolge, späterer Ausbau zu strukturierten Zutaten), in der API als `string[]`. Der zweite Teil von REQ-MEAL-032
(„Zutaten stehen auf der Einkaufsliste“) wird in Phase 5 umgesetzt. Offline-Lesen (REQ-MEAL-002) ist App-Seite (Phase 3b);
die API liefert nur IDs, Namen löst die App über `GET /api/v1/members` auf.

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
- **Kein Cache** in der Wochenübersicht; offline lesbar ist der Essensplan (Phase 3b).
- **Einstieg:** `feature:auth` bietet auf der Startseite den Slot `homeActions`; `app` füllt ihn mit `MealsHomeActions`.
- **Formatierung** (`formatDate`, `formatTime`, `formatDateTime`, `BerlinZone`) liegt zentral in `core:ui`.

## App-Seite (Phase 3b)

Pakete `domain/plan`, `data/plan`, `ui/plan`, `ui/dish` im Modul `android/feature/meals`. Der Offline-Cache folgt
[ADR-0011](../02-architecture/adr/0011-app-offline-cache.md). `api/` und `backend/` bleiben unverändert (Vertrag aus 3a).

| Screen | Rolle | Inhalt |
|---|---|---|
| Essensplan | alle | Woche Mo–So (‹ ›), je Tag Gericht, Koch/Köchin und Zutaten kurz, „noch offen“, „Heute“ markiert; Eltern (online): Tag antippen = anlegen/ändern |
| Gericht-Editor | Eltern | Tag (belegte Tage gesperrt), Koch/Köchin (nur Eltern), Name, Zutaten (eine pro Zeile, optional), Speichern, Löschen mit Bestätigung |

Regeln:

- **Startwoche:** diese Woche (`currentWeek`, Montag in Berlin), nicht die Planwoche; am Sonntag ist die kommende Woche einen Schritt entfernt (REQ-MEAL-001).
- **Prefetch:** Beim Öffnen werden diese und die nächste Woche geladen; die nächste läuft im Hintergrund nur für den Cache.
- **Cache (ADR-0011):** Jede erfolgreich geladene Woche wird mit Synchronisierungszeitpunkt und aufgelösten Namen von Koch/Köchin
  gespeichert; Besitzer ist das angemeldete Mitglied; Wochen vor der Vorwoche werden entfernt, höchstens 8 bleiben.
- **Offline-Fallback nur bei Verbindungsproblemen** (`Network`, `Certificate`, `Server`): Anzeige „Offline – Stand: <Wochentag Datum, Uhrzeit>“; bei `Server` (erreichbar, aber gestört) „Server gestört – gespeicherter Stand: …“.
  Andere Fehler (403, 404, …) werden gezeigt und nie vom Cache verdeckt.
- **Offline keine Bearbeitung:** Eltern sehen „Bearbeiten ist nur im Heim-WLAN möglich.“; Kinder können nie bearbeiten.
- **Editor:** nur Tage derselben Woche; Zutaten optional; Grenzen wie der Server (Name 60, Zutat 80, 30 Zutaten, 100 Rohzeilen).
  Ein Koch/Köchin, der kein Elternteil mehr ist, muss neu gewählt werden.
- **Fehler:** 409 „An diesem Tag ist schon ein Gericht geplant“ (Servermeldung, Formular bleibt), 403 „Nur für Eltern.“,
  404 beim Löschen zählt als gelöscht, offen als „Server nicht erreichbar. Bist du im Heim-WLAN?“.
- **Namen:** Koch/Köchin kommen aus `GET /api/v1/members`; unbekannte erscheinen als „?“.
