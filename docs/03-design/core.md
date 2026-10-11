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
