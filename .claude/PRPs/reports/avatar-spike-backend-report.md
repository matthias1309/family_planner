# Implementation Report: Avatar & Shop – Spike + Backend/API (GAME Phase 4a)

## Summary
Spike (Avataaars-Definition als gemeinsamer Vertrag, Renderer in `android/core/ui`) mit Ergebnis **Go**, ADR-0017 akzeptiert.
Backend: fester Katalog (157 Teile, 60 kostenpflichtig), Kauf mit atomarer `SHOP`-Abbuchung, Besitz je Kind, Avatar speichern/lesen,
fünf Endpunkte (Vertrag 0.10.0, additiv), Migration V13. Bildschirme folgen in 4b.

## Spike-Ergebnis (Task 2b)
Vier Testbilder unter `android/core/ui/build/avatar-spike/` (Robolectric Native Graphics, 200 dp), per Bildansicht geprüft:
- `free.png` (Hoodie, kurze Haare), `hat.png` (Bommelmütze, Hautton Braun), `glasses-beard.png` (große Mähne, Sonnenbrille, Prachtbart),
  `graphic-shirt.png` (Dutt, Herzaugen, Zunge, Motiv-Shirt mit Pizza).
- Gesicht, Haare/Mütze, Kleidung sitzen richtig; keine schwarzen Flächen; Motiv-Shirt zeigt die Grafik; optionale Slots fehlen sauber.
- Dauer je Avatar (SVG bauen, parsen, zeichnen, Screenshot): ca. 15 ms; der erste Avatar mit JSON-Laden und Warm-up ca. 750 ms.

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Large | Large |
| Confidence | – | hoch (Backend nach Mustern), Spike ohne Plan-B-Bedarf |
| Files Changed | ≈ 18 neu, ≈ 12 geändert | 29 neu, 17 geändert (+ Plan archiviert) |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | Avatar-Definition als Vertrag | Done | SHA-256 im README |
| 2 / 2b | Renderer + Spike | Done | Go |
| 3 | ADR-0017 | Done | Status Akzeptiert |
| 4 | OpenAPI 0.10.0 | Done | Generierte Enums: `AvatarSlot.HAIR_COLOR` usw. |
| 5 | Migration V13 | Done | gegen echte DB gestartet: V1–V13 erfolgreich |
| 6–10 | Domäne, Port, Tabellen, Repository, Service, Routen | Done | `LedgerWrites.kt` aus `ExposedRedemptionRepository` gezogen |
| 11 | Tests | Done | siehe unten |
| 12 | Doku | Done | Moduldesign, Traceability, PRD, Architektur-README |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | `ktlintCheck detekt` Backend und Android |
| Unit Tests | Pass | Backend `check`, Android `testDebugUnitTest` |
| Build | Pass | `:app:buildFatJar`, `assembleDebug`, `lintDebug` |
| Coverage | Pass | `koverVerify` (Backend), `koverVerifyDebug` (Android) |
| Integration | Pass | `ExposedAvatarRepositoryTest`, `AvatarApiTest`; Migration auf frischer DB |
| Edge Cases | Pass | Doppelkauf, Rollback, unbekannte Katalog-IDs, Rechte |

## Files Changed (Auswahl)

| File | Action |
|---|---|
| `api/avatar/avataaars.json`, `README.md` | CREATED |
| `api/openapi.yaml` | UPDATED (0.10.0) |
| `android/core/ui/.../avatar/*` (5 Dateien) | CREATED |
| `backend/gamification/.../avatar/*` (6 Dateien), `persistence/Avatar*`, `LedgerWrites.kt`, `web/Avatar*` | CREATED |
| `V13__gamification_avatar.sql` | CREATED |
| `docs/02-architecture/adr/0017-avatar-bausteine.md` | CREATED |

## Deviations from Plan
- `AvatarSlot` im Backend verwendet `HAIR_COLOR`-Schreibweise der Konstanten; `key` bleibt der API-Wert (`hairColor`).
- `purchase`/`validated` in kleinere Funktionen zerlegt (detekt `ThrowsCount`); Verhalten unverändert.
- Katalogdaten in eigene Datei `AvatarCatalogData.kt` ausgelagert (Lesbarkeit, Datei-Größe).
- Robolectric-Compose-Test braucht `src/test/resources/robolectric.properties` (`sdk=36`) in `core:ui`, wie die Feature-Module.
- Eine Testklasse je Look statt einer Schleife (ein `setContent` je Test erlaubt).
- `config/detekt/detekt.yml` (Android): `UnusedPrivateMember` ignoriert `@Preview`.
- Rechenbeispiel aus der Teststrategie: Guthaben 12, `eyes.hearts` à 10 → 2 (kein Test-Katalog), wie im Plan vorgesehen.

## Issues Encountered
- Rollback-Test lief zunächst grün durch, weil `ledger.append` mit Betrag 0 stillschweigend nichts schrieb → Betrag 1, Vorbedingung geprüft.
- OpenAPI-Pattern war zunächst doppelt escaped (`\\.`) → korrigiert.

## Tests Written

| Test File | Tests | Coverage |
|---|---|---|
| `AvatarCatalogTest` | 5 | feste ID-Liste (157), Konsistenz zur Definition, Defaults, Grenzen |
| `AvatarServiceTest` | 17 | Rechte, Validierung, Kauf, Besitz, Rangliste, Reparatur |
| `ExposedAvatarRepositoryTest` | 8 | Atomarität, Doppelkauf, Rollback, Cascade, `CHECK` |
| `AvatarApiTest` | 8 | Vertragsprüfung, Statuscodes, Konto/Rangliste |
| `AvatarSvgTest` (Android) | 7 | SVG-Aufbau, Optional, Fallback, Verschachtelung, Escaping |
| `AvatarImageSpikeTest` (Android) | 4 | Bitmaps nicht leer, Hautfarbe, PNG-Ausgabe |

## Nicht geprüft
- Manuelle curl-Szenarien aus dem Plan (Familie koppeln, Kauf-Ablauf) – durch `AvatarApiTest` abgedeckt, nicht von Hand gegen `:app:run` ausgeführt.
- Migration auf einer Kopie der Testinstanz-DB (nicht verfügbar in dieser Umgebung).
- Spike-PNG-Test unter CI-Native-Graphics (lokal grün).

## Next Steps
- [ ] `/code-review`, dann PR
- [ ] Phase 4b: App-Bildschirme (Editor, Shop, Avatar in Rangliste/Board/„Heute“, Kauf-Hinweis beim Sparziel, Namensnennung)
