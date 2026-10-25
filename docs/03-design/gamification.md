# Moduldesign: `gamification` – Punktekonto (Backend)

## Abgedeckte Anforderungen

REQ-GAME-010, -011, -012, -013, -014 (Phase 1a, Backend/API) und – Server-Anteil in `meals` – REQ-MEAL-034 — siehe
[gamification.prd.md](../01-requirements/gamification.prd.md). Die App-Seite folgt in Phase 1b. Konventionen für Feature-Module:
[ADR-0010](../02-architecture/adr/0010-backend-feature-modules.md); Ereignisse: [ADR-0016](../02-architecture/adr/0016-domain-events-outbox.md).

Die PRD-Notiz „Gutschrift in derselben Transaktion“ ist durch ADR-0016 ersetzt: Das Ereignis entsteht atomar mit dem Statuswechsel
(`tasks`/`meals`), die Gutschrift erfolgt danach mindestens einmal und ist über den `source_key` idempotent.

## Domänenmodell & Regeln

| Begriff | Definition |
|---|---|
| `LedgerEntry` | Unveränderliche Buchung eines Kindes: `kind`, `amount` (≠ 0), `title`, `note?`, `sourceKey?`, `createdBy?`, `bookedAt`. Korrekturen sind Gegenbuchungen |
| `EntryKind` | `TASK`, `COOKING`, `MANUAL` zählen für **Gesammelt**; `SHOP`, `REWARD`, `REWARD_REFUND` nur fürs Guthaben (Phase 1 bucht nur die ersten drei, alle sechs stehen schon im SQL-`CHECK` und im OpenAPI-Enum) |
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
