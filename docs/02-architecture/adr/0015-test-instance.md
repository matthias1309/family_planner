# ADR-0015: Testinstanz und Test-Build

- **Status:** Akzeptiert
- **Datum:** 2026-10-24

## Kontext

Neue Features (Aufgaben-Board, Punkte) sollen auf zwei Testhandys ausprobiert werden, ohne die Daten der Familie zu berühren.
Familie und Test würden sonst dasselbe Backend und dieselbe Datenbank teilen (ADR-0005, ADR-0006).

## Entscheidung

1. **Zweiter Container** `family-planner-test` im selben `docker-compose.yml` (Compose-Profil `test`), gleiches
   `stable`-Image, Port **8097**, eigene Daten `./data-test`, eigener Setup-Token in `.env.test` (nicht im Git).
   Ohne `COMPOSE_PROFILES=test` in `.env` bleibt alles wie bisher.
2. **Ein Update-Ablauf:** `update.sh` wartet auf alle laufenden Container des Projekts; der Rollback betrifft beide.
3. **Test-Build der App:** Schalter `fp.testInstance` (bzw. `FP_TEST_INSTANCE`) setzt `applicationIdSuffix ".test"`,
   Name „Familienplaner Test“ und die Standard-URL `https://planner.home.arpa:8097/`. Er läuft neben der Familien-App;
   das gepinnte Zertifikat gilt je Hostname und bleibt unverändert (ADR-0006).

## Alternativen

- **Testkind in der Familien-DB:** vermischt Test- und Familiendaten, Aufräumen fehleranfällig.
- **Eigenes Image-Tag für Tests:** eigene CI-Pipeline, mehr Pflege; Daten trennen genügt.
- **Zweiter Pi:** unverhältnismäßiger Aufwand.

## Konsequenzen

- Backend-Änderungen gehen weiterhin sofort auf beide Instanzen → API nur additiv ändern.
- Ein kaputtes Image trifft beide Instanzen; der Rollback in `update.sh` deckt beide ab.
- Backups der Testinstanz liegen getrennt unter `data-test/backups/`.
- Abhängigkeiten: ADR-0001 (Module), ADR-0005 (Deployment), ADR-0006 (Zugriff/Zertifikat), ADR-0009 (App-Aufbau).
