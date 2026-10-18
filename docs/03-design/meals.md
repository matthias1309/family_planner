# Moduldesign: `meals` – Anwesenheit, Fristen, Wochenplan, Einreichung & Rezepte

## Abgedeckte Anforderungen

REQ-MEAL-010, REQ-MEAL-011, REQ-MEAL-012, REQ-MEAL-013 (Phase 2), REQ-MEAL-001, REQ-MEAL-032 und – auf der App-Seite
(Phase 3b) – REQ-MEAL-002 (Phase 3) sowie REQ-MEAL-020, -021, -022, -023, -030, -031, -033 (Phase 4, Backend/API) sowie REQ-MEAL-060, -062 und -063 (Server-Anteil: Rezeptsammlung, Rezeptbezug und strukturierte Zutaten am Gericht, Phase 5a; App: Phase 5b) — siehe [meals.prd.md](../01-requirements/meals.prd.md).
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
- `GET /api/v1/meals/weeks/{weekStart}/attendance` (Schnittstelle für Phase 3/4 und die Erinnerungen in Phase 7)
- `GET /api/v1/meals/weeks/{weekStart}/plan` (Wochenplan, alle Mitglieder)
- `POST /api/v1/meals/dishes`, `PUT /api/v1/meals/dishes/{dishId}` (Eltern; Kinder nur eigene Gerichte, Phase 4), `DELETE /api/v1/meals/dishes/{dishId}` (nur Eltern)
- `POST /api/v1/meals/dishes/{dishId}/approval`, `POST …/rejection` (Phase 4, nur Eltern, 204)
- `PUT|DELETE /api/v1/meals/weeks/{weekStart}/cancellations/{memberId}` (Absage und Rücknahme, Kind für sich, Eltern für jedes Kind)
- `GET /api/v1/meals/weeks/{weekStart}/status` (Wochenstatus, alle Mitglieder)
- `GET /api/v1/meals/recipes?query=` (suchen, alle), `GET /api/v1/meals/recipes/{recipeId}` (alle), `POST /api/v1/meals/recipes`, `PUT|DELETE /api/v1/meals/recipes/{recipeId}` (nur Eltern, Phase 5a)
- Gericht (`Dish`) zusätzlich mit `structuredIngredients`, `recipeId`, `servings`; `ingredients: string[]` bleibt als Anzeigetext erhalten

## DB-Tabellen

Migration `V2__meals_attendance.sql`: `meal_attendance_profiles` (Tagesmasken: Bit `dayOfWeek − 1`, Mo = Bit 0),
`meal_attendance_exceptions`, `meal_deadline_settings`. Daten als ISO-Text `yyyy-MM-dd`, Zeitstempel als Epoch-Millis.

Migration `V3__meals_plan.sql`: `meal_dishes` (eindeutiger **Index** auf `meal_date` = ein Gericht je Tag; als Index statt
Constraint, damit Phase 4 ihn ersetzen kann) und `meal_dish_ingredients` (`dish_id`, `position`, `text`; eine Zeile je Zutat).

Migration `V4__meals_submissions.sql`: `meal_dishes` + `status` (Default `APPROVED`), `rejection_reason`, `late`; Index
`(cook_member_id, meal_date)`; Tabelle `meal_cooking_cancellations` (`member_id`, `week_start`, `reason`, `created_at`).

Migration `V5__meals_recipes.sql`: `meal_recipes` (eindeutiger `name_key` = Name in Kleinbuchstaben), `meal_recipe_ingredients`,
`meal_recipe_steps`; `meal_dish_ingredients` + `quantity`, `unit` (Spalte `text` bleibt und bedeutet „Name“); `meal_dishes` + `recipe_id`
(`ON DELETE SET NULL`), `servings`.

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
| Zutaten | (ab Phase 5: strukturiert, siehe „Rezepte & strukturierte Zutaten“) Freitext, eine Zeile je Zutat; Zeilen getrimmt, Leerzeilen entfernt, max. 80 Zeichen je Zeile, max. 30 Zeilen (nach dem Entfernen); roh höchstens 100 Zeilen inkl. Leerzeilen, damit die Eingabe begrenzt bleibt |
| Koch/Köchin | muss existieren (404); Elternteil → Regeln dieses Abschnitts, Kind → siehe „Einreichung & Freigabe (Phase 4)“ |
| Ändern | ersetzt Datum, Koch/Köchin, Name, Zutaten vollständig |
| Vergangene Tage | erlaubt, keine Sperre |

Entscheidungen (Anwender 2026-10-18): ein Gericht je Tag; Zutaten bei Elterngerichten optional (Reste, Essen gehen) — Pflicht
erst für Kindergerichte (REQ-MEAL-020); Koch/Köchin frei unter den Eltern wählbar. Zutaten liegen in der DB als eigene Zeilen
(Reihenfolge, späterer Ausbau zu strukturierten Zutaten), in der API als `string[]`. Der zweite Teil von REQ-MEAL-032
(„Zutaten stehen auf der Einkaufsliste“) wird in Phase 6 umgesetzt (Phasenfolge geändert, ADR-0012). Offline-Lesen (REQ-MEAL-002) ist App-Seite (Phase 3b);
die API liefert nur IDs, Namen löst die App über `GET /api/v1/members` auf.

## Einreichung & Freigabe (Phase 4)

Entscheidungen des Anwenders (2026-10-18): Ändern nach der Frist erlaubt und als „nach Frist“ markiert (keine Sperre,
ADR-0007); Absage durch Kind **und** Eltern, Rücknahme möglich; Wochenstatus für **alle** Mitglieder lesbar; Kinder löschen nicht
selbst, sondern sagen nur ab. Backend/API: 4a; App: Abschnitt „App-Seite (Phase 4b)“.

| Begriff | Definition |
|---|---|
| `DishStatus` | `SUBMITTED` („wartet auf Freigabe“), `APPROVED` („freigegeben“), `REJECTED` („abgelehnt“). Bestehende und von Eltern gespeicherte Gerichte sind `APPROVED`. |
| `Dish` (erweitert) | + `status`, `rejectionReason`, `late` |
| `Actor` | `id`, `role` — wer schreibt (aus dem Geräte-Token) |
| Kocht in Woche W | Kind hat in `AttendanceService.week(W)` eine Einreichfrist (`cooks=true` und in W an mind. einem Tag anwesend) |
| Regeln für **jedes** Kindergericht (Kind oder Eltern speichern) | (1) Kind kocht in der Planwoche, sonst 400; (2) Tag ist Anwesenheitstag, sonst 400 (REQ-MEAL-021); (3) mind. eine Zutat, sonst 400 (REQ-MEAL-020); (4) höchstens **ein** Gericht je Kind und Planwoche, sonst 409; (5) keine Absage für Kind+Woche, sonst 409. (4)+(5) atomar im Repository. |
| Kind als Akteur | darf nur `cookId == eigene ID` anlegen und nur eigene Gerichte ändern (sonst 403); Planwoche ≥ aktuelle Planwoche, sonst 400; Löschen: 403 |
| Status beim Speichern | Kind: immer `SUBMITTED`, Ablehnungsgrund gelöscht, `late = jetzt > Einreichfrist` (bei **jedem** Speichern neu). Eltern: immer `APPROVED`, Grund gelöscht; `late` beim Anlegen `false`, beim Ändern übernommen, wenn der Koch gleich bleibt. |
| Elterngericht | Regeln aus Phase 3 unverändert (Zutaten optional, keine Wochen-/Anwesenheitsprüfung) |
| Freigabe / Ablehnung (REQ-MEAL-030) | nur Eltern, nur aus `SUBMITTED`, sonst 409; Ablehnung braucht Begründung (1–200 Zeichen). Abgelehntes Gericht blockiert den Tag, bis das Kind es ändert (→ `SUBMITTED`) oder Eltern es löschen. |
| Absage (REQ-MEAL-023) | `PUT …/cancellations/{memberId}` mit Begründung (1–200); Ziel ist ein Kind, das in der Woche kocht, Woche ≥ aktuelle Planwoche; idempotent; **löscht atomar** das Gericht des Kindes in der Woche samt Zutaten. `DELETE` nimmt die Absage zurück (404 ohne Absage). |
| `dayValid` (REQ-MEAL-021) | beim Lesen berechnet: Kindergericht und Kind an dem Tag laut Anwesenheit (inkl. Ausnahmen) abwesend → `false`. |
| Zustand je Koch | Absage → `CANCELLED`; sonst Gericht der Woche → dessen Status; sonst `NOT_SUBMITTED`. Köche = Kinder, die kochen, plus Kinder mit einem Gericht in der Woche. |
| `WeekStatus` (REQ-MEAL-033) | `openDays`, `cooks`, `orderDeadline`, `readyToOrder = keine offenen Tage ∧ alle Gerichte APPROVED ∧ alle dayValid` |

Statusübergänge:

| Aktion | Akteur | vorher | nachher |
|---|---|---|---|
| anlegen | Kind (eigenes) | – | `SUBMITTED`, `late` berechnet |
| anlegen | Eltern | – | `APPROVED` |
| ändern | Kind (eigenes) | beliebig | `SUBMITTED`, Grund gelöscht, `late` neu |
| ändern | Eltern | beliebig | `APPROVED`, Grund gelöscht |
| freigeben | Eltern | `SUBMITTED` | `APPROVED` |
| ablehnen | Eltern | `SUBMITTED` | `REJECTED` + Grund |
| absagen | Kind / Eltern | Gericht der Woche (beliebig) | gelöscht, Absage gespeichert |
| löschen | Eltern | beliebig | gelöscht |

Freigabe und Ablehnung antworten mit 204 ohne Body; die App lädt Plan und Status danach neu. Benachrichtigungen
(REQ-MEAL-052) und Einkaufsliste (Phase 6) sind nicht Teil von 4a.

## Rezepte & strukturierte Zutaten (Phase 5a)

Architektur und Begründung: [ADR-0012](../02-architecture/adr/0012-recipes-and-rewe-import.md) (Kopie statt Verknüpfung, additive API,
Rezepte nur durch Eltern). Phase 5a ist Backend/API; Oberflächen und Umrechnung: „App-Seite (Phase 5b)“; REWE-Import: Phase 5c.

| Begriff | Definition |
|---|---|
| `Ingredient` | `name`, optional `quantity` (Double) und `unit` |
| `Recipe` | `id`, `name`, `servings`, `ingredients`, `steps`, `sourceUrl`, `createdBy`; `RecipeSummary` = `id`, `name`, `servings` |
| `RecipeRef` | am Gericht: `recipeId` und `servings` („übernommen aus Rezept X für N Portionen“) |
| Anzeigetext | `displayText()`: Menge ohne überflüssige Nullen mit Dezimalkomma, dann Einheit, dann Name, getrennt durch Leerzeichen; fehlende Teile entfallen (`500 g Vollkornspaghetti`, `2,5 Stange(n) Lauch`, `Salz`) |

| Thema | Regel |
|---|---|
| Zutat | `name` 1–80 nach Trim; `unit` nach Trim leer → `null`, sonst ≤ 20; `quantity` `null` oder `0,001 ≤ q ≤ 10000`, gerundet auf 3 Nachkommastellen; höchstens 30 Zutaten |
| Freitextzeilen (alt) | roh ≤ 100 Zeilen, getrimmt, Leerzeilen entfernt → Zutaten nur mit Name |
| Dish-Request | `structuredIngredients` gesetzt → diese gelten, `ingredients` muss leer sein (sonst 400); sonst gelten die Zeilen |
| Rezeptbezug am Gericht | `recipeId` und `servings` nur zusammen (sonst 400); `servings` 1–20; Rezept muss existieren (404); Kinder dürfen `recipeId` setzen |
| Reihenfolge der Prüfungen (Gericht) | 403 fremdes Gericht → 400 Eingaben → 404 Mitglied/Rezept → Kind-Regeln aus Phase 4 → 409 Repository |
| Gericht ändern | ersetzt auch den Rezeptbezug vollständig (fehlt er im Request → `null`) |
| Ältere App (Stand 4b) | Sendet sie beim Ändern genau die Anzeigezeilen des gespeicherten Gerichts zurück (ohne `structuredIngredients`/`recipeId`), bleiben Zutatenstruktur und Rezeptbezug erhalten; bei geänderten Zeilen werden sie ersetzt (Zeilen werden Zutaten nur mit Name, Rezeptbezug entfällt) |
| Mengen am Gericht | Der Server speichert, was kommt; keine Umrechnung auf dem Server |
| Rezept schreiben | nur Eltern (403 „Nur für Eltern“), lesen/suchen alle Mitglieder |
| Rezept-Name | getrimmt, 1–60, eindeutig ohne Groß-/Kleinschreibung (`name_key`), sonst 409 |
| Rezept-Portionen | 1–20, sonst 400 |
| Rezept-Zutaten | mindestens 1, sonst 400 |
| Schritte | roh ≤ 100, getrimmt, leere entfernt, danach ≤ 30, je ≤ 1000 Zeichen; 0 Schritte erlaubt |
| Quell-URL | getrimmt, leer → `null`; ≤ 500; parsebar, Schema `http`/`https`, Host nicht leer, sonst 400 |
| Suche | `query` getrimmt, leer → alle, > 60 → 400; Treffer = Name enthält Query (ohne Groß-/Kleinschreibung); sortiert nach Name |
| Rezept ändern | ersetzt Inhalt vollständig, `createdBy` bleibt; bestehende Gerichte bleiben unverändert |
| Rezept löschen | löscht Zutaten/Schritte; `meal_dishes.recipe_id` der Gerichte → `NULL`; Gerichte bleiben; unbekannt → 404 |

Der Server lädt nie externe URLs (ADR-0012). Die `servings` eines Gerichts gehen mit dem Rezeptbezug verloren, wenn das Rezept
gelöscht wird (die API liefert dann `servings: null`; die Mengen stehen bereits im Gericht).

Unit-Testfälle: `IngredientTest`, `RecipeRulesTest`, `RecipeServiceTest`, `MealPlanServiceTest`; Persistenz gegen echte SQLite
(`ExposedRecipeRepositoryTest`, `ExposedMealPlanRepositoryTest`, `MealsMigrationV5Test`); Integration/Contract `MealsRecipeApiTest`.

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

## App-Seite (Phase 4b)

Neue Pakete `domain/cooking` und `data/cooking` (Wochenstatus, Freigabe, Absage); Erweiterungen in `domain/plan` (Dish-Status,
`EditorRules`, Anwesenheit im Editor), `ui/plan` und `ui/dish`. `api/` und `backend/` bleiben unverändert (Vertrag 0.4.0 aus 4a).
Entscheidungen des Anwenders (2026-10-18): Wochenstatus als Karte oben im Essensplan; „Mein Kochtag“ für kochende Kinder;
Freigabe/Ablehnung im Gericht-Screen; bei `dayValid=false` können Eltern das Gericht **nur verschieben oder löschen**
(Verschieben gibt es nach der Server-Regel aus 4a frei – akzeptiert).

| Screen | Rolle | Inhalt |
|---|---|---|
| Essensplan | alle | + Badges „wartet auf Freigabe“ / „abgelehnt“ / „nach Frist“ / „⚠ Tag ungültig“ (auch offline aus dem Cache); „Zur Planwoche …“ in der Vorwoche; Karte **Wochenstatus** (Bestellfrist, offene Tage, Zustand je Koch; Eltern: Eintragen, Absagen…, Zurücknehmen, gesendetes Gericht öffnen); Karte **Mein Kochtag** (kochendes Kind: Frist, Zustand, Begründung, Einreichen/Ändern/Überarbeiten, Kochen absagen / Absage zurücknehmen) |
| Gericht-Screen | Eltern, Kinder | Modi `PARENT_EDIT` (planen/ändern), `PARENT_REVIEW` (Freigeben / Ablehnen… mit Begründung, darunter Ändern/Löschen), `PARENT_MOVE` (nur Tag wählbar, Name/Zutaten/Koch gesperrt, Verschieben oder Löschen), `CHILD` (Koch fest, nur eigene Anwesenheitstage, Zutaten Pflicht, kein Löschen) |

Regeln:

- **Karten nur ab Planwoche und online:** Der Wochenstatus wird nur für Wochen ab `currentPlanWeek` geladen (parallel zum Plan); bei
  gespeichertem Stand zeigt der Plan „Wochenstatus nur im Heim-WLAN.“ und keine Karten. Der Prefetch lädt keinen Status.
- **Editor-Modus** (`editorMode`): Kind → `CHILD`; Eltern + Kindergericht mit `dayValid=false` → `PARENT_MOVE` (hat Vorrang vor
  `SUBMITTED`); Eltern + Kindergericht `SUBMITTED` → `PARENT_REVIEW`; sonst `PARENT_EDIT` (auch abgelehnte Kindergerichte).
- **Wählbare Tage** (`allowedDates`): ohne belegte Tage; bei Kind als Koch nur dessen Anwesenheitstage. Beim Wechsel zu einem Kind
  fällt ein nicht mehr wählbarer Tag auf den ersten erlaubten.
- **Kind:** Zutaten sind Pflicht (Server-Reihenfolge Name → Zutaten); Fristhinweis „nach Frist“ ohne Sperre (ADR-0007); Kind ohne
  Kochwoche bekommt „Du kochst in dieser Woche nicht.“.
- **Begründung** (Absage und Ablehnung): 1–200 Zeichen, vor dem Request geprüft und getrimmt; ein Validierungsfehler bleibt im
  Dialog, andere Fehler (z. B. 409 „Das Gericht wartet nicht auf Freigabe“) schließen ihn und erscheinen auf dem Screen.
- **Stellvertretung:** „Eintragen“ im Wochenstatus öffnet den Editor mit vorgewähltem Kind; Absage/Rücknahme in den Karten.
- **Cache:** `StoredDish` bekommt Status-Felder mit Defaults; `CACHE_VERSION` bleibt 1, alte Dateien bleiben lesbar. Ein unbekannter
  Status macht die Woche „nicht gespeichert“.
- **Editor-Daten:** Woche, Mitglieder und Anwesenheit (Kinder, die kochen oder noch ein Gericht der Woche besitzen); schlägt eine
  der drei Anfragen fehl, schlägt das Laden fehl.

## App-Seite (Phase 5b)

Rezepte in `feature:meals`: Pakete `domain/recipe`, `data/recipe`, `ui/recipes` (Liste) und `ui/recipe` (Ansicht, Editor, Portionen-Stepper).
Der REWE-Import folgt in Phase 5c und nutzt den Zerleger unten wieder. `api/` und `backend/` bleiben unverändert (Vertrag 0.5.0).

| Screen | Rolle | Inhalt |
|---|---|---|
| Rezeptliste | alle | Einstieg „Rezepte“ auf der Startseite; Name + Portionen, Suche (clientseitig über die einmal geladene Liste), „Neues Rezept“ nur Eltern; im **Auswahlmodus** („Rezept wählen“) übernimmt Tippen das Rezept, kein Anlegen |
| Rezeptansicht | alle | Portionen, Zutaten, nummerierte Schritte, Quelle; Eltern: Bearbeiten, Löschen (mit Bestätigung) |
| Rezeptansicht aus dem Plan | alle | „Rezept ›“ in der Plan-Zeile (nur online) öffnet die Ansicht mit den **Mengen und Portionen des Gerichts**; keine Verwaltungsknöpfe |
| Rezept-Editor | nur Eltern | Name, Portionen 1–20, Zutaten (eine Zeile je Zutat), Schritte (eine Zeile je Schritt), Quelle; Kinder bekommen „Nur für Eltern.“ ohne Request |
| Gericht-Editor | Eltern, Kinder | + Abschnitt „Aus Rezept wählen“ (nicht im Modus `PARENT_MOVE`): Portionen-Stepper, „Anderes Rezept“, „Rezeptbezug entfernen“ |

Regeln:

- **Zerleger** (`parseIngredientLine`): Zahl am Anfang (Dezimalpunkt oder -komma, Punkt als Tausendertrenner bei Gruppen aus drei Ziffern nur bei g/mg/ml, sonst Dezimalpunkt,
  direkt am Buchstaben nur vor bekannter Einheit: „500g“), danach eine **bekannte Einheit** (Liste, ohne Groß-/Kleinschreibung, oder Wort mit „(n)“-Endung) und der
  Name. Alles andere, auch Mengen außerhalb 0,001–10000 oder eine Einheit ohne Namen dahinter, bleibt ganz Name (ADR-0012).
  Markenbereinigung gehört nicht dazu (Phase 5c).
- **Anzeigeformat** wie im Backend (`displayText`); Textfeld und Zerleger sind rundtrip-stabil. Ab 0.5.0 sendet die App Gerichte nur noch
  mit `structuredIngredients` (und `ingredients = []`) sowie `recipeId`/`servings`.
- **Portionen:** Vorschlag = alle Eltern + an dem Tag anwesende Kinder (Eltern-Anwesenheit kennt das System nicht), begrenzt auf 1–20.
  Auswahl übernimmt Name und umgerechnete Zutaten; **Portionen ändern rechnet immer neu aus dem Rezept** und überschreibt manuelle
  Zutaten-Änderungen (Hinweis im Editor). Mengen: Rezeptmenge · Ziel / Rezeptportionen, 3 Nachkommastellen; Zutaten ohne Menge bleiben so; Ergebnisse werden auf 0,001–10000 begrenzt, damit sie beim Zerlegen nicht verloren gehen.
- **Rezeptbezug** bleibt nach manuellen Textänderungen erhalten; „Rezeptbezug entfernen“ lässt Name und Zutaten stehen. Ein bestehendes Gericht
  behält `recipeId`/`servings` beim Ändern, auch wenn das Rezept nicht (mehr) ladbar ist (dann ohne Stepper).
- **Navigationsergebnis:** Die Rezeptliste im Auswahlmodus schreibt die Rezept-ID in den `SavedStateHandle` des Gericht-Editors; der Editor
  verbraucht sie einmal (`onRecipeConsumed`), sodass eine Rotation das Rezept nicht erneut übernimmt.
- **Nicht offline:** Rezepte werden nicht gecacht (PRD „Later“); beim gespeicherten Plan-Stand gibt es kein „Rezept ›“.
- **Löschen** nur in der Rezeptansicht; 404 gilt als gelöscht. Bestehende Gerichte bleiben unverändert (Server-Regel 5a).
- **Fehler:** 400/403/404/409 und offline über `ErrorText`; bei 409 (Name vergeben) und 400 gewinnt die Servermeldung.

Unit-Testfälle: `IngredientLinesTest`, `ServingsTest`, `ValidationTest` (recipe), `UseCasesTest` (recipe, plan), `RecipeViewModelsTest`,
`RecipeScreensTest`; Integration gegen MockWebServer: `RemoteRecipeRepositoryTest`, `RemoteMealPlanRepositoryTest`.
