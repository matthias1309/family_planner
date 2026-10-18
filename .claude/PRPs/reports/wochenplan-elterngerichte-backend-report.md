# Implementation Report: Wochenplan & Elterngerichte – Backend (Phase 3a)

## Summary
`backend/meals` hat jetzt einen Wochenplan: höchstens ein Gericht je Tag, mit Koch/Köchin (in Phase 3 nur Eltern), Name und
Freitext-Zutaten. Eltern legen Gerichte an, ändern, verschieben und löschen sie (REQ-MEAL-032); alle Mitglieder lesen den
Plan jeder Woche als sieben Tage, offene Tage mit `dish: null` (REQ-MEAL-001). Dazu gehören Migration `V3__meals_plan.sql`,
Domäne, Repository (Exposed + Fake), Service, vier Endpunkte und der OpenAPI-Vertrag (0.3.0). REQ-MEAL-002 (Offline) ist
App-Seite und folgt in Phase 3b.

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Medium | Medium |
| Confidence | 8/10 | Umsetzung in einem Durchgang, nur ktlint-Formatierung nachgezogen |
| Files Changed | ~18 | 25 (14 neu, 11 geändert; inkl. Plan und Report) |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | Moduldesign erweitern | [done] Complete | |
| 2 | OpenAPI-Vertrag | [done] Complete | Android-Client kompiliert |
| 3 | Migration V3 | [done] Complete | |
| 4 | Domäne (`Plan.kt`) | [done] Complete | |
| 5 | Repository | [done] Complete | |
| 6 | Service | [done] Complete | Tests zuerst geschrieben (RED: Klasse fehlte), dann grün |
| 7 | Routen, Mapper, Koin, App | [done] Complete | `weekParameter` nach `RequestParsing.kt`, neuer Helfer `parseUuid` |
| 8 | Integrations-/Contract-Tests | [done] Complete | `memberIdOf` nach `ApiTestSupport.kt` verschoben |
| 9 | Qualität & Doku | [done] Complete | README, Traceability, PRD, Design |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | [done] Pass | `./gradlew check` (ktlint, detekt) nach `ktlintFormat` |
| Unit Tests | [done] Pass | `meals`: 69 Tests (30 neu, inkl. Review-Nachtrag) |
| Build | [done] Pass | `:app:buildFatJar`; Fat-JAR enthält V1–V3 |
| Integration | [done] Pass | `app`: 56 Tests (11 neu in `MealsPlanApiTest`), alle mit Contract-Validierung; `koverVerify` grün |
| Edge Cases | [done] Pass | Grenzen 60/61, 80/81, 30/31 (+ Leerzeilen), Konflikte, 403/404/400 |
| Start auf leerer DB | [done] Pass | „Successfully applied 3 migrations … now at version v3“; Plan-Endpunkt ohne Token → 401 |

Gesamt nach `test --rerun-tasks`: core 34, meals 68, app 56 Tests, 0 Fehler. Docker-Image wurde lokal **nicht** gebaut
(übernimmt die CI).

## Files Changed

| File | Action | Lines |
|---|---|---|
| `api/openapi.yaml` | UPDATED | +159 / -1 |
| `docs/03-design/meals.md` | UPDATED | +28 / -2 |
| `docs/04-verification/traceability.md` | UPDATED | +3 / -3 |
| `docs/01-requirements/meals.prd.md` | UPDATED | Phase-3-Zeile |
| `backend/README.md` | UPDATED | +1 / -1 |
| `backend/meals/src/main/resources/db/migration/V3__meals_plan.sql` | CREATED | +19 |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/Plan.kt` | CREATED | +41 |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanRepository.kt` | CREATED | +26 |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanService.kt` | CREATED | +98 |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/PlanTables.kt` | CREATED | +27 |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedMealPlanRepository.kt` | CREATED | +119 |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/PlanApiMappers.kt` | CREATED | +21 |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/PlanRoutes.kt` | CREATED | +53 |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/RequestParsing.kt` | UPDATED | +17 |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/AttendanceRoutes.kt` | UPDATED | -9 |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/MealsModule.kt` | UPDATED | +5 |
| `backend/app/src/main/kotlin/de/familyplanner/app/Application.kt` | UPDATED | +3 |
| `backend/app/src/test/kotlin/de/familyplanner/app/ApiTestSupport.kt` | UPDATED | +5 |
| `backend/app/src/test/kotlin/de/familyplanner/app/MealsAttendanceApiTest.kt` | UPDATED | +1 / -5 |
| `backend/app/src/test/kotlin/de/familyplanner/app/MealsPlanApiTest.kt` | CREATED | +242 |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/fakes/InMemoryMealPlanRepository.kt` | CREATED | +38 |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/plan/PlanTest.kt` | CREATED | +47 |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/plan/MealPlanServiceTest.kt` | CREATED | +223 |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/persistence/ExposedMealPlanRepositoryTest.kt` | CREATED | +170 |

## Deviations from Plan
- `parseUuid` (im Plan „bevorzugt“) umgesetzt: ein ungültiges `cookId` im Body ergibt 400 statt 404.
- Der Service prüft erst Name und Zutaten, dann Koch/Köchin (Plan: Validierung vor jedem Repository-Zugriff außer der
  Mitgliedssuche — eingehalten).
- Validierungs- und 404-Fälle der API-Tests sind in zwei Tests mit Statuslisten gebündelt statt je einem Test pro Fall
  (gleiche Abdeckung, weniger Setup).

## Issues Encountered
- ktlint bemängelte die Formatierung in zwei Testdateien → `ktlintFormat`.
- Der erste `buildFatJar`-Lauf erzeugte kein neues `app-all.jar` (der Start lief mit dem JAR vom 17.10. und wendete nur
  V1–V2 an). Ein zweiter Lauf baute es neu; danach wurden V1–V3 angewendet. Die Ursache wurde nicht weiter untersucht —
  vor einem lokalen Image-Build den Zeitstempel von `app/build/libs/app-all.jar` prüfen.

## Tests Written

| Test File | Tests | Coverage |
|---|---|---|
| `meals/.../plan/PlanTest.kt` | 3 | `weekPlanOf`: 7 Tage, Gericht am Tag, Gerichte außerhalb der Woche |
| `meals/.../plan/MealPlanServiceTest.kt` | 18 | Anlegen, Trimmen, Grenzen, Koch-Regel, Konflikte, Ändern/Verschieben, Löschen |
| `meals/.../persistence/ExposedMealPlanRepositoryTest.kt` | 8 | echte SQLite: Reihenfolge, DAY_TAKEN/NOT_FOUND, Zutaten ersetzen/löschen, Bereich |
| `app/.../MealsPlanApiTest.kt` | 11 | Endpunkte + Contract, Rollen, 400/404/409 |

## Next Steps
- [ ] Code review via `/ecc:code-review`
- [ ] Commit + PR via `/ecc:prp-pr`
- [ ] Nach Merge: Plan für Phase 3b (App inkl. Offline-Cache) mit `/ecc:prp-plan docs/01-requirements/meals.prd.md`

## Review-Nachtrag
Lokales Review (`.claude/PRPs/reviews/wochenplan-elterngerichte-backend-local-review.md`): ein MEDIUM- und zwei
LOW-Befunde, alle behoben — Rohlimit von 100 Zutatenzeilen (Schema `maxItems: 100`), `parseUuid` `internal`,
`weekParameter()` in `web/CallParameters.kt`.
