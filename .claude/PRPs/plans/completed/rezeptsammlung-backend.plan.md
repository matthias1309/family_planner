# Plan: Rezeptsammlung – Backend (Phase 5a)

## Summary

Das Backend bekommt eine Rezeptsammlung (anlegen/ändern/löschen nur Eltern, lesen und suchen für alle) und **strukturierte
Zutaten** (Name, optional Menge und Einheit) für Rezepte und Gerichte. Ein Gericht kann sich merken, aus welchem Rezept und
für wie viele Portionen es übernommen wurde (`recipeId`, `servings`). Der API-Vertrag wird **additiv** erweitert, damit die
installierte App (Stand 4b) unverändert weiterläuft. Umrechnen der Mengen, Import von rewe.de und alle Oberflächen sind 5b.

## User Story

Als Elternteil möchte ich unsere Lieblingsrezepte mit Mengen hinterlegen, und als Familienmitglied möchte ich sie beim
Planen auswählen, damit niemand die Zutaten jede Woche neu abtippt und die Mengen stimmen.

## Problem → Solution

Zutaten sind Freitextzeilen (`meal_dish_ingredients.text`), Rezepte gibt es nicht → Rezepte mit strukturierten Zutaten,
Gerichte mit strukturierten Zutaten plus Rezeptbezug; alte Freitextzeilen werden zu Zutaten nur mit Name.

## Metadata

- **Complexity**: Large
- **Source PRD**: `docs/01-requirements/meals.prd.md`
- **PRD Phase**: 5 Rezeptsammlung — Teil 5a Backend/API
- **Architektur**: ADR-0012 (verbindlich)
- **Estimated Files**: ~30 (≈ 14 neu, ≈ 16 geändert)

---

## UX Design

N/A — Backend/API. Die installierte App (4b) zeigt Gerichte weiter über `ingredients: string[]`; Rezepte sieht man erst mit 5b.

### Interaction Changes

| Touchpoint | Before | After | Notes |
|---|---|---|---|
| `GET …/plan`, `POST/PUT /dishes` Antwort | `ingredients: string[]` | zusätzlich `structuredIngredients`, `recipeId`, `servings`; `ingredients` = Anzeigetexte („500 g Spaghetti“) | alte App ignoriert neue Felder (`ignoreUnknownKeys`) |
| `POST/PUT /dishes` Request | nur `ingredients: string[]` | optional `structuredIngredients`, `recipeId`, `servings` | alte App sendet weiter Zeilen |
| Rezepte | – | `GET/POST /recipes`, `GET/PUT/DELETE /recipes/{id}` | Schreiben nur Eltern |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `docs/02-architecture/adr/0012-recipes-and-rewe-import.md` | all | Entscheidungen (Kopie statt Verknüpfung, additive API, nur Eltern) |
| P0 | `docs/01-requirements/meals.prd.md` | „Rezepte“, „Fachliche Regeln“ | REQ-MEAL-060, -062 (Server-Anteil) |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanService.kt` | all | Validierung, Fehler, Logging, wird erweitert |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedMealPlanRepository.kt` | all | Repository-Muster, Zutaten-Speicherung, wird erweitert |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/Plan.kt` | all | `Dish`, Outcomes |
| P0 | `api/openapi.yaml` | 397-532, 965-1060 | Dish-Endpunkte und -Schemas, Envelope-Stil |
| P1 | `backend/meals/src/main/kotlin/de/familyplanner/meals/web/PlanRoutes.kt`, `PlanApiMappers.kt`, `RequestParsing.kt` | all | Routen-/Mapper-Muster |
| P1 | `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/PlanTables.kt` | all | Exposed-Tabellen |
| P1 | `backend/meals/src/main/resources/db/migration/V3__meals_plan.sql`, `V4__meals_submissions.sql` | all | Migrationsstil |
| P1 | `backend/meals/src/test/kotlin/de/familyplanner/meals/persistence/MealsMigrationV4Test.kt` | all | Muster für Migrationstest |
| P1 | `backend/meals/src/test/kotlin/de/familyplanner/meals/fakes/PlanFixture.kt`, `InMemoryMealPlanRepository.kt` | all | Fakes, Verdrahtung in Tests |
| P1 | `backend/app/src/test/kotlin/de/familyplanner/app/ApiTestSupport.kt`, `MealsPlanApiTest.kt` | all | API-/Contract-Tests (`call`, `setupFamilyWithChild`) |
| P2 | `backend/core/src/main/kotlin/de/familyplanner/core/error/{AppException,Validation}.kt`, `core/auth/RoleGuard.kt` | all | Fehler, `validatedName`, `requireParent`, `uuidParameter` |
| P2 | `.claude/PRPs/plans/completed/einreichung-freigabe-backend.plan.md` | all | Vorgänger-Plan (gleiche Konventionen) |
| P2 | `docs/03-design/meals.md` | all | wird in Task 1 erweitert |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| SQLite `ALTER TABLE ADD COLUMN` | sqlite.org/lang_altertable.html | Spalte mit `REFERENCES` nur mit Default `NULL` erlaubt — passt für `recipe_id` |
| SQLite Fremdschlüssel | sqlite.org/foreignkeys.html | `ON DELETE SET NULL` wirkt nur mit `foreign_keys=ON`; ist gesetzt (`DatabaseFactory.kt:51`) |
| OpenAPI-Generator `kotlin` | openapi-generator.tech/docs/generators/kotlin | nicht-`required` Felder werden `T? = null` und hinten angehängt → bestehende Konstruktoraufrufe bleiben gültig |

Keine weitere Recherche nötig — alles folgt bestehenden internen Mustern.

---

## Patterns to Mirror

### NAMING_CONVENTION
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/plan/Plan.kt:12-26
@JvmInline
value class DishId(
    val value: String,
)

data class Dish(
    val id: DishId,
    val date: LocalDate,
    val cookId: MemberId,
    val name: String,
    val ingredients: List<String>,
    val status: DishStatus = DishStatus.APPROVED,
    val rejectionReason: String? = null,
    val late: Boolean = false,
)
```
Grenzwerte als `const val` oben in der Service-Datei (`MealPlanService.kt:15-20`). Fehlermeldungen deutsch, Code/Kommentare
englisch. Neues Paket `de.familyplanner.meals.recipe` (Domäne + Service), Persistenz in `meals.persistence`, Web in `meals.web`.

### ERROR_HANDLING
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanService.kt:143-153
private fun requireSaved(outcome: SaveOutcome) {
    val error =
        when (outcome) {
            SaveOutcome.SAVED -> return
            SaveOutcome.DAY_TAKEN -> AppException.Conflict("An diesem Tag ist schon ein Gericht geplant")
            SaveOutcome.NOT_FOUND -> AppException.NotFound(DISH_NOT_FOUND)
            ...
        }
    throw error
}
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/error/Validation.kt:7-17
fun validatedName(value: String, field: String, max: Int): String  // trim; 1..max sonst Validation("$field muss zwischen 1 und $max Zeichen lang sein")
```
Nur `AppException` (`Validation` 400, `Forbidden` 403, `NotFound` 404, `Conflict` 409); `StatusPages` baut das Envelope.
`when` über Outcomes erschöpfend, ohne `else`.

### LOGGING_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanService.kt:44,61
private val log = LoggerFactory.getLogger(MealPlanService::class.java)
log.info("Dish created: {} {}", draft.dish.id.value, draft.dish.status)
```
Nur IDs loggen — keine Rezeptnamen, Zutaten, Schritte oder URLs (`"Recipe created: {}"`, `"Recipe deleted: {}"`).

### REPOSITORY_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedMealPlanRepository.kt:83-110
override suspend fun updateIfDayFree(dish: Dish, at: Instant, cookWeek: PlanWeek?): SaveOutcome =
    dbQuery(db) {
        val exists = MealDishesTable.selectAll().where { MealDishesTable.id eq dish.id.value }.any()
        if (!exists) return@dbQuery SaveOutcome.NOT_FOUND
        ...
        MealDishesTable.update({ MealDishesTable.id eq dish.id.value }) { ... }
        MealDishIngredientsTable.deleteWhere { MealDishIngredientsTable.dishId eq dish.id.value }
        insertIngredients(dish)
        SaveOutcome.SAVED
    }
// :203-209
private fun JdbcTransaction.insertIngredients(dish: Dish) {
    MealDishIngredientsTable.batchInsert(dish.ingredients.withIndex()) { (index, line) ->
        this[MealDishIngredientsTable.dishId] = dish.id.value
        this[MealDishIngredientsTable.position] = index
        this[MealDishIngredientsTable.text] = line
    }
}
```
Prüfen und Schreiben in **einer** `dbQuery` (Pool-Größe 1 — verschachtelte `dbQuery` = Deadlock). Kinder-Tabellen werden
explizit gelöscht, obwohl `ON DELETE CASCADE` existiert (Fake-Parität). Mapper `internal fun ResultRow.toX()` am Dateiende.

### SERVICE_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanService.kt:37-63
class MealPlanService(
    private val members: MemberRepository,
    private val plan: MealPlanRepository,
    private val attendance: AttendanceService,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun create(actor: Actor, date: LocalDate, cookId: MemberId, name: String, ingredients: List<String>): Dish {
        requireMayWriteFor(actor, cookId)
        val draft = draft(actor, null, DishInput(DishId(newId()), date, cookId, name, ingredients))
        requireSaved(plan.createIfDayFree(draft.dish, clock.instant(), draft.cookWeek))
        log.info("Dish created: {} {}", draft.dish.id.value, draft.dish.status)
        return draft.dish
    }
}
```
Reine Regeln als Top-Level-Funktionen, damit sie ohne Fakes testbar sind (wie `weekPlanOf` in `Plan.kt`).

### ROUTE_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/web/PlanRoutes.kt:24-53
fun Route.planRoutes(plan: MealPlanService) {
    authenticate(AUTH_DEVICE) {
        post("/api/v1/meals/dishes") {
            val actor = call.principalOrThrow().toActor()
            val request = call.receive<SaveDishRequest>()
            val dish = plan.create(actor, parseDate(request.date, "date"), request.cook(), request.name, request.ingredients)
            call.respond(HttpStatusCode.Created, DishResponse(success = true, data = dish.toApi()))
        }
        delete("/api/v1/meals/dishes/{dishId}") {
            call.requireParent()
            plan.delete(call.dishId())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
private fun ApplicationCall.dishId() = DishId(uuidParameter("dishId"))
private fun SaveDishRequest.cook() = MemberId(parseUuid(cookId, "cookId"))
```
Mapper als Extension-Funktionen; Import-Alias bei Namenskollision (`import de.familyplanner.api.model.Dish as ApiDish`).

### KOIN_AND_WIRING
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/MealsModule.kt:19-27 ; app/Application.kt:79,92-94
single<MealPlanRepository> { ExposedMealPlanRepository(get()) }
single { MealPlanService(get(), get(), get(), clock) }
routing { … planRoutes(get<MealPlanService>()); cookingRoutes(get<DishReviewService>(), get<CookingWeekService>()) }
```

### TEST_STRUCTURE
```kotlin
// SOURCE: backend/meals/src/test/kotlin/de/familyplanner/meals/fakes/PlanFixture.kt:17-41
class PlanFixture(start: Instant = Instant.parse("2026-10-14T10:00:00Z")) {
    val clock = MutableClock(start)
    val members = InMemoryMemberRepository()
    val planRepository = InMemoryMealPlanRepository()
    ...
    val mealPlan = MealPlanService(members, planRepository, attendance, clock) { "dish-${++nextId}" }
    suspend fun parent(): Actor = Actor(members.create(aMember(role = Role.PARENT)).id, Role.PARENT)
}
// SOURCE: backend/meals/src/test/kotlin/de/familyplanner/meals/persistence/MealsMigrationV4Test.kt:15-51 (Migration roh per JDBC)
// SOURCE: backend/app/src/test/kotlin/de/familyplanner/app/MealsPlanApiTest.kt:31-70
private fun ApplicationTestBuilder.start() = application { module(testConfig(dir), FIXED_NOW) }
private suspend fun HttpClient.createDish(token: String, body: String, invalid: Boolean = false): HttpResponse =
    call(HttpMethod.Post, DISHES, token, body, invalidRequestExpected = invalid)
```
kotlin.test + JUnit 5, `runTest`, Fakes statt Mocks, AAA-Kommentare, Testname beginnt mit REQ-ID. `client.call(...)` prüft
Request **und** Response gegen `openapi.yaml`; `invalidRequestExpected = true` nur für absichtlich schema-ungültige Bodies.

---

## Domänenmodell & Regeln (verbindlich)

### Typen

```kotlin
// plan/Ingredient.kt
data class Ingredient(
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
)

/** Display line for clients that only read `ingredients: string[]`: "500 g Vollkornspaghetti", "2,5 Stange(n) Lauch", "Salz". */
fun Ingredient.displayText(): String

/** Legacy free-text lines → name-only ingredients: raw limit, trim, drop blank lines. */
fun ingredientsFromLines(lines: List<String>): List<Ingredient>

/** Trims name/unit, blank unit → null, checks limits; shared by dishes and recipes. */
fun validatedIngredients(ingredients: List<Ingredient>): List<Ingredient>

// plan/Plan.kt — Dish erweitert
data class RecipeRef(val recipeId: RecipeId, val servings: Int)
data class Dish(
    val id: DishId, val date: LocalDate, val cookId: MemberId, val name: String,
    val ingredients: List<Ingredient>,           // war List<String>
    val status: DishStatus = DishStatus.APPROVED,
    val rejectionReason: String? = null,
    val late: Boolean = false,
    val recipe: RecipeRef? = null,              // neu
)

// recipe/Recipe.kt
@JvmInline value class RecipeId(val value: String)
data class Recipe(
    val id: RecipeId, val name: String, val servings: Int,
    val ingredients: List<Ingredient>, val steps: List<String>,
    val sourceUrl: String?, val createdBy: MemberId,
)
data class RecipeSummary(val id: RecipeId, val name: String, val servings: Int)
enum class RecipeSaveOutcome { SAVED, NAME_TAKEN, NOT_FOUND }
```

### Regeln

| Thema | Regel |
|---|---|
| Zutat | `name` 1–80 nach Trim (`validatedName(…, "Zutat", INGREDIENT_MAX)`); `unit` nach Trim leer → `null`, sonst ≤ 20 (`UNIT_MAX`); `quantity` `null` oder endlich und `0 < q ≤ 10000` (`QUANTITY_MAX`), sonst 400 „Menge muss größer als 0 und höchstens 10000 sein“; höchstens 30 Zutaten (`INGREDIENTS_MAX_COUNT`, bestehend) |
| Freitextzeilen (alt) | wie bisher: roh ≤ 100 Zeilen (sonst 400), trimmen, Leerzeilen weg → `Ingredient(name)`; danach `validatedIngredients` |
| Dish-Request | `structuredIngredients != null` → diese gelten, `ingredients` muss leer sein, sonst 400 „Zutaten bitte entweder als Zeilen oder strukturiert angeben“; sonst gelten die Zeilen |
| Rezeptbezug am Gericht | `recipeId` und `servings` nur zusammen, sonst 400 „Rezept und Portionen gehören zusammen“; `servings` 1–20 (`SERVINGS_MAX`), sonst 400; Rezept muss existieren, sonst 404 „Rezept nicht gefunden“; Kinder dürfen `recipeId` setzen (sie wählen aus) |
| Reihenfolge der Prüfungen (Gericht) | 403 fremdes Gericht → 400 Eingaben → 404 Mitglied/Rezept → Kind-Regeln aus Phase 4 → 409 Repository |
| Kind-Regel „mind. eine Zutat“ | gilt unverändert für die resultierende Zutatenliste (Zeilen oder strukturiert) |
| Gericht ändern | ersetzt auch `recipe` vollständig (fehlt der Bezug im Request → `null`) |
| Mengen am Gericht | Server speichert, was kommt — **keine** Umrechnung auf dem Server (macht die App beim Übernehmen, 5b) |
| Anzeigetext | `displayText()`: Menge ohne überflüssige Nullen, Dezimalkomma (`BigDecimal.valueOf(q).stripTrailingZeros().toPlainString().replace('.', ',')`), dann Einheit, dann Name, getrennt durch ein Leerzeichen; fehlende Teile entfallen |
| Rezept schreiben | nur Eltern (`requireParent()` in der Route; 403 „Nur für Eltern“) |
| Rezept lesen/suchen | alle angemeldeten Mitglieder |
| Rezept-Name | `validatedName(…, "Name", DISH_NAME_MAX)` = 1–60 (gleiches Limit wie Gericht, weil der Name kopiert wird); **eindeutig ohne Groß-/Kleinschreibung** (`name_key = name.lowercase(Locale.ROOT)`), sonst 409 „Ein Rezept mit diesem Namen gibt es schon“ |
| Rezept-Portionen | 1–20 (`SERVINGS_MAX`), sonst 400 „Portionen müssen zwischen 1 und 20 liegen“ |
| Rezept-Zutaten | `validatedIngredients`, mindestens 1, sonst 400 „Bitte mindestens eine Zutat angeben“ |
| Schritte | roh ≤ 100 (`STEPS_RAW_MAX`), trimmen, leere entfernen, danach ≤ 30 (`STEPS_MAX_COUNT`), je ≤ 1000 Zeichen (`STEP_MAX`); 0 Schritte erlaubt |
| Quell-URL | trimmen, leer → `null`; ≤ 500 (`SOURCE_URL_MAX`); `java.net.URI` parsebar, Schema `http`/`https`, Host nicht leer — sonst 400 „Ungültige Quell-URL“ |
| Suche | `query` getrimmt, leer → alle; > 60 Zeichen → 400; Treffer = Name enthält Query (beide `lowercase(Locale.ROOT)`); sortiert nach `name.lowercase(Locale.ROOT)`, dann `id`; Filtern in Kotlin (Sammlung klein, Unicode-sicher) |
| Rezept ändern | ersetzt Name, Portionen, Zutaten, Schritte, URL vollständig; `createdBy` bleibt; bestehende Gerichte unverändert |
| Rezept löschen | löscht Rezept samt Zutaten/Schritten; `meal_dishes.recipe_id` der Gerichte → `NULL` (explizit in derselben Transaktion, zusätzlich FK `ON DELETE SET NULL`); `servings` am Gericht bleibt; Gerichte selbst unverändert; unbekannt → 404 |

### Migration `V5__meals_recipes.sql`

```sql
-- Phase 5: recipes with structured ingredients (ADR-0012). Free-text lines stay as name-only ingredients.
CREATE TABLE meal_recipes (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  name_key TEXT NOT NULL,
  servings INTEGER NOT NULL CHECK (servings BETWEEN 1 AND 20),
  source_url TEXT,
  created_by TEXT NOT NULL REFERENCES members(id),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

-- Recipe names are unique ignoring case; name_key is lowercased in Kotlin (Locale.ROOT), so umlauts compare correctly.
CREATE UNIQUE INDEX idx_meal_recipes_name_key ON meal_recipes(name_key);

CREATE TABLE meal_recipe_ingredients (
  recipe_id TEXT NOT NULL REFERENCES meal_recipes(id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  name TEXT NOT NULL,
  quantity REAL,
  unit TEXT,
  PRIMARY KEY (recipe_id, position)
);

CREATE TABLE meal_recipe_steps (
  recipe_id TEXT NOT NULL REFERENCES meal_recipes(id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  text TEXT NOT NULL,
  PRIMARY KEY (recipe_id, position)
);

-- Existing dish ingredient lines keep their text as the ingredient name; quantity and unit stay NULL.
ALTER TABLE meal_dish_ingredients ADD COLUMN quantity REAL;
ALTER TABLE meal_dish_ingredients ADD COLUMN unit TEXT;

-- A dish remembers the recipe it was copied from; deleting the recipe keeps the dish.
ALTER TABLE meal_dishes ADD COLUMN recipe_id TEXT REFERENCES meal_recipes(id) ON DELETE SET NULL;
ALTER TABLE meal_dishes ADD COLUMN servings INTEGER CHECK (servings IS NULL OR servings BETWEEN 1 AND 20);
```
Die Spalte `meal_dish_ingredients.text` wird **nicht** umbenannt (kein Tabellen-Neuaufbau nötig); in Exposed bleibt die
Property `text` und bedeutet „Name“ (KDoc-Kommentar).

---

## API-Vertrag (Task 2, `api/openapi.yaml`)

`info.version: 0.5.0`. Neuer Parameter und neue Schemas:

```yaml
  parameters:
    RecipeId:
      name: recipeId
      in: path
      required: true
      schema: { type: string, format: uuid }

  schemas:
    Ingredient:
      type: object
      required: [name]
      properties:
        name: { type: string, minLength: 1, maxLength: 80 }
        quantity: { type: number, format: double, minimum: 0, exclusiveMinimum: true, maximum: 10000, nullable: true }
        unit: { type: string, maxLength: 20, nullable: true }

    Recipe:
      type: object
      required: [id, name, servings, ingredients, steps, createdBy]
      properties:
        id: { type: string, format: uuid }
        name: { type: string, minLength: 1, maxLength: 60 }
        servings: { type: integer, minimum: 1, maximum: 20 }
        ingredients: { type: array, minItems: 1, maxItems: 30, items: { $ref: '#/components/schemas/Ingredient' } }
        steps: { type: array, maxItems: 30, items: { type: string, minLength: 1, maxLength: 1000 } }
        sourceUrl: { type: string, maxLength: 500, nullable: true }
        createdBy: { type: string, format: uuid }

    RecipeSummary:
      type: object
      required: [id, name, servings]
      properties:
        id: { type: string, format: uuid }
        name: { type: string, minLength: 1, maxLength: 60 }
        servings: { type: integer, minimum: 1, maximum: 20 }

    SaveRecipeRequest:
      type: object
      required: [name, servings, ingredients, steps]
      properties:
        name: { type: string, maxLength: 60 }
        servings: { type: integer }
        ingredients: { type: array, maxItems: 30, items: { $ref: '#/components/schemas/Ingredient' } }
        steps:
          type: array
          description: Ein Eintrag pro Schritt; leere Einträge werden entfernt, danach höchstens 30.
          maxItems: 100
          items: { type: string, maxLength: 1000 }
        sourceUrl: { type: string, maxLength: 500, nullable: true }

    RecipeResponse / RecipeListResponse:   # Envelope wie DishResponse; RecipeListResponse.data = array of RecipeSummary (nullable)
```
Im `SaveRecipeRequest` bewusst wenige Schema-Grenzen: `name` nur `maxLength: 60` (ohne `minLength`), `servings` ohne
`minimum/maximum`. So erreichen `""`, `"   "`, `0` und `21` den Server, der mit deutscher Meldung 400 antwortet, und die
Tests brauchen dafür kein `invalidRequestExpected`.

Änderungen an bestehenden Schemas:

```yaml
    Dish:
      required: [id, date, cookId, name, ingredients, structuredIngredients, status, late, dayValid]
      properties:
        ingredients:
          type: array
          description: Anzeigetexte der Zutaten ("500 g Spaghetti"); für ältere App-Versionen.
          maxItems: 30
          items: { type: string, minLength: 1, maxLength: 120 }     # war 80: Menge + Einheit + Name
        structuredIngredients: { type: array, maxItems: 30, items: { $ref: '#/components/schemas/Ingredient' } }
        recipeId: { type: string, format: uuid, nullable: true }
        servings: { type: integer, minimum: 1, maximum: 20, nullable: true }

    SaveDishRequest:      # ingredients bleibt required (alte App); neue Felder optional
      properties:
        structuredIngredients:
          type: array
          nullable: true
          description: Wenn gesetzt, gelten diese Zutaten und `ingredients` muss leer sein.
          maxItems: 30
          items: { $ref: '#/components/schemas/Ingredient' }
        recipeId: { type: string, format: uuid, nullable: true }
        servings: { type: integer, nullable: true }
```

Neue Pfade (alle `tags: [meals]`, Fehler per `$ref: '#/components/responses/...'`, inkl. `'429'`):

| Methode & Pfad | operationId | summary | Erfolg | Fehler |
|---|---|---|---|---|
| `GET /api/v1/meals/recipes` (Query `query`, optional, `maxLength: 60`) | `listRecipes` | Rezepte auflisten und suchen | 200 `RecipeListResponse` | 400, 401 |
| `POST /api/v1/meals/recipes` | `createRecipe` | Rezept anlegen (nur Eltern) | 201 `RecipeResponse` | 400, 401, 403, 409 |
| `GET /api/v1/meals/recipes/{recipeId}` | `getRecipe` | Rezept mit Zutaten und Schritten | 200 `RecipeResponse` | 400, 401, 404 |
| `PUT /api/v1/meals/recipes/{recipeId}` | `updateRecipe` | Rezept ändern (nur Eltern); übernommene Gerichte bleiben unverändert | 200 `RecipeResponse` | 400, 401, 403, 404, 409 |
| `DELETE /api/v1/meals/recipes/{recipeId}` | `deleteRecipe` | Rezept löschen (nur Eltern); übernommene Gerichte bleiben | 204 | 400, 401, 403, 404 |

`query` als Query-Parameter direkt am Pfad (`in: query`, `required: false`, `schema: {type: string, maxLength: 60}`), im
Code `call.request.queryParameters["query"]`; länger als 60 → Server prüft ebenfalls (400).

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `docs/03-design/meals.md` | UPDATE | Abschnitt „Rezepte & strukturierte Zutaten (Phase 5a)“, Schnittstellen, DB-Tabellen, Grenzen |
| `api/openapi.yaml` | UPDATE | Vertrag zuerst (ADR-0004) |
| `backend/meals/src/main/resources/db/migration/V5__meals_recipes.sql` | CREATE | Schema |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/Ingredient.kt` | CREATE | Typ + reine Funktionen |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/Plan.kt` | UPDATE | `Dish.ingredients: List<Ingredient>`, `RecipeRef`, `Dish.recipe` |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanService.kt` | UPDATE | strukturierte Zutaten, Rezeptbezug, Rezept-Existenz |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/recipe/Recipe.kt` | CREATE | Domäne |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/recipe/RecipeRules.kt` | CREATE | reine Validierung (Schritte, URL, Portionen, Suche) |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/recipe/RecipeRepository.kt` | CREATE | Interface |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/recipe/RecipeService.kt` | CREATE | Anwendungslogik |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/PlanTables.kt` | UPDATE | neue Spalten |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/RecipeTables.kt` | CREATE | Exposed-Tabellen |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedMealPlanRepository.kt` | UPDATE | Zutaten/Rezeptbezug lesen und schreiben |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedRecipeRepository.kt` | CREATE | Persistenz |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/PlanRoutes.kt` | UPDATE | neue Request-Felder |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/PlanApiMappers.kt` | UPDATE | Dish-Antwort, `Ingredient`-Mapper |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/RecipeRoutes.kt` | CREATE | Endpunkte |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/RecipeApiMappers.kt` | CREATE | Mapper |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/MealsModule.kt` | UPDATE | Koin |
| `backend/app/src/main/kotlin/de/familyplanner/app/Application.kt` | UPDATE | `recipeRoutes(...)` |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/plan/IngredientTest.kt` | CREATE | reine Funktionen |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/recipe/RecipeRulesTest.kt` | CREATE | reine Regeln |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/recipe/RecipeServiceTest.kt` | CREATE | Service mit Fakes |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/fakes/InMemoryRecipeRepository.kt` | CREATE | Fake |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/fakes/PlanFixture.kt` | UPDATE | Rezept-Fake verdrahten, Helfer `lines(...)` |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/plan/MealPlanServiceTest.kt` | UPDATE | Typänderung + neue Fälle |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/persistence/ExposedMealPlanRepositoryTest.kt` | UPDATE | Typänderung + Roundtrip strukturiert/Rezeptbezug |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/persistence/ExposedRecipeRepositoryTest.kt` | CREATE | gegen echte SQLite |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/persistence/MealsMigrationV5Test.kt` | CREATE | Bestandsdaten nach V5 |
| `backend/app/src/test/kotlin/de/familyplanner/app/MealsRecipeApiTest.kt` | CREATE | Integration + Contract |
| `backend/app/src/test/kotlin/de/familyplanner/app/ContractValidator.kt` | UPDATE | Query-Parameter an den Validator geben |
| `backend/app/src/test/kotlin/de/familyplanner/app/MealsPlanApiTest.kt`, `MealsSubmissionApiTest.kt` | UPDATE | nur falls Assertions auf `ingredients` brechen (Werte bleiben gleich, da Name-only-Anzeigetext = Name) |
| `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/data/RemoteMealPlanRepositoryTest.kt` | UPDATE | `dishJson` um `structuredIngredients` (neues Pflichtfeld im generierten Modell) |
| `backend/README.md`, `docs/04-verification/traceability.md`, `docs/01-requirements/meals.prd.md` | UPDATE | Doku |

## NOT Building

- Mengen-Umrechnung auf Portionen, Portionen-Vorschlag aus Anwesenheit (App, 5b — die App hat Anwesenheit und Mitgliederliste schon)
- Rezept-Import, Zerlegen von Zutatenzeilen, Entfernen von Eigenmarken, WebView, Teilen-Menü (App, 5b)
- Server-seitiges Laden externer URLs (ADR-0012: nie)
- Rezeptbilder, Kategorien, Tags, Bewertungen, Nährwerte
- Zusammenführen/Einkaufsliste (Phase 6), Einheiten-Umrechnung
- Paging der Rezeptliste (Sammlung klein; bei Bedarf später)
- Offline-Cache für Rezepte (Backlog)
- Entfernen von `ingredients: string[]` aus dem Vertrag (erst wenn alle Geräte ≥ 5b, eigener Schritt)
- Änderungen an Freigabe-/Absage-Logik aus Phase 4

---

## Step-by-Step Tasks

### Task 1: Moduldesign erweitern (V-Modell links)
- **ACTION**: `docs/03-design/meals.md` erweitern.
- **IMPLEMENT**: Titel um „& Rezepte“ ergänzen; „Abgedeckte Anforderungen“ um REQ-MEAL-060 und REQ-MEAL-062 (Server-Anteil:
  Rezeptbezug und strukturierte Zutaten am Gericht) ergänzen; neuer Abschnitt `## Rezepte & strukturierte Zutaten (Phase 5a)`
  mit den Tabellen aus „Domänenmodell & Regeln“ und Verweis auf ADR-0012; „Schnittstellen“ um die fünf Rezept-Endpunkte und
  die neuen Dish-Felder; „DB-Tabellen“ um `V5__meals_recipes.sql`; im Phase-3-Abschnitt Zeile „Zutaten | Freitext …“ mit
  „ab Phase 5: strukturiert, siehe unten“ ergänzen.
- **MIRROR**: Abschnittsstruktur von „Einreichung & Freigabe (Phase 4)“.
- **GOTCHA**: Kein neues ADR — ADR-0012 deckt alles ab.
- **VALIDATE**: Regeln identisch mit diesem Plan; Links auf ADR-0012 funktionieren.

### Task 2: OpenAPI-Vertrag (Skill `contract-first`, ADR-0004)
- **ACTION**: `api/openapi.yaml` gemäß „API-Vertrag“ ändern.
- **IMPLEMENT**: Parameter `RecipeId`; Schemas `Ingredient`, `Recipe`, `RecipeSummary`, `SaveRecipeRequest`, `RecipeResponse`,
  `RecipeListResponse`; `Dish` und `SaveDishRequest` erweitern; fünf Operationen; `info.version: 0.5.0`.
- **MIRROR**: `createDish`/`deleteDish` (`openapi.yaml:397-458`), Envelope `DishResponse` (`1020-1032`).
- **GOTCHA**: Nullable-Objekte nur als `allOf: [$ref] + nullable: true`; nullable Arrays als `type: array` + `nullable: true`.
  `required` sorgfältig setzen — `Dish.structuredIngredients` ist required (Server sendet es immer), in `SaveDishRequest`
  nicht. Generator legt Nicht-Pflichtfelder als `T? = null` ans Ende → `SaveDishRequest("…", "…", "…", listOf(...))` im
  Android-Test bleibt gültig. `exclusiveMinimum: true` ist OpenAPI-3.0-Syntax (boolesch), nicht 3.1.
- **VALIDATE**: `cd backend && ./gradlew :api-contract:compileKotlin` erzeugt `Ingredient`, `Recipe`, `RecipeListResponse`;
  `cd android && ./gradlew :core:api:compileDebugKotlin` grün.

### Task 3: Migration V5
- **ACTION**: `backend/meals/src/main/resources/db/migration/V5__meals_recipes.sql` mit dem SQL aus „Migration V5“ anlegen.
- **MIRROR**: `V3__meals_plan.sql`, `V4__meals_submissions.sql` (Kommentar oben, Präfix `meal_`).
- **GOTCHA**: `CREATE TABLE meal_recipes` **vor** dem `ALTER TABLE meal_dishes … REFERENCES meal_recipes`. Vor dem Merge
  `ls backend/*/src/main/resources/db/migration` prüfen (keine parallele V5). `MealsMigrationV4Test` splittet SQL an `;` —
  keine Semikolons in Kommentaren.
- **VALIDATE**: `MealsMigrationV5Test` (Task 9) grün; App-Start meldet „Successfully applied 5 migrations“ bei leerer DB.

### Task 4: Zutaten-Domäne (TDD: `IngredientTest` zuerst RED)
- **ACTION**: `plan/Ingredient.kt` anlegen; Konstanten `INGREDIENT_MAX`, `INGREDIENTS_MAX_COUNT`, `INGREDIENT_LINES_RAW_MAX`
  aus `MealPlanService.kt` hierher verschieben; neu `UNIT_MAX = 20`, `QUANTITY_MAX = 10_000.0`.
- **IMPLEMENT**:
  ```kotlin
  fun ingredientsFromLines(lines: List<String>): List<Ingredient> {
      if (lines.size > INGREDIENT_LINES_RAW_MAX) throw AppException.Validation(TOO_MANY_INGREDIENTS)
      return lines.map { it.trim() }.filter { it.isNotEmpty() }.map { Ingredient(it) }
  }

  fun validatedIngredients(ingredients: List<Ingredient>): List<Ingredient> {
      if (ingredients.size > INGREDIENTS_MAX_COUNT) throw AppException.Validation(TOO_MANY_INGREDIENTS)
      return ingredients.map { it.validated() }
  }

  private fun Ingredient.validated(): Ingredient {
      val validName = validatedName(name, "Zutat", INGREDIENT_MAX)
      val validUnit = unit?.trim()?.takeIf { it.isNotEmpty() }
      if (validUnit != null && validUnit.length > UNIT_MAX) throw AppException.Validation("Einheit darf höchstens $UNIT_MAX Zeichen lang sein")
      if (quantity != null && !(quantity.isFinite() && quantity > 0 && quantity <= QUANTITY_MAX)) {
          throw AppException.Validation("Menge muss größer als 0 und höchstens ${QUANTITY_MAX.toInt()} sein")
      }
      return Ingredient(validName, quantity, validUnit)
  }

  fun Ingredient.displayText(): String =
      listOfNotNull(quantity?.let(::formatQuantity), unit, name).joinToString(" ")

  private fun formatQuantity(value: Double): String =
      BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', ',')
  ```
  `TOO_MANY_INGREDIENTS = "Höchstens $INGREDIENTS_MAX_COUNT Zutaten je Gericht"` → zu `"Höchstens $INGREDIENTS_MAX_COUNT Zutaten"`
  verallgemeinern (gilt auch für Rezepte); bestehende Tests, die den alten Text prüfen, anpassen (`grep -rn "Zutaten je Gericht" backend`).
- **MIRROR**: Top-Level-Regeln wie `weekPlanOf` (`Plan.kt:63-70`); `validatedName` aus `core.error`.
- **GOTCHA**: Die bisherige Meldung bei zu langer Zutat war „Eine Zutat darf höchstens 80 Zeichen lang sein“; `validatedName`
  liefert „Zutat muss zwischen 1 und 80 Zeichen lang sein“ — Tests, die den alten Text prüfen, mit anpassen.
  `Double.isFinite()` fängt `NaN`/`Infinity` (kann JSON nicht senden, aber Fakes/Tests). Kein `!!`.
- **VALIDATE**: `IngredientTest` grün (Fälle siehe Testing Strategy).

### Task 5: `Dish` erweitern und Repository anpassen (TDD)
- **ACTION**: `Plan.kt`, `PlanTables.kt`, `ExposedMealPlanRepository.kt`, `InMemoryMealPlanRepository.kt` (Fake braucht keine
  Änderung außer Typen), `ExposedMealPlanRepositoryTest.kt`.
- **IMPLEMENT**:
  - `Plan.kt`: `Dish.ingredients: List<Ingredient>`, `val recipe: RecipeRef? = null`, `data class RecipeRef(val recipeId: RecipeId, val servings: Int)`
    (Import `de.familyplanner.meals.recipe.RecipeId`).
  - `PlanTables.kt`: `MealDishesTable` + `val recipeId = varchar("recipe_id", ID_LENGTH).nullable()`, `val servings = integer("servings").nullable()`;
    `MealDishIngredientsTable` + `val quantity = double("quantity").nullable()`, `val unit = varchar("unit", UNIT_LENGTH).nullable()`,
    KDoc an `text`: „Ingredient name (column kept from V3)“. `UNIT_LENGTH = 20`.
  - `ExposedMealPlanRepository`: insert/update schreiben `recipeId = dish.recipe?.recipeId?.value`, `servings = dish.recipe?.servings`;
    `insertIngredients` schreibt `text = ingredient.name`, `quantity`, `unit`; `ingredientsOf` liefert `Map<String, List<Ingredient>>`;
    `toDish` baut `recipe` nur, wenn beide Spalten nicht null sind (`recipeId` null + `servings` gesetzt nach Rezept-Löschung →
    `recipe = null`; `servings` geht dann in der API verloren — akzeptiert, siehe Notes).
- **MIRROR**: REPOSITORY_PATTERN.
- **GOTCHA**: `RecipeRef` braucht `recipeId` — nach Löschen des Rezepts ist der Bezug weg; das Gericht bleibt sonst gleich.
  Bestehende Tests bauen `Dish(... ingredients = listOf("Nudeln"))` → auf `lines("Nudeln")` umstellen (Helfer in
  `PlanFixture.kt` als Top-Level `fun lines(vararg names: String) = names.map { Ingredient(it) }`).
- **VALIDATE**: `ExposedMealPlanRepositoryTest`: neue Fälle „strukturierte Zutaten bleiben in Reihenfolge mit Menge/Einheit“,
  „Rezeptbezug wird gespeichert und geladen“; alle bestehenden Fälle grün.

### Task 6: Rezept-Domäne, Regeln und Repository (TDD)
- **ACTION**: `recipe/Recipe.kt`, `recipe/RecipeRules.kt`, `recipe/RecipeRepository.kt`, `persistence/RecipeTables.kt`,
  `persistence/ExposedRecipeRepository.kt`, `fakes/InMemoryRecipeRepository.kt`; Tests `RecipeRulesTest`, `ExposedRecipeRepositoryTest`.
- **IMPLEMENT**:
  - `RecipeRules.kt` (rein): Konstanten `SERVINGS_MAX = 20`, `STEP_MAX = 1000`, `STEPS_MAX_COUNT = 30`, `STEPS_RAW_MAX = 100`,
    `SOURCE_URL_MAX = 500`, `RECIPE_QUERY_MAX = 60`; Funktionen `validatedServings(value: Int): Int`, `validatedSteps(steps: List<String>): List<String>`,
    `validatedSourceUrl(value: String?): String?` (URI-Prüfung mit `runCatching { URI(it) }`), `nameKey(name: String) = name.lowercase(Locale.ROOT)`,
    `matchingRecipes(all: List<RecipeSummary>, query: String?): List<RecipeSummary>` (Trim, > 60 → Validation, Filter + Sortierung).
  - `RecipeRepository`:
    ```kotlin
    interface RecipeRepository {
        suspend fun findAllSummaries(): List<RecipeSummary>
        suspend fun findById(id: RecipeId): Recipe?
        suspend fun exists(id: RecipeId): Boolean
        /** Atomically inserts unless another recipe has the same [nameKey]; never NOT_FOUND. */
        suspend fun createIfNameFree(recipe: Recipe, at: Instant): RecipeSaveOutcome
        /** Atomically replaces content (not createdBy) unless the name belongs to another recipe. */
        suspend fun updateIfNameFree(recipe: Recipe, at: Instant): RecipeSaveOutcome
        /** Deletes the recipe with ingredients and steps and clears `recipe_id` on dishes. */
        suspend fun delete(id: RecipeId): Boolean
    }
    ```
  - `RecipeTables.kt`: `MealRecipesTable` (`id`, `name`, `nameKey`, `servings`, `sourceUrl` nullable, `createdBy`, `createdAt`, `updatedAt`),
    `MealRecipeIngredientsTable` (`recipeId`, `position`, `name`, `quantity` double nullable, `unit` nullable), `MealRecipeStepsTable`
    (`recipeId`, `position`, `text`). Längen-Konstanten privat am Dateiende wie `PlanTables.kt:36-41` (Name 60, Zutat 80, Einheit 20,
    Schritt 1000, URL 500).
  - `ExposedRecipeRepository`: Namensprüfung `nameKey eq … and id neq …` in derselben `dbQuery` wie das Schreiben; `delete` in einer
    `dbQuery`: `MealDishesTable.update({ MealDishesTable.recipeId eq id.value }) { it[MealDishesTable.recipeId] = null }`, dann
    Steps, Ingredients, Recipe löschen; `findAllSummaries` liest nur `id, name, servings` (Sortierung macht `matchingRecipes`).
  - `InMemoryRecipeRepository`: `mutableMapOf<RecipeId, Recipe>()`, gleiche Outcomes; `delete` kennt keine Gerichte (Parität für
    das Gericht prüft `ExposedRecipeRepositoryTest`).
- **MIRROR**: REPOSITORY_PATTERN, NAMING_CONVENTION.
- **GOTCHA**: `name_key` immer aus dem **validierten** (getrimmten) Namen. Unique-Index ist nur Absicherung — der Konflikt wird
  vorher per Abfrage erkannt (sonst SQLite-Exception = 500). Exposed-Import: `org.jetbrains.exposed.v1.core.*` / `…v1.jdbc.*`
  wie in `ExposedMealPlanRepository.kt:13-27`.
- **VALIDATE**: `RecipeRulesTest` und `ExposedRecipeRepositoryTest` grün (Fälle siehe Testing Strategy).

### Task 7: `RecipeService` und `MealPlanService` erweitern (TDD)
- **ACTION**: `recipe/RecipeService.kt` anlegen; `MealPlanService.kt` ändern; `PlanFixture.kt` erweitern; Tests
  `RecipeServiceTest`, `MealPlanServiceTest`.
- **IMPLEMENT**:
  - `RecipeService(recipes: RecipeRepository, clock: Clock, newId: () -> String = { UUID.randomUUID().toString() })`:
    `list(query: String?)`, `get(id)` (404 „Rezept nicht gefunden“), `create(actor: Actor, input: RecipeInput)`,
    `update(id, input)`, `delete(id)`. `data class RecipeInput(val name: String, val servings: Int, val ingredients: List<Ingredient>, val steps: List<String>, val sourceUrl: String?)`.
    Validierung: Name → Portionen → Zutaten (≥ 1) → Schritte → URL. Outcome-Mapping erschöpfend (`NAME_TAKEN` → 409, `NOT_FOUND` → 404).
    Logging `"Recipe created: {}"`, `"Recipe updated: {}"`, `"Recipe deleted: {}"`. Rollenprüfung liegt in der Route
    (`requireParent()`), wie `DELETE /dishes`; der Service bekommt beim Anlegen `actor.id` als `createdBy`.
  - `MealPlanService`: Konstruktor + `private val recipes: RecipeRepository` (nach `attendance`); `create(actor, date, cookId, name,
    ingredients: List<Ingredient>, recipe: RecipeRef? = null)`, `update(…, recipe: RecipeRef? = null)`; `DishInput` + `recipe`;
    `draft()` ruft `validatedIngredients(input.ingredients)` und `validatedRecipeRef(input.recipe)` (Portionen 1–20 via
    `validatedServings`, dann `recipes.exists` → sonst 404) und setzt `Dish(…, recipe = …)`. Alte private `validatedIngredients`/
    `requireRawLineLimit` entfallen (wandern nach `Ingredient.kt`).
  - `PlanFixture`: `val recipeRepository = InMemoryRecipeRepository()`, `mealPlan = MealPlanService(members, planRepository, attendance, recipeRepository, clock) {…}`,
    `val recipes = RecipeService(recipeRepository, clock) { "recipe-${++nextRecipeId}" }`; Top-Level `fun lines(vararg names: String)`.
- **MIRROR**: SERVICE_PATTERN, ERROR_HANDLING.
- **GOTCHA**: Reihenfolge in `draft()`: Name → Zutaten → Rezeptbezug → Mitglied (bisher Name → Zutaten → Mitglied); 403 bleibt
  vorn (`requireMayWriteFor` vor `draft`). IDs im Fake sind keine UUIDs — nur im Service-Test; API-Tests nutzen echte UUIDs.
  `RecipeRef` validieren heißt: `servings` prüfen **bevor** das Repository gefragt wird.
- **VALIDATE**: `./gradlew :meals:test` grün.

### Task 8: Routen, Mapper, Koin, App-Verdrahtung
- **ACTION**: `PlanRoutes.kt`, `PlanApiMappers.kt` ändern; `RecipeRoutes.kt`, `RecipeApiMappers.kt` anlegen; `MealsModule.kt`, `Application.kt`.
- **IMPLEMENT**:
  - `PlanApiMappers.kt`: `fun Ingredient.toApi() = ApiIngredient(name, quantity, unit)`, `fun ApiIngredient.toDomain() = Ingredient(name, quantity, unit)`;
    `Dish.toApi()` + `ingredients = ingredients.map { it.displayText() }`, `structuredIngredients = ingredients.map { it.toApi() }`,
    `recipeId = recipe?.recipeId?.value`, `servings = recipe?.servings`.
  - `PlanRoutes.kt`: privater Helfer
    ```kotlin
    private fun SaveDishRequest.ingredientList(): List<Ingredient> {
        val structured = structuredIngredients ?: return ingredientsFromLines(ingredients)
        if (ingredients.isNotEmpty()) throw AppException.Validation("Zutaten bitte entweder als Zeilen oder strukturiert angeben")
        return structured.map { it.toDomain() }
    }
    private fun SaveDishRequest.recipeRef(): RecipeRef? {
        val id = recipeId
        val count = servings
        if (id == null && count == null) return null
        if (id == null || count == null) throw AppException.Validation("Rezept und Portionen gehören zusammen")
        return RecipeRef(RecipeId(parseUuid(id, "recipeId")), count)
    }
    ```
    POST/PUT übergeben `request.ingredientList()` und `request.recipeRef()`.
  - `RecipeRoutes.kt` — `fun Route.recipeRoutes(recipes: RecipeService)` in `authenticate(AUTH_DEVICE)`:
    - `get("/api/v1/meals/recipes")` → `principalOrThrow()`, `recipes.list(call.request.queryParameters["query"])` → 200 `RecipeListResponse`.
    - `post("/api/v1/meals/recipes")` → `val actor = call.requireParent().toActor()`, `receive<SaveRecipeRequest>()` → 201 `RecipeResponse`.
    - `get("/api/v1/meals/recipes/{recipeId}")` → `principalOrThrow()` → 200.
    - `put(…/{recipeId})` → `requireParent()` → 200; `delete(…/{recipeId})` → `requireParent()` → 204.
    - `private fun ApplicationCall.recipeId() = RecipeId(uuidParameter("recipeId"))`; `SaveRecipeRequest.toInput() = RecipeInput(name, servings, ingredients.map { it.toDomain() }, steps, sourceUrl)`.
  - `RecipeApiMappers.kt`: `Recipe.toApi()` (`createdBy = createdBy.value`), `RecipeSummary.toApi()`.
  - `MealsModule.kt`: `single<RecipeRepository> { ExposedRecipeRepository(get()) }`, `single { RecipeService(get(), clock) }`,
    `single { MealPlanService(get(), get(), get(), get(), clock) }`.
  - `Application.kt`: `recipeRoutes(get<RecipeService>())` nach `cookingRoutes(...)`; Import.
- **MIRROR**: ROUTE_PATTERN, KOIN_AND_WIRING.
- **GOTCHA**: Import-Aliase `de.familyplanner.api.model.Ingredient as ApiIngredient`, `…Recipe as ApiRecipe`,
  `…RecipeSummary as ApiRecipeSummary`. `requireParent()` gibt den `DevicePrincipal` zurück → `.toActor()`. Für Kinder auf
  POST/PUT/DELETE muss 403 vor jeder Body-Validierung kommen → `requireParent()` als erste Zeile, **vor** `receive`.
- **VALIDATE**: `./gradlew :app:compileKotlin` grün; `FP_SETUP_TOKEN=local-setup-token-123 ./gradlew :app:run` startet,
  „Successfully applied 5 migrations“.

### Task 9: Integrations-, Contract- und Migrationstests
- **ACTION**: `MealsRecipeApiTest`, `MealsMigrationV5Test` anlegen; `MealsPlanApiTest` ergänzen.
- **IMPLEMENT**:
  - `MealsMigrationV5Test` (Muster `MealsMigrationV4Test`): V1–V4 anwenden, Elternteil + Gericht + zwei Zutatenzeilen einfügen, V5
    anwenden → Zeilen unverändert (`text`), `quantity`/`unit` NULL, `meal_dishes.recipe_id`/`servings` NULL; Name
    `REQ-MEAL-062 ingredient lines from before V5 stay as names without quantity`.
  - `MealsRecipeApiTest` (Konstante `RECIPES = "/api/v1/meals/recipes"`, Helfer `recipeJson(name, servings, ingredients, steps, sourceUrl)`
    mit `buildJsonObject`, `createRecipe(token, body, invalid)`):
    - `REQ-MEAL-060 parent creates recipe and everyone sees it` (201; Kind `GET /recipes` → enthält Summary; `GET /recipes/{id}` → Zutaten mit Menge/Einheit, Schritte, URL).
    - `REQ-MEAL-060 child cannot create, change or delete recipes` (Kind POST/PUT/DELETE → 403).
    - `REQ-MEAL-060 recipe without name, ingredients or with invalid servings is rejected` (`"  "` → 400; `ingredients: []` → 400; `servings: 0`/`21` → 400).
    - `REQ-MEAL-060 duplicate recipe name ignoring case returns 409` (`"Quesadilla"` und `"quesadilla"`).
    - `REQ-MEAL-060 invalid source url is rejected` (`"ftp://x"`, `"kein link"` → 400; `https://www.rewe.de/rezepte/bunte-gemuese-quesadilla/` → 201).
    - `REQ-MEAL-060 search filters by name ignoring case` (drei Rezepte, `?query=CARBO` → nur Carbonara; leeres `query` → alle, alphabetisch).
    - `REQ-MEAL-060 changing or deleting a recipe keeps dishes taken from it` (Rezept anlegen; Elterngericht mit `recipeId`, `servings`,
      strukturierten Zutaten; Rezept ändern → Gericht im Plan unverändert; Rezept löschen → 204, Gericht unverändert, `recipeId == null`;
      `GET /recipes/{id}` → 404).
    - `REQ-MEAL-060 unknown recipe returns 404` (GET/PUT/DELETE mit zufälliger UUID; ungültige ID → 400).
    - `REQ-MEAL-062 child submits dish from recipe with structured ingredients` (Kind kocht — `makeCook` wie `MealsSubmissionApiTest`;
      `structuredIngredients` `[{name:"Vollkornspaghetti",quantity:625,unit:"g"},{name:"Salz"}]`, `recipeId`, `servings: 5` → 201,
      `status=SUBMITTED`, `ingredients == ["625 g Vollkornspaghetti","Salz"]`, `structuredIngredients` gleich, `servings == 5`).
    - `REQ-MEAL-062 dish with unknown recipe returns 404` und `… recipe without servings returns 400` (und `servings: 0` → 400).
    - `REQ-MEAL-062 lines and structured ingredients together are rejected` (400).
    - `REQ-MEAL-062 free-text dish still works for older apps` (nur `ingredients: ["Nudeln"]` → `structuredIngredients == [{name:"Nudeln"}]`, `recipeId == null`).
  - `MealsPlanApiTest`: `REQ-MEAL-032 parent plans dish on open day` zusätzlich `structuredIngredients` und `recipeId == null` prüfen.
- **MIRROR**: TEST_STRUCTURE (`MealsPlanApiTest`, `MealsSubmissionApiTest` für `makeCook`, `setupFamilyWithChild`).
- **ContractValidator erweitern** (`backend/app/src/test/kotlin/de/familyplanner/app/ContractValidator.kt:30-35`): `SimpleRequest.Builder(method, path)`
  bekommt bisher den ganzen Pfad; mit `?query=…` findet der Validator den Pfad nicht. In `report(...)` den Pfad an `?` trennen,
  Query mit `URLDecoder.decode(…, UTF_8)` zerlegen und je Paar `.withQueryParam(name, value)` setzen. Bestehende Tests bleiben
  unverändert (keine nutzt bisher Query-Parameter).
- **GOTCHA**: `FIXED_NOW` = Mi 14.10.2026 → Kind reicht für Woche `2026-10-19` ein. Feste Strings, kein `LocalDate.now()`.
  Mengen in JSON als Zahl (`625`), Vergleich der dekodierten `Double`-Werte (`625.0`). Query-Werte im Test URL-kodieren.
  `query` mit > 60 Zeichen verletzt das Schema → nur mit `invalid = true` testen (der Server antwortet trotzdem 400).
- **VALIDATE**: `./gradlew :app:test` grün.

### Task 10: Android-Testfixture an den Vertrag anpassen
- **ACTION**: `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/data/RemoteMealPlanRepositoryTest.kt:62-64`.
- **IMPLEMENT**: `dishJson` um `"structuredIngredients":[{"name":"Nudeln"},{"name":"Hackfleisch"}]` ergänzen. Sonst **keine**
  Android-Änderung (App-Seite ist 5b).
- **GOTCHA**: Generiertes `Dish` bekommt das Pflichtfeld ohne Default → ohne Ergänzung schlägt das Parsen im Test fehl.
  `SaveDishRequest` bekommt nur optionale Felder → `Mappers.kt:40` und Test Zeile 213 kompilieren unverändert.
  `DataStorePlanCacheTest` nutzt das Cache-Format, nicht das API-Modell — nicht anfassen.
- **VALIDATE**: `cd android && ./gradlew :core:api:compileDebugKotlin :feature:meals:testDebugUnitTest` grün.

### Task 11: Qualität, Coverage, Doku & Traceability
- **ACTION**: Statische Analyse, Coverage, Doku.
- **IMPLEMENT**:
  - `backend/README.md`: Modulzeile `meals` um „Rezepte“ ergänzen.
  - `traceability.md`: REQ-MEAL-060 → Design `[meals](../03-design/meals.md)`, `[ADR-0012](…)`; Unit `RecipeRulesTest`,
    `RecipeServiceTest`, `ExposedRecipeRepositoryTest`; Integration `MealsRecipeApiTest`; Status „Backend umgesetzt (App folgt in 5b)“.
    REQ-MEAL-062 → Unit `IngredientTest`, `MealPlanServiceTest`, `ExposedMealPlanRepositoryTest`, `MealsMigrationV5Test`;
    Integration `MealsRecipeApiTest`; Status „Backend-Anteil umgesetzt (Übernahme/Umrechnung in 5b)“.
  - `meals.prd.md`: Phase-5-Zeile Status `in-progress`, PRP-Spalte `5a: \`.claude/PRPs/plans/rezeptsammlung-backend.plan.md\``
    (bei Abschluss nach `completed/` verschieben, Report verlinken).
- **VALIDATE**: alle Validation Commands unten grün.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `IngredientTest` `REQ-MEAL-062 display text formats quantity unit and name` | `(500.0, "g", "Vollkornspaghetti")`, `(2.5, "Stange(n)", "Lauch")`, `(null, null, "Salz")`, `(4.0, null, "Tortilla-Wraps")`, `(10000.0, "g", "Mehl")` | `"500 g Vollkornspaghetti"`, `"2,5 Stange(n) Lauch"`, `"Salz"`, `"4 Tortilla-Wraps"`, `"10000 g Mehl"` | ja (kein `1E+4`) |
| `IngredientTest` `REQ-MEAL-062 lines become name-only ingredients` | `["  Nudeln ", "", "Salz"]` | `[Ingredient("Nudeln"), Ingredient("Salz")]` | ja |
| `IngredientTest` `REQ-MEAL-020 more than 100 raw lines are rejected` | 101 Zeilen | `Validation` | ja |
| `IngredientTest` `REQ-MEAL-062 ingredient is trimmed and blank unit becomes null` | `(" Lauch ", 2.0, "  ")` | `Ingredient("Lauch", 2.0, null)` | ja |
| `IngredientTest` `REQ-MEAL-062 invalid quantity is rejected` | `0.0`, `-1.0`, `10000.1`, `NaN`, `Infinity` | `Validation` | ja |
| `IngredientTest` `REQ-MEAL-062 too long name or unit is rejected` | Name 81, Einheit 21, Name `"  "` | `Validation` | ja |
| `IngredientTest` `REQ-MEAL-062 more than 30 ingredients are rejected` | 31 | `Validation` | ja |
| `RecipeRulesTest` `REQ-MEAL-060 servings must be between 1 and 20` | 0, 1, 20, 21 | 400 / ok / ok / 400 | ja |
| `RecipeRulesTest` `REQ-MEAL-060 steps are trimmed and blank steps removed` | `[" a ", "", "b"]` | `["a","b"]`; 31 Schritte → 400; Schritt 1001 → 400; 101 roh → 400 | ja |
| `RecipeRulesTest` `REQ-MEAL-060 source url must be http or https` | `"https://www.rewe.de/rezepte/x/"`, `"  "`, `null`, `"ftp://x"`, `"kein link"`, `"https://"`, 501 Zeichen | URL / `null` / `null` / 400 / 400 / 400 / 400 | ja |
| `RecipeRulesTest` `REQ-MEAL-060 search matches name ignoring case and sorts` | `["Quesadilla","carbonara","Äpfel-Crumble"]`, `"CARBO"`, `null`, `"äpfel"` | `[carbonara]`; alle alphabetisch (`äpfel…` nach `lowercase` — Reihenfolge laut `String.compareTo`, im Test fest erwartet); `[Äpfel-Crumble]` | ja |
| `RecipeRulesTest` `REQ-MEAL-060 too long query is rejected` | 61 Zeichen | `Validation` | ja |
| `RecipeServiceTest` `REQ-MEAL-060 parent creates recipe with validated content` | Name `" Carbonara "`, 4, 2 Zutaten | Name getrimmt, `createdBy` = Actor | |
| `RecipeServiceTest` `REQ-MEAL-060 recipe needs at least one ingredient` | `[]` | 400 | ja |
| `RecipeServiceTest` `REQ-MEAL-060 duplicate name returns conflict` | zweimal `"Carbonara"`/`"CARBONARA"` | 409 | ja |
| `RecipeServiceTest` `REQ-MEAL-060 update keeps creator and allows same name` | eigenes Rezept mit gleichem Namen ändern | ok, `createdBy` unverändert | ja |
| `RecipeServiceTest` `REQ-MEAL-060 update to name of another recipe returns conflict` | | 409 | ja |
| `RecipeServiceTest` `REQ-MEAL-060 unknown recipe returns not found` | get/update/delete | 404 | ja |
| `MealPlanServiceTest` `REQ-MEAL-062 dish keeps structured ingredients and recipe reference` | Rezept anlegen, Gericht mit `RecipeRef(id, 5)` | gespeichert mit `recipe` | |
| `MealPlanServiceTest` `REQ-MEAL-062 unknown recipe is not found` | `RecipeRef(RecipeId("x"), 4)` | 404 | ja |
| `MealPlanServiceTest` `REQ-MEAL-062 servings out of range are rejected before recipe lookup` | `servings = 0` mit unbekanntem Rezept | 400 (nicht 404) | ja |
| `MealPlanServiceTest` `REQ-MEAL-062 child may submit dish from recipe` | Kind, Rezeptbezug | `SUBMITTED`, `recipe` gesetzt | |
| `MealPlanServiceTest` `REQ-MEAL-022 update replaces recipe reference` | Gericht mit Bezug, Update ohne | `recipe == null` | ja |
| `ExposedMealPlanRepositoryTest` `REQ-MEAL-062 structured ingredients and recipe reference roundtrip` | Gericht mit Mengen/Einheiten und Rezept | identisch geladen, Reihenfolge erhalten | |
| `ExposedRecipeRepositoryTest` `REQ-MEAL-060 recipe roundtrip keeps ingredient and step order` | 3 Zutaten, 2 Schritte | identisch | |
| `ExposedRecipeRepositoryTest` `REQ-MEAL-060 name taken ignoring case` | create/update mit gleichem `nameKey` | `NAME_TAKEN`; eigenes Update `SAVED` | ja |
| `ExposedRecipeRepositoryTest` `REQ-MEAL-060 deleting recipe clears reference on dishes` | Gericht mit `recipe_id` | Gericht existiert, `recipe == null`, Zutaten unverändert | ja |
| `ExposedRecipeRepositoryTest` `REQ-MEAL-060 update of unknown recipe is not found` | | `NOT_FOUND` | ja |
| `MealsMigrationV5Test` | siehe Task 9 | | ja |

### Edge Cases Checklist
- [x] Leere Eingaben (Name, Zutaten, Schritte, Query, URL)
- [x] Maximalgrößen (30 Zutaten, 100 Rohzeilen, 30/100 Schritte, Längen)
- [x] Ungültige Typen/Formate (UUIDs, URL, Menge `NaN`/negativ)
- [x] Gleichzeitiger Zugriff: Namenseindeutigkeit atomar in einer `dbQuery` + Unique-Index
- [x] Netzwerkfehler: n/a (Backend ruft nichts Externes auf)
- [x] Keine Berechtigung (Kind schreibt Rezept → 403)
- [x] Bestandsdaten (Migration V5, alte App mit Zeilen)

---

## Validation Commands

### Static Analysis
```bash
cd backend && ./gradlew check
```
EXPECT: ktlint, detekt und Tests ohne Befunde (bei Formatbefunden `./gradlew ktlintFormat`)

### Unit Tests
```bash
cd backend && ./gradlew :meals:test
```
EXPECT: alle grün

### Full Test Suite + Coverage
```bash
cd backend && ./gradlew check koverVerify
```
EXPECT: keine Regressionen, Coverage ≥ 80 %

### API-Vertrag / Android-Client
```bash
cd android && ./gradlew :core:api:compileDebugKotlin :feature:meals:testDebugUnitTest
```
EXPECT: generierter Client kompiliert, App-Tests grün

### Image
```bash
cd backend && ./gradlew :app:buildFatJar && docker build -t family-planner:local .
```
EXPECT: Image baut (vorher Zeitstempel von `app/build/libs/app-all.jar` prüfen, siehe 3a-Report)

### Database Validation
```bash
cd backend && FP_SETUP_TOKEN=local-setup-token-123 ./gradlew :app:run
```
EXPECT: leere DB → „Successfully applied 5 migrations“; DB mit Stand Phase 4 → nur V5, bestehende Gerichte samt Zutaten unverändert

### Manual Validation
- [ ] Lokal (`curl -k`, Tokens aus Setup/Kopplung): Elternteil legt „Spaghetti mit Lauch-Carbonara“ (4 Portionen, „500 g Vollkornspaghetti“,
      „2 Stange(n) Lauch“, „Salz“) an → `GET /recipes?query=lauch` findet es; Kind-Token → `POST /recipes` 403.
- [ ] Kind reicht ein Gericht mit `recipeId`, `servings: 5` und strukturierten Zutaten ein → Plan zeigt `ingredients` als Anzeigetexte.
- [ ] Rezept löschen → Gericht bleibt, `recipeId: null`.
- [ ] Nach Merge + Deploy auf dem Pi: Container startet, Log zeigt Migration V5, Health ok; **App Stand 4b** zeigt Plan und
      Zutaten unverändert und kann weiter einreichen (alter Request mit Zeilen).

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt
- [ ] Alle Validation Commands grün
- [ ] Tests geschrieben (TDD) und grün, Coverage ≥ 80 %
- [ ] Keine ktlint-/detekt-Befunde
- [ ] Alte App (4b) funktioniert gegen das neue Backend (additiver Vertrag)

## Completion Checklist
- [ ] Code folgt den gefundenen Mustern (Outcomes, `dbQuery`, Mapper, Routen)
- [ ] Fehler nur über `AppException`, deutsche Meldungen
- [ ] Logging nur mit IDs
- [ ] Tests mit REQ-ID im Namen, AAA, Fakes statt Mocks
- [ ] Grenzen als `const val`
- [ ] Doku (Moduldesign, Traceability, PRD, README) aktualisiert
- [ ] Nichts aus „NOT Building“ umgesetzt
- [ ] Selbsterklärend — keine Rückfragen während der Umsetzung nötig

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Typänderung `Dish.ingredients` bricht viele Tests | H | M | Helfer `lines(...)`; Anzeigetext einer Zeile = ihr Name, daher bleiben API-Assertions auf `ingredients` gleich |
| Alte App bricht am neuen Vertrag | L | H | nur additive Felder, `ignoreUnknownKeys` auf beiden Seiten; Anzeigetexte in `ingredients` (maxLength 120 gilt nur für Antworten) |
| Rezept wird zwischen Existenzprüfung und Insert gelöscht → FK-Verletzung (500) | L | L | extrem selten bei 2 Eltern; dokumentiert; bei Bedarf Prüfung ins Repository ziehen |
| Gleitkomma-Mengen (`REAL`) | L | L | Werte sind Haushaltsmengen; Anzeige mit `stripTrailingZeros`; Summen erst in Phase 6 |
| Detekt `TooManyFunctions`/`LongParameterList` | M | L | Rezeptlogik in eigenen Dateien (`RecipeRules.kt`, `RecipeService.kt`); `MealPlanService.update` hat 7 Parameter (< 8) |
| Parallele Migration V5 aus anderem Branch | L | M | vor dem Merge Migrationsordner prüfen |

## Notes
- **Warum `servings` am Gericht nach Rezept-Löschung wegfällt:** `RecipeRef` modelliert „übernommen aus Rezept X für N Portionen“.
  Ohne Rezept hat die Portionenzahl keinen Nutzen mehr (die Mengen stehen schon im Gericht). Die Spalte bleibt in der DB, die API
  liefert `servings: null`. Einfacher als ein drittes Feld.
- **Warum die Rollenprüfung für Rezepte in der Route liegt:** wie `DELETE /dishes` (`PlanRoutes.kt:43-47`); der Service hat keine
  rollenabhängige Fachlogik. Unit-Tests decken Regeln ab, `MealsRecipeApiTest` die 403.
- **Portionen-Vorschlag** (Anwesende des Tages) berechnet die App in 5b aus `GET /members` (Eltern immer anwesend) und
  `GET …/attendance` (Kinder je Tag); der Server liefert dafür nichts Neues.
- **`ingredients: string[]` entfernen** erst, wenn alle Familienhandys mindestens 5b haben — eigener kleiner Schritt nach Phase 6.
- Umsetzung laut CLAUDE.md in einer **neuen Session** mit `/ecc:prp-implement .claude/PRPs/plans/rezeptsammlung-backend.plan.md`.
