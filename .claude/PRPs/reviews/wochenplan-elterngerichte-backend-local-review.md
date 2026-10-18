# Local Review: Wochenplan & Elterngerichte – Backend (Phase 3a)

**Reviewed**: 2026-10-18
**Branch**: feat/wochenplan-elterngerichte-backend (uncommitted)
**Decision**: APPROVE with comments

## Summary
The change follows the attendance patterns exactly: one `dbQuery` per check-and-write, `AppException` with German messages,
logs that contain only IDs, writes for parents only, and a contract-first OpenAPI update. No CRITICAL or HIGH findings.
One MEDIUM finding (no upper bound on the raw ingredient list in the request) and two LOW findings.

## Findings

### CRITICAL
None

### HIGH
None

### MEDIUM
- **The raw ingredient list in the request has no upper bound** —
  `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanService.kt:78-87`, `api/openapi.yaml` (`SaveDishRequest.ingredients`).
  The service checks the 30-line limit only after trimming and dropping blank lines. The schema has no `maxItems` on
  purpose, and the backend has no limit on request body size. An authenticated parent device can send, for example, a
  million empty lines. Ktor parses all of them into memory and the service trims every one before the request is
  rejected. Impact is low (LAN only, parents only, rate limiting active), but the Pi has little memory.
  *Fix:* add a generous raw limit, e.g. `INGREDIENT_LINES_RAW_MAX = 100`: reject longer lists in the service before
  trimming (`Validation("Höchstens 30 Zutaten je Gericht")`) and put `maxItems: 100` in the schema. Add one test each
  in `MealPlanServiceTest` and `MealsPlanApiTest`.

### LOW
- **Inconsistent visibility in `RequestParsing.kt`** — `web/RequestParsing.kt:50-63`: `weekParameter()` is `internal`,
  `parseUuid()` is public. Both are only used inside the web package. *Fix:* make `parseUuid` `internal` as well.
- **`RequestParsing.kt` now also depends on Ktor** (`ApplicationCall`). It used to hold only pure parsing helpers.
  This is acceptable because the file stays small. If more call helpers are added later, move them to their own file
  (`CallParameters.kt`).

## Checked and fine
- Atomicity: `createIfDayFree` / `updateIfDayFree` check and write in one transaction. Pool size is 1
  (`DatabaseFactory.kt:50`), and the unique index `idx_meal_dishes_date` is a second safety net.
- Data integrity: updates replace the ingredients in the same transaction, `createdAt` is kept, and delete removes the
  ingredients explicitly (with FK cascade as a backup).
- No N+1 query: ingredients for a week are loaded with one `inList` query in the same transaction.
- Input validation: dates go through `parseDate` (years 2000–2100, so text comparison is correct), IDs through
  `uuidParameter`/`parseUuid`, and name and ingredient lengths match the `varchar` lengths.
- Authorization: `requireParent()` on POST/PUT/DELETE, `principalOrThrow()` on GET; covered by tests (403 ×3).
- Exhaustive `when` over `SaveOutcome` with no `else`; no `!!`; functions under 50 lines; files under 250 lines.
- Tests: names carry REQ IDs and use the AAA structure, every API request is validated against `openapi.yaml`, and the
  edge cases from the plan are covered.

## Validation Results

| Check | Result |
|---|---|
| Type check (compile) | Pass |
| Lint (ktlint, detekt) | Pass |
| Tests (core 34, meals 68, app 56) | Pass |
| Coverage (`koverVerify`) | Pass |
| Build (fat JAR, start on empty DB, V1–V3) | Pass |
| Android client (`:core:api:compileDebugKotlin`) | Pass |

## Files Reviewed
Added: `V3__meals_plan.sql`, `plan/Plan.kt`, `plan/MealPlanRepository.kt`, `plan/MealPlanService.kt`,
`persistence/PlanTables.kt`, `persistence/ExposedMealPlanRepository.kt`, `web/PlanApiMappers.kt`, `web/PlanRoutes.kt`,
`fakes/InMemoryMealPlanRepository.kt`, `plan/PlanTest.kt`, `plan/MealPlanServiceTest.kt`,
`persistence/ExposedMealPlanRepositoryTest.kt`, `MealsPlanApiTest.kt`.
Modified: `api/openapi.yaml`, `MealsModule.kt`, `AttendanceRoutes.kt`, `RequestParsing.kt`, `Application.kt`,
`ApiTestSupport.kt`, `MealsAttendanceApiTest.kt`, `docs/03-design/meals.md`, `docs/04-verification/traceability.md`,
`docs/01-requirements/meals.prd.md`, `backend/README.md`.

## Follow-up (2026-10-18)
All three findings are fixed:
- MEDIUM: `INGREDIENT_LINES_RAW_MAX = 100`. More raw lines are rejected before any trimming
  (`requireRawLineLimit`, its own function because of detekt `ThrowsCount`). The schema has `maxItems: 100`, and the
  design doc is updated. New tests: `REQ-MEAL-032 more than 100 raw ingredient lines are rejected even if blank`
  (service) plus one more case in `MealsPlanApiTest`.
- LOW: `parseUuid` is now `internal`.
- LOW: `weekParameter()` moved to `web/CallParameters.kt`; `RequestParsing.kt` holds only pure parsing helpers again.

Validation after the fixes: `./gradlew check koverVerify` passes (core 34, meals 69, app 56 tests);
`:core:api:compileDebugKotlin` passes.
