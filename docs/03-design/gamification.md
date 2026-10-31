# Moduldesign: `gamification` – Punktekonto (Backend)

## Abgedeckte Anforderungen

REQ-GAME-010, -011, -012, -013, -014 (Phase 1a, Backend/API), REQ-GAME-020, -021 (Phase 2a, Backend/API, Abschnitt „Badges“), REQ-GAME-040, -041 (Server-Anteil), -042, -043 (Phase 3a, Backend/API, Abschnitt „Belohnungen“) und – Server-Anteil in `meals` – REQ-MEAL-034 — siehe
[gamification.prd.md](../01-requirements/gamification.prd.md). Die App-Seite ist in Phase 1b (Punkte) und 2b (Badges) umgesetzt. Konventionen für Feature-Module:
[ADR-0010](../02-architecture/adr/0010-backend-feature-modules.md); Ereignisse: [ADR-0016](../02-architecture/adr/0016-domain-events-outbox.md).

Die PRD-Notiz „Gutschrift in derselben Transaktion“ ist durch ADR-0016 ersetzt: Das Ereignis entsteht atomar mit dem Statuswechsel
(`tasks`/`meals`), die Gutschrift erfolgt danach mindestens einmal und ist über den `source_key` idempotent.

## Domänenmodell & Regeln

| Begriff | Definition |
|---|---|
| `LedgerEntry` | Unveränderliche Buchung eines Kindes: `kind`, `amount` (≠ 0), `title`, `note?`, `sourceKey?`, `createdBy?`, `bookedAt`. Korrekturen sind Gegenbuchungen |
| `EntryKind` | `TASK`, `COOKING`, `MANUAL` zählen für **Gesammelt**; `SHOP`, `REWARD`, `REWARD_REFUND` nur fürs Guthaben (Phase 1 bucht nur die ersten drei, alle sechs stehen schon im SQL-`CHECK` und im OpenAPI-Enum; `REWARD` und `REWARD_REFUND` werden ab Phase 3a gebucht) |
| Gesammelt / Guthaben | je Kind per Summe aus dem Journal berechnet (`totalsFrom`), nichts wird gespeichert; manuelle Abzüge dürfen ins Minus |
| Idempotenz | `source_key` UNIQUE: `task:<cardId>`, `cooking:<dishId>`; doppelte Zustellung → `INSERT OR IGNORE`; manuelle Buchungen haben `NULL` |
| Abonnent | `gamification.points` am `DomainEventDispatcher` (Name nie ändern), eigene Transaktion je Gutschrift; Start bei `seq` 0 → alte Abnahmen werden beim ersten Start nachgebucht |
| `bookedAt` | Zeitpunkt aus dem Ereignis (`acceptedAt`/`confirmedAt`), nicht „jetzt“ |
| Kochpunktwert | Einstellung `game_settings.cooking_points`, Standard 3, Bereich 1–20; gilt für Ereignisse, die ab der Änderung verarbeitet werden |
| Ungültige Ereignisse | Mitglied unbekannt oder kein Kind, Betrag 0 → überspringen mit `warn`, nie werfen (sonst bliebe der Abonnent hängen) |
| Rangliste | alle Kinder (auch mit 0), nach Gesammelt absteigend, Gleichstand gleicher Rang (1, 1, 3), dann nach Name; ohne Guthaben |
| Manuelle Buchung | nur Eltern, nur auf Kinder, Betrag ±1–1000, Begründung 1–200 Zeichen (wird `title`) |

### Sichtbarkeit

Rangliste und Einstellungen lesen alle. Konto (`Gesammelt`, `Guthaben`, Verlauf): ein Kind nur das eigene (`403`), Eltern alle Kinder;
Eltern-Mitglied als Ziel → `400`, unbekannt → `404` (erst Existenz, dann Recht). Buchen und Einstellungen ändern nur Eltern.

## Schnittstellen

Endpunkte siehe `api/openapi.yaml` (Tag `gamification`):

- `GET /api/v1/gamification/leaderboard` → `Leaderboard.items` (`memberId`, `name`, `rank`, `collected`)
- `GET /api/v1/gamification/members/{memberId}/account?limit=` → `PointsAccount` (`collected`, `balance`, `items` neueste zuerst; `limit` 1–100, Standard 50)
- `POST /api/v1/gamification/members/{memberId}/entries` (Eltern) `{amount, reason}` → `201` mit `LedgerEntry`
- `GET|PUT /api/v1/gamification/settings` (`PUT` nur Eltern) `{cookingPoints}`

Das Feld heißt `items` statt `entries`, weil der OpenAPI-Generator `entries` zu `propertyEntries` umbenennt.

## DB-Tabellen

`game_ledger` (`seq` AUTOINCREMENT, `id`, `member_id`, `kind`, `amount`, `title`, `note`, `source_key` UNIQUE, `created_by`, `booked_at`) und
`game_settings` (eine Zeile, `id = 1`) — Migration `V10__gamification.sql` (`CHECK` auf Art, Betrag ≠ 0 und Kochpunkte 1–20).

## Eingabegrenzen

Betrag ±1–1000, Begründung 1–200, Kochpunkte 1–20, Verlauf-`limit` 1–100.

## Unit-Testfälle

`PointsEventHandlerTest` (Gutschrift Aufgabe/Kochen, doppeltes Ereignis, geänderter Kochpunktwert, ungültiges Mitglied, unbekannter Typ),
`AccountServiceTest` (Konto, Sichtbarkeit, Buchen, Grenzen), `LeaderboardTest` (Rang bei Gleichstand, Kind ohne Buchung),
`GameSettingsServiceTest`. Integration: `ExposedLedgerRepositoryTest` (Idempotenz, Summen, Reihenfolge, Migrations-`CHECK`),
`PointsSubscriptionTest` (Dispatcher + Handler + echte DB, Nachholen, verlorener Cursor), API: `GamificationApiTest`
(Vertragsprüfung gegen `openapi.yaml`).

## Badges (Phase 2a)

**Katalog** (`badges/Badge.kt`, Enum, Reihenfolge = Anzeige): `id` ist der DB-Schlüssel und darf **nie** geändert werden (Test mit fester ID-Liste).
Jede Regel lautet „Messwert ≥ Ziel“, Fortschritt = `min(Messwert, Ziel)`.

| `id` | Titel | Bedingung | Geheim |
|---|---|---|---|
| `first-task` / `tasks-10` / `tasks-50` | Erste Aufgabe / Fleißig / Haushaltsprofi | 1 / 10 / 50 Aufgaben | – |
| `first-cooking` / `cooking-10` / `cooking-25` | Erstes Gericht / Küchenchef / Sterneköchin/-koch | 1 / 10 / 25 Gerichte | – |
| `points-100` / `points-500` | Hunderter / Punktesammler | 100 / 500 Punkte gesammelt | – |
| `streak-7` | Dranbleiber | 7 Tage in Folge eine Aufgabe | – |
| `streak-30` | Unaufhaltsam | 30 Tage in Folge eine Aufgabe | ja |
| `all-rounder` | Allrounder | an einem Tag Aufgabe und Kochen | ja |
| `points-1000` | Legende | 1000 Punkte gesammelt | ja |

**Statistik:** `badgeStatsFrom(entries)` rechnet je Kind aus dem ganzen Journal: Anzahl `TASK`-/`COOKING`-Buchungen (Betrag > 0), Gesammelt,
längste Tagesserie mit Aufgabe, Tage mit Aufgabe und Kochen. Kalendertage in `Europe/Berlin` (`BADGE_ZONE`); bei Aufgaben zählt der Abnahmetag.

**Vergabe:** Tabelle `game_badges (member_id, badge_id, awarded_at)`, PK `(member_id, badge_id)`, `INSERT OR IGNORE` → höchstens einmal, nie
entzogen (auch nicht nach negativer Korrektur). `BadgeService.evaluate` läuft nach jeder Gutschrift (auch bei erneuter Zustellung), nach
manueller Buchung und beim Start für alle Kinder (`evaluateAll`, holt Bestandsdaten und neue Katalogeinträge nach). `awardedAt` = Zeitpunkt
der Auswertung. Schlägt `evaluate` im manuellen Pfad fehl, ist die Buchung gespeichert und der Request antwortet 500; die nächste Buchung bzw.
der Neustart holt das Badge nach.

**Sichtbarkeit:** `GET /api/v1/gamification/members/{memberId}/badges` → `BadgeOverview`. Eigene Ansicht und Eltern: alle Badges mit
Fortschritt; geheime, nicht erhaltene Badges sind maskiert (`title`/`description`/`emoji`/`progress` = `null`, `hint` gesetzt). Geschwisterkind:
nur erhaltene Badges, kein Fortschritt; `totalCount` ist immer der ganze Katalog. Eltern-Mitglied → `400`, unbekannt → `404`.

**Tests:** `BadgeStatsTest`, `BadgeServiceTest`, `PointsEventHandlerTest`/`AccountServiceTest` (`REQ-GAME-020 …`), `ExposedBadgeRepositoryTest`,
`ExposedLedgerRepositoryTest` (`allEntriesOf`), `PointsSubscriptionTest` (Dispatcher + echte DB), `GamificationApiTest` (`REQ-GAME-020/021 …`).

## Belohnungen (Phase 3a)

Paket `rewards`, alles im Modul `gamification`. Die App-Bildschirme folgen in Phase 3b.

**Begriffe:** *Belohnung* (`Reward`, Katalog der Eltern), *Einlösung* (`Redemption`, Momentaufnahme von Titel/Symbol/Preis), *Sparziel*
(`SavingsGoal`, höchstens eins je Kind).

**Zustände einer Einlösung:** `PENDING → FULFILLED` (Eltern markieren erfüllt) oder `PENDING → REJECTED` (Eltern lehnen mit Begründung ab).
Andere Übergänge gibt es nicht (`409`). Das Kind kann eine Einlösung nicht zurückziehen.

**Atomarität:** Jede Einlösung und jede Entscheidung läuft in **einer** `dbQuery`-Transaktion (Pool 1 + `SERIALIZABLE` serialisiert sie):
- Einlösen: Belohnung aktiv? → Guthaben ≥ Preis? → `game_redemptions` (`PENDING`) + Buchung `REWARD` (−Preis, `source_key = reward:<id>`)
  → Sparziel löschen, wenn es diese Belohnung war. Verwendet `insert` (nicht `insertIgnore`), damit Fehler zurückrollen.
- Ablehnen: bedingtes `UPDATE … WHERE status = 'PENDING'` + Buchung `REWARD_REFUND` (+Preis, `source_key = reward-refund:<id>`, `note` = Begründung).
  Die Rückbuchung geschieht dadurch genau einmal.
- Ab-/Rückbuchungen zählen nicht für „Gesammelt“, lösen keine Badge-Prüfung aus und ändern die Rangliste nicht.

**Rechte:**

| Aktion | Kind (selbst) | Geschwister | Eltern |
|---|---|---|---|
| Belohnungen lesen (`includeArchived`) | ja | ja | ja |
| Belohnung anlegen / ändern / deaktivieren | 403 | 403 | ja |
| Einlösen | ja | – | 403 |
| Einlösungen auflisten | eigene | – | alle |
| Erfüllt / Ablehnen | 403 | 403 | ja |
| Sparziel lesen | ja | 403 | ja |
| Sparziel setzen / entfernen | ja | 403 | 403 |

**Endpunkte:** `GET/POST /gamification/rewards`, `PUT/DELETE /gamification/rewards/{rewardId}`, `POST …/rewards/{rewardId}/redemptions`,
`GET /gamification/redemptions?status=&limit=`, `POST …/redemptions/{id}/fulfillment|rejection`,
`GET/PUT/DELETE /gamification/members/{memberId}/savings-goal`. Fehlerreihenfolge: Rolle (403), Eingabe (400), Existenz (404), Zustand (409).

**Regeln:**
- Deaktivieren setzt `archived_at`; kein Reaktivieren, keine Änderung danach (`409`). Laufende Einlösungen bleiben entscheidbar.
- Zu wenig Guthaben oder deaktivierte Belohnung beim Einlösen → `409`.
- Sparziel-Fortschritt nutzt das **Guthaben**: `progress.current = clamp(balance, 0, price)`, `redeemable = aktiv && balance ≥ price`.
  Zeigt das Ziel auf eine deaktivierte Belohnung, bleibt es bestehen (`reward.archived = true`, `redeemable = false`).
- Eltern-Hinweis bei neuer Einlösung: kein Server-Push; die App fragt `GET /redemptions?status=PENDING` beim Abgleich ab (ADR-0013).

**Tabellen (V12):** `game_rewards`, `game_redemptions`, `game_savings_goals`.

**Eingabegrenzen:** Titel 1–40, Symbol ≤ 16, Preis 1–10 000, Ablehnungsgrund 1–200, `limit` 1–100 (Standard 50).

**Tests:** `RewardTest`, `RewardServiceTest`, `RedemptionServiceTest` (Fakes), `ExposedRewardRepositoryTest`, `ExposedRedemptionRepositoryTest`
(Atomarität, Rückbuchung genau einmal, `CHECK`, Cascade), `RewardsApiTest` (Vertragsprüfung gegen `openapi.yaml`).

## App-Seite (Phase 1b)

Modul `android/feature/gamification` (`domain/`, `data/`, `ui/`, kennt `meals`/`tasks`/`auth` nicht; nur `app` verbindet). `api/` und `backend/`
bleiben unverändert (Vertrag 0.7.0).

| Bildschirm | Inhalt |
|---|---|
| „Ich“ (Kind, `MeHomeRoute`) | Kacheln Gesammelt / Guthaben, „Platz n von m“, Rangliste (eigene Zeile fett), Verlauf (letzte 50, `HISTORY_LIMIT`) |
| „Familie“ → Abschnitt „Punkte“ (Eltern) | Rangliste (antippbar), „Kochpunkte: n · Ändern“ (Stepper 1–20, gilt für künftige Bestätigungen) |
| Punktekonto (Eltern, `ChildAccountRoute`, ohne Leiste) | Stand, Verlauf, „Punkte buchen“ (Gutschreiben/Abziehen, Betrag 1–1000, Begründung 1–200 mit Zähler) |

Der Abschnitt „Punkte“ wird über den Slot `familyContent` von `mainGraph`/`FamilyScreen` eingehängt (`feature:auth` kennt das Modul nicht).

- **Sichtbarkeit:** Kind lädt nur das eigene Konto (`LoadPointsUseCase`: Konto + Rangliste parallel); Eltern nur die Rangliste, das Konto eines Kindes über `LoadChildAccountUseCase` (ohne lokalen Stand).
- **Lokaler Stand** (ADR-0011): Datei `game_points.json`, je Mitglied (`memberId`), Version 2 (seit 2b mit Badges; eine Datei alter Version gilt als leer), Zeitpunkte als Epoch-Millis. Nur bei `Network`/`Certificate`/`Server` als Rückfall; `Unauthorized` leert den Stand; ein halber Ladeerfolg wird nicht gespeichert. Schreibaktionen (Buchen, Kochpunkte ändern) nur online.
- **Laden:** nur über `ON_RESUME`/Pull-to-Refresh (kein Laden im `init`, sonst doppelt); ein neuer Ladevorgang bricht den alten ab.
- **Rangliste:** Reihenfolge und Rang wie vom Server (Gleichstand 1, 1, 3); Avatar-Platzhalter = Anfangsbuchstabe (Avatar folgt in Phase 4).
- **Grenzen** (`PointsLimits`): Betrag ≠ 0, |Betrag| ≤ 1000; Begründung 1–200 nach `trim`; Kochpunkte 1–20.

Unit-Tests: `ValidationTest`, `UseCasesTest`, `ViewModelsTest` (Me/Familie/Punktekonto), `ScreensTest` (Robolectric),
`RemotePointsRepositoryTest` (MockWebServer), `DataStorePointsCacheTest`, `GamificationModuleTest`; Navigation: `AppNavHostTest` (`app`).

## App-Seite Badges (Phase 2b)

Gleiches Modul, kein Backend-/Vertragswechsel (OpenAPI 0.8.0, `GET /gamification/members/{memberId}/badges`). App 0.11.0.

| Bildschirm | Inhalt |
|---|---|
| „Ich“ (Kind) | Abschnitt „Badges n/m“ zwischen Rangliste und Verlauf: Zeile je Badge (Emoji, Titel, Bedingung), erhalten → „erhalten TT.MM.“, offen → Fortschrittsbalken + „7/10“ (abgeblendet), geheim → „???“ + Hinweis. Rangliste ist online antippbar |
| „Badges von <Name>“ (`MemberBadgesRoute`, ohne Leiste) | „Badges n/m“ + Liste; für Geschwister nur erhaltene (Server-Sicht) |
| Punktekonto (Eltern) | Abschnitt „Badges n/m“ mit Fortschritt vor dem Verlauf |

- **Neu-Hinweis (REQ-GAME-020):** Dialog „Neues Badge!“ auf „Ich“. „Neu“ = in der frischen Übersicht erhalten, im zuletzt gespeicherten
  lokalen Stand nicht (`BadgeOverview.newlyEarned`). Ohne gespeicherte Badges (Erststart, Update, Neuinstallation) gilt die Basislinie: kein Dialog.
  Keine Systembenachrichtigung.
- **Laden:** Kind lädt Konto, Rangliste und eigene Badges parallel (`LoadPointsUseCase`); schlägt eine Anfrage fehl, wird nichts gespeichert.
  Geschwister und Eltern: `LoadBadgesUseCase` ohne lokalen Stand. Das Punktekonto der Eltern zeigt das Konto auch, wenn nur die Badges fehlschlagen.
- **Lokaler Stand:** Badges im Snapshot, Cache-Version 2. Folge: Nach dem Update gibt es offline keinen Rückfall, bis einmal online geladen wurde.
  Geschwister- und Eltern-Ansicht nur online.
- **Mapping:** erhaltenes Badge ohne lesbares Datum → ganze Übersicht ist ein Serverfehler (kein halbes Ergebnis).

Tests: `BadgesTest`, `UseCasesTest`, `RemotePointsRepositoryTest`, `DataStorePointsCacheTest`, `ViewModelsTest`, `ScreensTest`, `GamificationModuleTest` (`REQ-GAME-020/021 …`).
