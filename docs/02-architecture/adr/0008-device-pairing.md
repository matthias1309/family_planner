# ADR-0008: Geräte-Anmeldung per Kopplungscode

- **Status:** Akzeptiert
- **Datum:** 2026-10-11

## Kontext

Fünf Familienmitglieder, alle mit Android-Handy, Zugriff nur im Heimnetz (ADR-0006). Das Backend muss wissen, welches
Mitglied ein Gerät benutzt und ob es ein Elternteil ist. Kinder sollen sich keine Passwörter merken müssen; die
Anmeldung muss auch ohne Internetdienste (Firebase, OAuth) funktionieren.

## Entscheidung

- **Erst-Einrichtung:** Solange keine Mitglieder existieren, legt `POST /api/v1/setup` mit dem Setup-Token
  (`FP_SETUP_TOKEN`, ≥ 16 Zeichen, nur in der `.env` auf dem Pi) das erste Elternteil samt Gerät an. Ist kein
  Setup-Token konfiguriert, ist der Endpunkt deaktiviert (503).
- **Weitere Geräte:** Ein Elternteil erzeugt für ein Mitglied einen **Kopplungscode** (6 Ziffern, `SecureRandom`,
  15 Minuten gültig, einmalig einlösbar, ein neuer Code macht den alten ungültig). Das neue Handy löst ihn per
  `POST /api/v1/devices/pair` ein. Gespeichert wird nur der SHA-256-Hash. Rate-Limit: 5 Versuche pro Minute und IP.
- **Geräte-Token:** 32 Byte `SecureRandom`, Base64URL, in der DB nur als SHA-256-Hash. Übertragung als
  `Authorization: Bearer`. Das Token gilt, bis ein Elternteil das Gerät entfernt (Widerruf per `revoked_at`).
- **TLS** terminiert **in Ktor** (PKCS12-Keystore aus `certs/`). Ein zusätzlicher HTTP-Port ist nur an `127.0.0.1`
  gebunden und dient dem Docker-Healthcheck.

## Alternativen

- **PIN pro Mitglied:** 4–6 Ziffern sind bei jeder Anmeldung eintippbar, aber offline brute-forcebar und von Geschwistern
  leicht abzuschauen — verworfen.
- **Name + Passwort:** Passwörter für Kinder sind unpraktisch, erfordern Reset-Abläufe und Passwort-Hashing — verworfen.
- **Reverse-Proxy (Caddy) für TLS:** sauber getrennt, aber ein weiterer Container auf dem geteilten Pi (RAM, Betrieb) —
  verworfen, solange Ktor TLS selbst kann.

## Konsequenzen

- Keine Passwörter, keine externe Abhängigkeit; Kopplung ist ein einmaliger Vorgang pro Handy.
- Ein verlorenes Handy lässt sich sofort sperren (`DELETE /api/v1/devices/{id}`).
- Wer das Token eines Geräts besitzt, ist dieses Mitglied (kein Ablauf) — akzeptiert wegen LAN-only und Widerrufbarkeit.
- Das selbstsignierte Zertifikat muss in der App gepinnt werden (Phase 1b).
