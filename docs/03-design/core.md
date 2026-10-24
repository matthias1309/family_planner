# Moduldesign: `core` (AUTH)

## Abgedeckte Anforderungen

REQ-AUTH-001, REQ-AUTH-002, REQ-AUTH-003, REQ-OPS-001 (Teil Health/Backup) — siehe [auth.prd.md](../01-requirements/auth.prd.md).
Entscheidung zur Anmeldung: [ADR-0008](../02-architecture/adr/0008-device-pairing.md).

## Domänenmodell

| Entität | Felder | Hinweise |
|---|---|---|
| `Member` | `id` (UUID), `name` (1–40, eindeutig case-insensitiv), `role` (PARENT/CHILD), `createdAt` | unveränderlich |
| `Device` | `id`, `memberId`, `name` (1–60), `createdAt`, `lastSeenAt?`, `revokedAt?` | Token/Hash verlässt das Repository nicht |
| `PairingCode` | `codeHash`, `memberId`, `expiresAt`, `usedAt?` | nur Hash gespeichert, 6 Ziffern, TTL 15 min |
| `DevicePrincipal` | `deviceId`, `memberId`, `role` | Ergebnis der Bearer-Authentifizierung |

Services: `MemberService`, `SetupService`, `PairingService`, `DeviceService`. Fehler ausschließlich als `AppException`
(`StatusPages` in `app` übersetzt in HTTP + Envelope `{success, data, error}`).

## Schnittstellen

Endpunkte siehe `api/openapi.yaml`: `/health`, `/api/v1/setup`, `/api/v1/devices/pair`, `/api/v1/me`,
`/api/v1/members`, `/api/v1/members/{memberId}/pairing-codes`, `/api/v1/devices`, `/api/v1/devices/{deviceId}`.
Rollenprüfung explizit je Route über `requireParent()`; spätere Module nutzen dasselbe.

## DB-Tabellen

`members`, `devices`, `pairing_codes` — Migration `V1__auth.sql` (Zeitstempel als Epoch-Millis, IDs als UUID-Text).

## Unit-Testfälle

Siehe Testklassen in `backend/core/src/test` (Namen beginnen mit der REQ-ID): Mitglieder (leer/zu lang/doppelt),
Setup (Token falsch/deaktiviert/bereits erfolgt), Kopplungscode (Format, TTL, einmalig, ersetzt, unbekanntes Mitglied),
Geräte-Authentifizierung (Widerruf, `lastSeenAt`-Drosselung), Secrets nur als Hash, `DatabaseFactory` (Pragmas,
Backup-Rotation).

## App-Seite (Phase 1b)

Entscheidungen: [ADR-0009](../02-architecture/adr/0009-android-app-architecture.md). Code: `android/` (`core:data`, `feature:auth`).

**Session-Lebenszyklus:** `SessionStore` hält `Loading` → `SignedOut` | `SignedIn(session)`. Koppeln/Einrichten speichert die
Session (verschlüsselt, Tink + Keystore); die Navigation folgt dem Zustand. Antwortet der Server auf einen Request *mit*
Token mit 401 (z. B. Gerät entfernt), löscht der `UnauthorizedInterceptor` die Session und meldet `SessionEvent.SignedOut`;
die Startseite zeigt dann „Dieses Gerät wurde abgemeldet. Bitte neu koppeln.“. 401 bei `/setup` und `/devices/pair` (ohne
Token) meldet nicht ab. Ist der Keystore-Schlüssel nicht mehr lesbar, gilt das Gerät als abgemeldet.

**Fehlerabbildung** (`ApiResult` / `AppError`, je Fehler genau eine deutsche Meldung):

| Quelle | `AppError` |
|---|---|
| `IOException` | `Network` („Im Heim-WLAN?“) |
| `SSLException` | `Certificate` |
| `ErrorCode` `VALIDATION_FAILED` / `CONFLICT` | `Validation` / `Conflict` (Servermeldung) |
| `UNAUTHORIZED` / `INVALID_PAIRING_CODE` | `Unauthorized` / `InvalidPairingCode` |
| `FORBIDDEN` / `NOT_FOUND` / `RATE_LIMITED` / `SETUP_DISABLED` | gleichnamig |
| `INTERNAL`, unlesbare Antwort, fehlendes `data` | `Server` |

**Screens und Rollen:** Welcome (Koppeln / Einrichten) → Setup (Setup-Token, Name, Gerätename; 409 → Hinweis) und Pair
(6-stelliger Code, Gerätename) → Home („Hallo <Name>“, Rolle; `/me` beim Start, offline bleibt die Session erhalten) →
Familie (nur Eltern: Mitglieder anlegen, Kopplungscode erzeugen, Geräte entfernen mit Bestätigung). Kinder sehen keine
Verwaltung; ein 403 wird als „Nur für Eltern.“ angezeigt.

## App-Navigation (TASK Phase 1)

Entscheidungen: [ADR-0014](../02-architecture/adr/0014-app-main-navigation.md), [ADR-0015](../02-architecture/adr/0015-test-instance.md).
Code: `android/app` (`TopLevelDestination`, `AppNavigationBar`, `AppNavHost`), `feature:auth` (`HomeScreen`), `feature:meals`
(`MealsHomeScreen`, `ui/today`).

- **Hauptbereiche je Rolle** (`topLevelDestinations`): Kind *Heute · Essen · Aufgaben · Ich*, Eltern *Heute · Essen · Aufgaben ·
  Familie*. „Aufgaben“ und „Ich“ sind Platzhalter („Kommt bald“).
- **Sichtbarkeit:** Die Leiste erscheint nur auf Hauptseiten (`topLevelOf != null`); Unterseiten behalten „Zurück“.
- **Zustand:** Wechsel mit `saveState`/`restoreState`/`launchSingleTop`; System-Zurück → „Heute“.
- **Slot `todayContent`:** `mainGraph` reicht Inhalte anderer Module an „Heute“ durch; `app` setzt die Karte
  `TodayMealRoute` ein (Plan der laufenden Woche über `LoadWeekPlanUseCase`, offline aus dem Plan-Cache).
- **Deep-Links** (Benachrichtigung, geteiltes Rezept) wechseln zuerst zu „Essen“ (`openMealsAndThen`), `openWeekPlan` nutzt
  `launchSingleTop`, damit kein doppelter Essensplan entsteht.
- **Test-Build:** `FP_TEST_INSTANCE=true` → `applicationId …android.test`, Name „Familienplaner Test“, Server `:8097`.

**Unit-Testfälle:** `MainDestinationsTest` (Ziele je Rolle, keine Leiste ohne Hauptbereich), `TodayMealTest` (Gericht des
Tages, Sonntag → Woche ab Montag, abgelehnt/eingereicht/kein Gericht, Cache offline, nicht erreichbar), `ScreensTest`
(auth: Begrüßung/Slot/Offline, Familie ohne Zurück), `AppNavHostTest` (Leiste je Rolle, Essen-Übersicht, Unterseite ohne
Leiste, Platzhalter, Deep-Links).

