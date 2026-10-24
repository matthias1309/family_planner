# ADR-0016: Domänenereignisse über eine Outbox in core

- **Status:** Akzeptiert
- **Datum:** 2026-10-24

## Kontext

Feature-Module kennen sich nicht (ADR-0001). `tasks` (Aufgabe abgenommen) und später `meals` (Kochen bestätigt) müssen
`gamification` informieren, ohne es zu kennen. Punkte dürfen dabei nicht verloren gehen — auch nicht bei einem Absturz zwischen
Statuswechsel und Gutschrift. `gamification` entsteht nach `tasks`; Abnahmen im Testbetrieb davor sollen trotzdem zählen.

## Entscheidung

1. **Outbox in `core`:** Tabelle `core_domain_events` (fortlaufende `seq`, `type`, JSON-`payload`, `occurred_at`) und
   `core_event_cursors` (ein Cursor je Abonnent, `last_seq`). Migration `V7` (ADR-0010).
2. **Schreiben in derselben Transaktion:** `JdbcTransaction.recordEvent(event, at)` wird im selben `dbQuery`-Block wie die
   fachliche Änderung aufgerufen. Rollback verwirft das Ereignis.
3. **Zustellung:** `DomainEventDispatcher` liefert je Abonnent ab dessen Cursor in `seq`-Reihenfolge, synchron nach dem Request
   (`EventNotifier.dispatchPending()`) und einmal beim Start. Der Cursor wird nach jedem erfolgreich verarbeiteten Ereignis
   gespeichert. Garantie: **mindestens einmal**, Abonnenten müssen idempotent sein.
4. **Typen:** Payload-Klassen liegen in `core.events`; der Codec bildet Typname (`task.accepted.v1`) explizit ab. Unbekannte
   Typen werden als `null` gelesen (kein Absturz nach Downgrade).
5. **Neue Abonnenten** starten bei `seq` 0 und holen alle früheren Ereignisse nach.

## Alternativen

- **Direkter Aufruf über ein Interface in `core` ohne Persistenz:** verliert Ereignisse vor `gamification` und bei Absturz.
- **In-Memory-Event-Bus (Koin):** dito.
- **Handler in derselben Transaktion:** koppelt Module an Persistenzdetails und lässt Abonnentenfehler den Request scheitern.
- **Message-Broker (MQTT läuft auf dem Pi):** zusätzlicher Betrieb ohne Nutzen.

## Konsequenzen

- Die Ereignistabelle wächst (Familienvolumen: wenige Tausend Zeilen pro Jahr) — kein Aufräumen vorgesehen.
- Eine Payload-Änderung bedeutet einen neuen Typnamen (`…v2`).
- Fehler eines Abonnenten werden geloggt und beim nächsten Request wiederholt; andere Abonnenten laufen weiter.
- Reihenfolge je Abonnent = `seq`. Der Mutex gilt prozessweit (ein Container je Datenbank).

Verweise: ADR-0001, ADR-0003, ADR-0010.
