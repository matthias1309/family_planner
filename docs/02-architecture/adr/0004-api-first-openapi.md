# ADR-0004: API-first mit OpenAPI

- **Status:** Akzeptiert
- **Datum:** 2026-10-11

## Kontext

Backend und App werden oft getrennt (und von verschiedenen Agenten) weiterentwickelt. Beide brauchen einen
verbindlichen, prüfbaren Vertrag.

## Entscheidung

- `api/openapi.yaml` ist die Quelle der Wahrheit für alle Endpunkte und Datenmodelle.
- Der Android-Client wird daraus generiert (OpenAPI Generator, Kotlin + kotlinx.serialization).
- Das Backend wird in der CI gegen den Vertrag geprüft (Contract-Tests).
- Einheitliches Antwort-Format gemäß `.claude/rules/ecc/common/patterns.md`.

## Alternativen

- **Code-first (Spec aus Backend generieren):** weniger Doppelpflege, aber App-Entwicklung hängt am Backend-Stand.

## Konsequenzen

- Jede API-Änderung beginnt mit einer Änderung an `api/openapi.yaml` (im selben PR wie die Implementierung).
- Breaking Changes sind im Diff sofort sichtbar.
