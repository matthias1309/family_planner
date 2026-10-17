# ADR-0010: Backend-Feature-Module

- **Status:** Akzeptiert
- **Datum:** 2026-10-17

## Kontext

ADR-0001 legt fest, dass jedes Feature ein eigenes Gradle-Modul bekommt, das nur von `core` abhängt. Mit `meals` entsteht
das erste Feature-Modul. Offen sind die praktischen Konventionen: Verdrahtung, Pfade, Migrationen und geteilte Test-Fakes.
Flyway führt pro Datenbank **eine** Versionshistorie, die Versionsnummern sind also über Module hinweg global.

## Entscheidung

- Gradle-Modul `backend/<feature>`, Paket `de.familyplanner.<feature>`, Abhängigkeit nur `implementation(project(":core"))`.
- Eigenes Koin-Modul `<feature>Module(clock)`; `Database` und `core`-Repositories kommen aus `coreModule`.
- Routen als Extension `Route.<x>Routes(...)`, in `app/Application.kt` registriert. Pfade `/api/v1/<feature>/…`, OpenAPI-Tag `<feature>`.
- Migrationen liegen im Modul unter `src/main/resources/db/migration/` und werden über `classpath:db/migration` gefunden.
  Die Versionsnummer ist **global fortlaufend**, der Dateiname trägt den Modulbezug (`V2__meals_attendance.sql`).
  Kollidieren zwei Branches, wird die später gemergte Migration auf die nächste freie Nummer umbenannt — vor dem Deploy.
  Eine bereits deployte Version wird nie geändert; `outOfOrder` bleibt aus.
- Tabellen tragen das Präfix `<feature>_`. Fremdschlüssel auf `members(id)` sind erlaubt (das Modul hängt von `core` ab).
- Geteilte Test-Fakes (`MutableClock`, In-Memory-Repositories, Builder) liegen in `core` als Gradle-`testFixtures`;
  Feature-Module nutzen sie über `testImplementation(testFixtures(project(":core")))`.

## Alternativen

- **Migrationen zentral in `core`:** verletzt die Modulgrenze, `core` müsste jedes Feature kennen.
- **Eigener Flyway-Ort und eigene History-Tabelle je Modul:** mehr Komplexität, Reihenfolge von Fremdschlüsseln unklar.
- **Fakes in jedem Modul duplizieren:** driftet auseinander.

## Konsequenzen

- Neue Features folgen einem festen Muster; Agenten finden Code und Migrationen vorhersehbar.
- Parallel entwickelte Features brauchen Absprache bei der Migrationsnummer (siehe Regel oben).
- `core` bekommt einen zusätzlichen Gradle-Source-Set (`testFixtures`).
