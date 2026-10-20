# Plan: Einkaufsliste – Backend (Phase 6a)

## Summary

Das Backend liefert je Planwoche eine **Einkaufsliste**, die aus den Zutaten der **freigegebenen** Gerichte berechnet wird:
Zutaten mit gleichem Namen und gleicher Einheit werden zu einem Eintrag mit addierter Menge zusammengeführt, je Eintrag
mit Herkunft (Gerichte). Eltern ergänzen **freie Einträge** und **haken ab**. Die Liste selbst wird nicht gespeichert —
gespeichert werden nur freie Einträge und Häkchen. Oberfläche und Offline-Anzeige folgen in Phase 6b (App).

## User Story

Als Elternteil möchte ich eine Einkaufsliste mit den zusammengeführten Zutaten aller freigegebenen Gerichte der Woche,
die ich ergänzen und abhaken kann, damit ich bei Picnic ohne Nachrechnen und ohne etwas zu vergessen bestelle.

## Problem → Solution

Zutaten stehen verstreut an den einzelnen Gerichten; zum Bestellen muss man sie abschreiben und addieren →
`GET …/weeks/{weekStart}/shopping-list` liefert die fertige, zusammengeführte Liste mit Herkunft, freien Einträgen und
Häkchen; Schreiben (Eintrag hinzufügen/löschen, abhaken) nur Eltern.

## Metadata

- **Complexity**: Medium
- **Source PRD**: `docs/01-requirements/meals.prd.md`
- **PRD Phase**: 6 Einkaufsliste — Teil 6a Backend/API (6b App folgt als eigener Plan)
- **Anforderungen**: REQ-MEAL-040, REQ-MEAL-041 (Server-Anteil)
- **Estimated Files**: ~17 (≈ 10 neu, ≈ 7 geändert)

---

## UX Design

N/A — Backend/API. Die App zeigt die Liste erst mit 6b.

### Interaction Changes

| Touchpoint | Before | After | Notes |
|---|---|---|---|
| `GET /api/v1/meals/weeks/{weekStart}/shopping-list` | – | zusammengeführte Liste + freie Einträge + Häkchen | lesen alle Mitglieder |
| `POST …/shopping-list/items` | – | freien Eintrag hinzufügen → 201 + ganze Liste | nur Eltern |
| `DELETE …/shopping-list/items/{itemId}` | – | freien Eintrag löschen → 204 | nur Eltern |
| `PUT …/shopping-list/checks` | – | Eintrag ab-/anhaken → 200 + ganze Liste | nur Eltern |

Bestehende Endpunkte bleiben unverändert (rein additive API-Erweiterung).

---

## Fachliche Entscheidungen (verbindlich für die Umsetzung)

Diese Punkte präzisieren REQ-MEAL-040/041; sie werden in Task 1 in `docs/03-design/meals.md` übernommen.

| Thema | Regel | Begründung |
|---|---|---|
| Quelle | Nur Gerichte mit Status `APPROVED`, deren Datum in der Planwoche liegt. `SUBMITTED`/`REJECTED` fehlen; abgesagte Gerichte sind ohnehin gelöscht (Phase 4). Gerichte mit `dayValid = false` zählen mit (sie sind freigegeben). | REQ-MEAL-040 „wartet auf Freigabe → nicht enthalten“; REQ-MEAL-023 |
| Zusammenführungs-Schlüssel | `(nameKey(name), nameKey(unit ?: ""), hatMenge)` — `nameKey` = `lowercase(Locale.ROOT)` aus `recipe/RecipeRules.kt` (Namen sind bereits getrimmt gespeichert) | PRD: „gleicher Name und gleiche Einheit“; Groß-/Kleinschreibung soll nicht trennen („Lauch“/„lauch“) |
| Menge | Mit Menge: Summe (BigDecimal, auf 3 Nachkommastellen gerundet). Ohne Menge: ein Eintrag je Name+Einheit, ohne Menge. „2 Zwiebeln“ (Menge, keine Einheit) und „Zwiebeln“ (ohne Menge) bleiben **getrennt** | REQ-MEAL-040 Abnahme 2; eine erfundene Summe wäre falsch |
| Verschiedene Einheiten | getrennte Einträge („200 g Sahne“, „1 Becher Sahne“); keine Umrechnung | REQ-MEAL-040 Abnahme 3; Umrechnung ist „Later“ |
| Anzeige-Name/-Einheit | Schreibweise des ersten Vorkommens (Gerichte nach Datum, Zutaten in Eingabereihenfolge) | deterministisch |
| Herkunft | je Eintrag die beitragenden Gerichte (`dishId`, `dishName`, `date`), jedes Gericht höchstens einmal, nach Datum sortiert. Steht eine Zutat in einem Gericht doppelt, werden beide Mengen addiert | REQ-MEAL-040 Abnahme 1 |
| Reihenfolge | Erst berechnete Einträge, sortiert nach `nameKey(name)`, dann `nameKey(unit)`, dann ohne-Menge vor mit-Menge; danach freie Einträge in Anlagereihenfolge | stabil, gut lesbar |
| Eintrags-Schlüssel `key` | berechnet: `"ing:" + nameKey(name) + "|" + nameKey(unit ?: "") + "|" + ("q" bei Menge, sonst "-")`; frei: `"item:" + itemId`. Max. 120 Zeichen (80 + 20 + Präfixe = 107) | Häkchen brauchen eine stabile ID für berechnete Einträge |
| Häkchen an berechneten Einträgen | gespeichert als `(week, key, checkedQuantity)`. Ein Eintrag gilt nur dann als abgehakt, wenn seine **aktuelle** Menge gleich `checkedQuantity` ist (beide `null` zählt als gleich). Kommt nach dem Abhaken Menge dazu (z. B. weiteres Gericht freigegeben), erscheint der Eintrag wieder offen | „Bestellung ohne Nachrechnen“: nachträgliche Mengen dürfen nicht unter einem alten Häkchen verschwinden |
| Häkchen an freien Einträgen | Spalte `checked` am freien Eintrag | einfach |
| Häkchen entfernen | `checked=false` löscht den Häkchen-Datensatz bzw. setzt `checked = false` | |
| Unbekannter `key` | 404 „Eintrag nicht gefunden“ (Schlüssel nicht in der aktuellen Liste) | |
| Veraltete Häkchen | Häkchen-Datensätze, deren Schlüssel nicht mehr vorkommt, bleiben liegen und sind wirkungslos; erscheint der Schlüssel mit derselben Menge wieder, ist er wieder abgehakt | KISS, keine Aufräumlogik |
| Freier Eintrag | `Ingredient`-Struktur (Name Pflicht, Menge/Einheit optional), gleiche Validierung wie Zutaten (`validatedIngredients`); höchstens **50** freie Einträge je Woche (sonst 400); wird **nicht** mit berechneten Einträgen zusammengeführt | REQ-MEAL-041; Struktur hilft der späteren Picnic-Übergabe |
| Freien Eintrag löschen | nur innerhalb der angegebenen Woche; fremde Woche oder unbekannt → 404 | |
| Rechte | `GET` alle Mitglieder (wie Plan/Status); `POST`/`PUT`/`DELETE` nur Eltern (403 „Nur für Eltern“ via `requireParent`) | REQ-MEAL-040/041 sind Eltern-Stories; Mitlesen schadet nicht |
| Woche | `weekStart` muss ein Montag sein (`PlanWeek`-init → 400); jede Woche erlaubt | wie Plan/Status |
| Logging | nur IDs/Woche: `"Shopping item added: {} {}"`, `"Shopping item deleted: {}"`, `"Shopping entry checked: {} {}"` (Woche, checked) — keine Namen/Schlüssel (Schlüssel enthalten Zutatennamen) | wie bisher |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `docs/01-requirements/meals.prd.md` | 293-313, 94-110 | REQ-MEAL-040/041, Fachliche Regeln |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/Ingredient.kt` | all | `Ingredient`, `validatedIngredients`, Rundung (`QUANTITY_SCALE`) |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/Plan.kt` | all | `Dish`, `DishStatus`, `weekPlanOf` (Muster reine Funktion) |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/recipe/RecipeService.kt`, `RecipeRules.kt` | all | Service-Muster, `nameKey` |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedRecipeRepository.kt` | all | Repository-Muster in einer `dbQuery` |
| P0 | `api/openapi.yaml` | 323-395 (Wochen-Pfade), 597-670 (parameters/responses), 1115-1121 (`Ingredient`), 1289-1315 (Envelope) | Vertrag erweitern |
| P1 | `backend/meals/src/main/kotlin/de/familyplanner/meals/web/CookingRoutes.kt`, `RecipeRoutes.kt`, `CallParameters.kt`, `RecipeApiMappers.kt` | all | Routen, `weekParameter()`, `Ingredient.toApi()/toDomain()` |
| P1 | `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/PlanTables.kt` | all | Tabellen-Stil (Längen als private consts) |
| P1 | `backend/meals/src/main/resources/db/migration/V5__meals_recipes.sql` | all | Migrationsstil |
| P1 | `backend/meals/src/main/kotlin/de/familyplanner/meals/MealsModule.kt`, `backend/app/src/main/kotlin/de/familyplanner/app/Application.kt` | 21-33 / 75-99 | Koin-/Routen-Verdrahtung |
| P1 | `backend/meals/src/test/kotlin/de/familyplanner/meals/fakes/PlanFixture.kt`, `InMemoryRecipeRepository.kt` | all | Fakes |
| P1 | `backend/meals/src/test/kotlin/de/familyplanner/meals/persistence/ExposedRecipeRepositoryTest.kt` | 1-60 | Persistenztest gegen echte SQLite |
| P1 | `backend/app/src/test/kotlin/de/familyplanner/app/ApiTestSupport.kt`, `MealsRecipeApiTest.kt` | all | API-/Contract-Tests, `setupFamilyWithChild`, `makeCook` |
| P2 | `.claude/PRPs/plans/completed/rezeptsammlung-backend.plan.md` | all | Vorgänger-Plan, gleiche Konventionen |
| P2 | `docs/03-design/meals.md` | 139-176 | Abschnittsstil für Task 1 |

## External Documentation

Keine externe Recherche nötig — ausschließlich bestehende interne Muster (Ktor, Exposed v1, Flyway, SQLite).
`java.math.BigDecimal` für die Summen, damit `0.1 + 0.2` nicht `0.30000000000000004` ergibt.

---

## Patterns to Mirror

### NAMING_CONVENTION
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/plan/Plan.kt:15-36
@JvmInline
value class DishId(
    val value: String,
)

data class Dish(
    val id: DishId,
    val date: LocalDate,
    val cookId: MemberId,
    val name: String,
    val ingredients: List<Ingredient>,
    val status: DishStatus = DishStatus.APPROVED,
    ...
)
```
Grenzwerte als `const val` oben in der Datei (`Ingredient.kt:8-16`). Fehlermeldungen deutsch, Code/Kommentare englisch.
Neues Paket `de.familyplanner.meals.shopping` (Domäne + Service); Persistenz in `meals.persistence`, Web in `meals.web`.

### PURE_DOMAIN_FUNCTION
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/plan/Plan.kt:70-78
/** Seven days Monday..Sunday; days without a dish stay open. Dishes outside [week] are ignored. */
fun weekPlanOf(
    week: PlanWeek,
    dishes: List<Dish>,
    isDayValid: (Dish) -> Boolean = { true },
): WeekPlan { ... }
```
Die Zusammenführung wird genauso eine reine Top-Level-Funktion `shoppingListOf(...)` — ohne Fakes testbar.

### ERROR_HANDLING
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/recipe/RecipeService.kt:54-57,72-80
suspend fun delete(id: RecipeId) {
    if (!recipes.delete(id)) throw AppException.NotFound(RECIPE_NOT_FOUND)
    log.info("Recipe deleted: {}", id.value)
}
private fun requireSaved(outcome: RecipeSaveOutcome) {
    val error =
        when (outcome) {
            RecipeSaveOutcome.SAVED -> return
            RecipeSaveOutcome.NAME_TAKEN -> AppException.Conflict("Ein Rezept mit diesem Namen gibt es schon")
            RecipeSaveOutcome.NOT_FOUND -> AppException.NotFound(RECIPE_NOT_FOUND)
        }
    throw error
}
```
Nur `AppException` (`Validation` 400, `Forbidden` 403, `NotFound` 404, `Conflict` 409); `StatusPages` baut das Envelope.

### LOGGING_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/recipe/RecipeService.kt:27,39
private val log = LoggerFactory.getLogger(RecipeService::class.java)
log.info("Recipe created: {}", recipe.id.value)
```

### REPOSITORY_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedRecipeRepository.kt:44-62
override suspend fun createIfNameFree(recipe: Recipe, at: Instant): RecipeSaveOutcome =
    dbQuery(db) {
        if (nameTaken(recipe)) return@dbQuery RecipeSaveOutcome.NAME_TAKEN
        MealRecipesTable.insert {
            it[id] = recipe.id.value
            ...
            it[createdAt] = at.toEpochMilli()
        }
        insertContent(recipe)
        RecipeSaveOutcome.SAVED
    }
```
Prüfen und Schreiben in **einer** `dbQuery` (Pool-Größe 1 — verschachtelte `dbQuery` = Deadlock). Zeitstempel als
`Long` epoch millis. Mapper `private fun ResultRow.toX()` bzw. am Dateiende.

### TABLE_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/PlanTables.kt:33-48
object MealCookingCancellationsTable : Table("meal_cooking_cancellations") {
    val memberId = varchar("member_id", ID_LENGTH)
    val weekStart = varchar("week_start", DATE_LENGTH)
    val reason = varchar("reason", REASON_LENGTH)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(memberId, weekStart)
}
private const val ID_LENGTH = 36
private const val DATE_LENGTH = 10
```

### SERVICE_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/recipe/RecipeService.kt:22-41
class RecipeService(
    private val recipes: RecipeRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) { ... }
```

### ROUTE_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/web/CookingRoutes.kt:26-57 / RecipeRoutes.kt:24-50
fun Route.cookingRoutes(reviews: DishReviewService, cooking: CookingWeekService) {
    authenticate(AUTH_DEVICE) {
        get("/api/v1/meals/weeks/{weekStart}/status") {
            call.principalOrThrow()
            call.respond(WeekStatusResponse(success = true, data = cooking.status(call.weekParameter()).toApi()))
        }
        delete("/api/v1/meals/recipes/{recipeId}") {
            call.requireParent()
            recipes.delete(call.recipeId())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
```
API-Modelle per Import-Alias bei Namenskollision (`import de.familyplanner.api.model.Ingredient as ApiIngredient`).

### KOIN_AND_WIRING
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/MealsModule.kt:27-32 ; app/Application.kt:94-97
single<RecipeRepository> { ExposedRecipeRepository(get()) }
single { RecipeService(get(), clock) }
routing { … recipeRoutes(get<RecipeService>()) }
```

### TEST_STRUCTURE
```kotlin
// SOURCE: backend/meals/src/test/kotlin/de/familyplanner/meals/fakes/PlanFixture.kt:19-45
class PlanFixture(start: Instant = Instant.parse("2026-10-14T10:00:00Z")) {
    val clock = MutableClock(start)
    val members = InMemoryMemberRepository()
    val planRepository = InMemoryMealPlanRepository()
    ...
    suspend fun parent(): Actor = Actor(members.create(aMember(role = Role.PARENT)).id, Role.PARENT)
}
// SOURCE: backend/app/src/test/kotlin/de/familyplanner/app/MealsRecipeApiTest.kt:120-138
@Test
fun `REQ-MEAL-060 parent creates recipe and everyone sees it`() =
    testApplication {
        start()
        val family = setupFamilyWithChild()
        val created = client.createRecipe(family.parentToken, recipeJson(sourceUrl = REWE_URL))
        ...
        assertEquals(HttpStatusCode.Created, created.status)
    }
```
kotlin.test + JUnit 5, `runTest`, Fakes statt Mocks, AAA-Kommentare, Testname beginnt mit REQ-ID. `client.call(...)`
prüft Request **und** Response gegen `openapi.yaml`. `FIXED_NOW` = Mi 14.10.2026 → Planwoche ab Mo 2026-10-19.

---

## Domänenmodell (verbindlich)

```kotlin
// shopping/ShoppingList.kt
package de.familyplanner.meals.shopping

const val SHOPPING_ITEMS_MAX_COUNT = 50
const val ENTRY_KEY_MAX = 120

@JvmInline value class ShoppingItemId(val value: String)

/** A free entry a parent added to the week's list. */
data class ShoppingItem(
    val id: ShoppingItemId,
    val week: PlanWeek,
    val ingredient: Ingredient,
    val checked: Boolean = false,
)

/** A stored tick for a computed entry; only valid while the entry still has [quantity]. */
data class ShoppingCheck(
    val key: String,
    val quantity: Double?,
)

data class EntrySource(
    val dishId: DishId,
    val dishName: String,
    val date: LocalDate,
)

data class ShoppingEntry(
    val key: String,
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val checked: Boolean,
    val itemId: ShoppingItemId? = null,   // set for free entries
    val sources: List<EntrySource> = emptyList(),
)

data class ShoppingList(
    val week: PlanWeek,
    val entries: List<ShoppingEntry>,
)

/** Merges the ingredients of the approved dishes in [week], then appends [items]; see docs/03-design/meals.md. */
fun shoppingListOf(
    week: PlanWeek,
    dishes: List<Dish>,
    items: List<ShoppingItem>,
    checks: List<ShoppingCheck>,
): ShoppingList
```

```kotlin
// shopping/ShoppingListRepository.kt
interface ShoppingListRepository {
    /** Free entries of [week] in creation order. */
    suspend fun findItems(week: PlanWeek): List<ShoppingItem>

    suspend fun findChecks(week: PlanWeek): List<ShoppingCheck>

    /** Atomically inserts unless [week] already has [max] items; false if the limit is reached. */
    suspend fun addItemIfBelowLimit(item: ShoppingItem, max: Int, at: Instant): Boolean

    /** Deletes the item only if it belongs to [week]. */
    suspend fun deleteItem(week: PlanWeek, id: ShoppingItemId): Boolean

    suspend fun setItemChecked(week: PlanWeek, id: ShoppingItemId, checked: Boolean): Boolean

    /** Stores (or replaces) the tick of a computed entry. */
    suspend fun saveCheck(week: PlanWeek, check: ShoppingCheck, at: Instant)

    suspend fun deleteCheck(week: PlanWeek, key: String)
}
```

```kotlin
// shopping/ShoppingListService.kt
class ShoppingListService(
    private val plan: MealPlanRepository,
    private val shopping: ShoppingListRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun list(week: PlanWeek): ShoppingList
    suspend fun addItem(week: PlanWeek, ingredient: Ingredient): ShoppingList
    suspend fun deleteItem(week: PlanWeek, id: ShoppingItemId)
    suspend fun setChecked(week: PlanWeek, key: String, checked: Boolean): ShoppingList
}
```
Rollenprüfung (`requireParent`) passiert in den Routen — wie bei Rezepten; der Service braucht keinen `Actor`.

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `docs/03-design/meals.md` | UPDATE | neuer Abschnitt „Einkaufsliste (Phase 6a)“ mit den Regeln oben |
| `api/openapi.yaml` | UPDATE | 4 Operationen, Parameter `ShoppingItemId`, Schemas `ShoppingList*` |
| `backend/meals/src/main/resources/db/migration/V6__meals_shopping.sql` | CREATE | Tabellen `meal_shopping_items`, `meal_shopping_checks` |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/shopping/ShoppingList.kt` | CREATE | Domänentypen + `shoppingListOf` |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/shopping/ShoppingListRepository.kt` | CREATE | Repository-Interface |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/shopping/ShoppingListService.kt` | CREATE | Anwendungslogik |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ShoppingTables.kt` | CREATE | Exposed-Tabellen |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedShoppingListRepository.kt` | CREATE | SQLite-Implementierung |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/ShoppingRoutes.kt` | CREATE | Routen |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/ShoppingApiMappers.kt` | CREATE | Domäne → API |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/MealsModule.kt` | UPDATE | Koin |
| `backend/app/src/main/kotlin/de/familyplanner/app/Application.kt` | UPDATE | `shoppingRoutes(get<ShoppingListService>())` |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/shopping/ShoppingListTest.kt` | CREATE | Unit-Tests der Zusammenführung |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/shopping/ShoppingListServiceTest.kt` | CREATE | Service-Tests mit Fakes |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/fakes/InMemoryShoppingListRepository.kt` | CREATE | Fake |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/fakes/PlanFixture.kt` | UPDATE | `shoppingRepository`, `shopping` ergänzen |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/persistence/ExposedShoppingListRepositoryTest.kt` | CREATE | Persistenz gegen echte SQLite |
| `backend/app/src/test/kotlin/de/familyplanner/app/MealsShoppingApiTest.kt` | CREATE | Integration/Contract |
| `docs/04-verification/traceability.md` | UPDATE | REQ-MEAL-040/041 |
| `docs/01-requirements/meals.prd.md` | UPDATE | (bereits durch Planerstellung: Phase 6 `in-progress`) — nach Umsetzung Report-Pfad ergänzen |

## NOT Building

- App-Oberfläche, Navigation, Offline-Cache der Liste → Phase 6b.
- Einheiten-Umrechnung (g/kg, ml/l), Normalisierung von Singular/Plural („Zwiebel“/„Zwiebeln“) → „Later“.
- Umrechnung auf Esser / Portionen auf der Liste — Mengen stehen bereits umgerechnet am Gericht (Phase 5).
- Picnic-Übergabe über Home Assistant → „Later“.
- Ändern freier Einträge (nur hinzufügen/löschen/abhaken; Korrektur = löschen + neu).
- Ausblenden einzelner berechneter Einträge („haben wir noch“) — Abhaken reicht.
- Aufräumen veralteter Häkchen-Datensätze.
- Änderungen an Wochenstatus/„bereit zur Bestellung“.

---

## Step-by-Step Tasks

### Task 1: Moduldesign ergänzen
- **ACTION**: In `docs/03-design/meals.md` nach „Rezepte & strukturierte Zutaten (Phase 5a)“ (vor „App-Seite (Phase 2b)“) einen
  Abschnitt `## Einkaufsliste (Phase 6a)` einfügen; Überschrift Zeile 1 um „Einkaufsliste“ ergänzen; in „Abgedeckte
  Anforderungen“ REQ-MEAL-040/041 aufnehmen.
- **IMPLEMENT**: Begriffstabelle (`ShoppingEntry`, `key`, `ShoppingItem`, `ShoppingCheck`, Herkunft) + Regeltabelle
  = Tabelle „Fachliche Entscheidungen“ dieses Plans (ohne Spalte Begründung), Endpunkte, Tabellen, Testklassen.
- **MIRROR**: Stil von Abschnitt „Rezepte & strukturierte Zutaten (Phase 5a)“ (Zeilen 139-176).
- **GOTCHA**: Deutsche Fachsprache, keine Codeblöcke nötig.
- **VALIDATE**: Abschnitt deckt jede Zeile der Entscheidungstabelle ab.

### Task 2: API-Vertrag (contract-first, ADR-0004)
- **ACTION**: `api/openapi.yaml` erweitern — Pfade direkt nach `/api/v1/meals/weeks/{weekStart}/cancellations/{memberId}`
  einfügen, Schemas nach `WeekStatusResponse` anhängen, Parameter nach `RecipeId`.
- **IMPLEMENT**:
```yaml
  /api/v1/meals/weeks/{weekStart}/shopping-list:
    get:
      operationId: getShoppingList
      tags: [meals]
      summary: Einkaufsliste der Planwoche (freigegebene Gerichte zusammengeführt, freie Einträge, Häkchen)
      parameters:
        - $ref: '#/components/parameters/WeekStart'
      responses:
        '200':
          description: Einkaufsliste
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ShoppingListResponse' }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/Unauthorized' }
        '429': { $ref: '#/components/responses/RateLimited' }

  /api/v1/meals/weeks/{weekStart}/shopping-list/items:
    post:
      operationId: addShoppingItem
      tags: [meals]
      summary: Freien Eintrag hinzufügen (nur Eltern)
      parameters:
        - $ref: '#/components/parameters/WeekStart'
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/Ingredient' }
      responses:
        '201':
          description: Eintrag hinzugefügt, aktuelle Liste
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ShoppingListResponse' }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/Unauthorized' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '429': { $ref: '#/components/responses/RateLimited' }

  /api/v1/meals/weeks/{weekStart}/shopping-list/items/{itemId}:
    delete:
      operationId: deleteShoppingItem
      tags: [meals]
      summary: Freien Eintrag löschen (nur Eltern)
      parameters:
        - $ref: '#/components/parameters/WeekStart'
        - $ref: '#/components/parameters/ShoppingItemId'
      responses:
        '204':
          description: Eintrag gelöscht
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/Unauthorized' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '429': { $ref: '#/components/responses/RateLimited' }

  /api/v1/meals/weeks/{weekStart}/shopping-list/checks:
    put:
      operationId: setShoppingEntryChecked
      tags: [meals]
      summary: Eintrag ab- oder anhaken (nur Eltern)
      parameters:
        - $ref: '#/components/parameters/WeekStart'
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/CheckShoppingEntryRequest' }
      responses:
        '200':
          description: Häkchen gespeichert, aktuelle Liste
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ShoppingListResponse' }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/Unauthorized' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '429': { $ref: '#/components/responses/RateLimited' }
```
  Parameter:
```yaml
    ShoppingItemId:
      name: itemId
      in: path
      required: true
      schema: { type: string, format: uuid }
```
  Schemas:
```yaml
    ShoppingEntrySource:
      type: object
      required: [dishId, dishName, date]
      properties:
        dishId: { type: string, format: uuid }
        dishName: { type: string, minLength: 1, maxLength: 60 }
        date: { type: string, format: date }

    ShoppingEntry:
      type: object
      required: [key, name, checked, sources]
      properties:
        key: { type: string, minLength: 1, maxLength: 120, description: "Stabiler Schlüssel zum Abhaken" }
        name: { type: string, minLength: 1, maxLength: 80 }
        quantity: { type: number, format: double, nullable: true, description: "Summe; kann 10000 übersteigen" }
        unit: { type: string, maxLength: 20, nullable: true }
        checked: { type: boolean }
        itemId: { type: string, format: uuid, nullable: true, description: "Gesetzt bei freien Einträgen" }
        sources: { type: array, items: { $ref: '#/components/schemas/ShoppingEntrySource' } }

    ShoppingList:
      type: object
      required: [weekStart, entries]
      properties:
        weekStart: { type: string, format: date }
        entries: { type: array, items: { $ref: '#/components/schemas/ShoppingEntry' } }

    CheckShoppingEntryRequest:
      type: object
      required: [key, checked]
      properties:
        key: { type: string, minLength: 1, maxLength: 120 }
        checked: { type: boolean }

    ShoppingListResponse:
      type: object
      required: [success]
      properties:
        success: { type: boolean }
        data:
          allOf:
            - $ref: '#/components/schemas/ShoppingList'
          nullable: true
        error:
          allOf:
            - $ref: '#/components/schemas/ApiError'
          nullable: true
```
- **MIRROR**: Pfad-/Envelope-Stil von `getWeekStatus` / `WeekStatusResponse`.
- **GOTCHA**: `quantity` an `ShoppingEntry` **ohne** `maximum` — eine Summe kann 10000 überschreiten (sonst schlägt der
  Contract-Test fehl). Das Request-Schema für freie Einträge ist das bestehende `Ingredient` (mit min/max).
  Der Generator erzeugt `ShoppingEntry`, `ShoppingList` etc. in `de.familyplanner.api.model` — Namenskollision mit den
  Domänentypen → im Web-Paket Import-Alias (`as ApiShoppingList`).
- **VALIDATE**: `cd backend && ./gradlew :api-contract:compileKotlin`; `cd android && ./gradlew :core:api:compileDebugKotlin`
  (App-Client wird ebenfalls generiert und muss kompilieren).

### Task 3: Migration V6
- **ACTION**: `backend/meals/src/main/resources/db/migration/V6__meals_shopping.sql` anlegen.
- **IMPLEMENT**:
```sql
-- Phase 6: shopping list. The merged list is computed from approved dishes; only free entries and ticks are stored.
CREATE TABLE meal_shopping_items (
  id TEXT PRIMARY KEY,
  week_start TEXT NOT NULL,
  name TEXT NOT NULL,
  quantity REAL,
  unit TEXT,
  checked INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);

CREATE INDEX idx_meal_shopping_items_week ON meal_shopping_items(week_start, created_at);

-- Tick of a computed entry; it only counts while the entry still has checked_quantity (NULL = entry without quantity).
CREATE TABLE meal_shopping_checks (
  week_start TEXT NOT NULL,
  entry_key TEXT NOT NULL,
  checked_quantity REAL,
  checked_at INTEGER NOT NULL,
  PRIMARY KEY (week_start, entry_key)
);
```
- **MIRROR**: `V5__meals_recipes.sql` (Kommentarstil `--`, `TEXT`-IDs, epoch millis als `INTEGER`).
- **GOTCHA**: Kein `created_by` — wer eingetragen hat, ist nicht gefordert (YAGNI). Keine Fremdschlüssel auf Gerichte: die
  Liste ist berechnet. Anlagereihenfolge bei gleichem `created_at`: zusätzlich nach `id` sortieren (siehe Task 5).
- **VALIDATE**: `./gradlew :meals:test` — `DatabaseFactory.create` (Flyway) läuft in Task 5 durch.

### Task 4: Domäne – `shoppingListOf` (TDD)
- **ACTION**: Erst `ShoppingListTest.kt` (RED), dann `shopping/ShoppingList.kt` (GREEN).
- **IMPLEMENT**: Typen aus „Domänenmodell“. Algorithmus:
  1. `approved = dishes.filter { it.status == APPROVED && it.date in week.days }.sortedBy { it.date }`
  2. Für jede Zutat jedes Gerichts (in Reihenfolge): `key = entryKey(ingredient)`; in einer `LinkedHashMap<String, Acc>`
     sammeln: erste Schreibweise von Name/Einheit, `BigDecimal`-Summe (nur bei Menge), Quellen als `LinkedHashMap<DishId, EntrySource>`.
  3. Berechnete Einträge sortieren: `compareBy({ nameKey(it.name) }, { nameKey(it.unit.orEmpty()) }, { it.quantity != null })`.
  4. `checked` = `checks.any { it.key == entry.key && sameQuantity(it.quantity, entry.quantity) }`, wobei
     `sameQuantity` beide Werte mit `BigDecimal.valueOf(x).setScale(3, HALF_UP)` vergleicht (`compareTo == 0`), `null == null` → true.
  5. Freie Einträge anhängen: `key = "item:" + id`, `itemId = id`, `checked = item.checked`, `sources = emptyList()`.
  ```kotlin
  internal fun entryKey(ingredient: Ingredient): String =
      "ing:" + nameKey(ingredient.name) + "|" + nameKey(ingredient.unit.orEmpty()) + "|" + if (ingredient.quantity != null) "q" else "-"
  ```
  Hilfsfunktionen privat halten; jede Funktion < 50 Zeilen (detekt).
- **MIRROR**: PURE_DOMAIN_FUNCTION; Rundung wie `roundedQuantity` in `Ingredient.kt:63` (Konstante dort ist privat →
  eigene `private const val SUM_SCALE = 3` mit Kommentar „matches ingredient precision“).
- **IMPORTS**: `de.familyplanner.meals.attendance.PlanWeek`, `de.familyplanner.meals.plan.{Dish, DishId, DishStatus, Ingredient}`,
  `de.familyplanner.meals.recipe.nameKey`, `java.math.BigDecimal`, `java.math.RoundingMode`, `java.time.LocalDate`.
- **GOTCHA**: Summe als `BigDecimal` bilden und erst am Ende `.setScale(3, HALF_UP).toDouble()`; `0.1 + 0.2` muss `0.3` ergeben.
  Gerichte außerhalb der Woche ignorieren (das Repository liefert zwar nur die Woche, die Funktion soll trotzdem robust sein).
- **VALIDATE**: `./gradlew :meals:test --tests '*ShoppingListTest*'` grün; Testfälle siehe „Testing Strategy“.

### Task 5: Repository (Interface, Fake, Exposed) (TDD)
- **ACTION**: `ShoppingListRepository.kt`, `fakes/InMemoryShoppingListRepository.kt`, `persistence/ShoppingTables.kt`,
  `persistence/ExposedShoppingListRepository.kt`; Test `ExposedShoppingListRepositoryTest.kt` zuerst.
- **IMPLEMENT**:
  - Tabellen:
    ```kotlin
    object MealShoppingItemsTable : Table("meal_shopping_items") {
        val id = varchar("id", ID_LENGTH)
        val weekStart = varchar("week_start", DATE_LENGTH)
        val name = varchar("name", INGREDIENT_LENGTH)
        val quantity = double("quantity").nullable()
        val unit = varchar("unit", UNIT_LENGTH).nullable()
        val checked = bool("checked")
        val createdAt = long("created_at")
        override val primaryKey = PrimaryKey(id)
    }
    object MealShoppingChecksTable : Table("meal_shopping_checks") {
        val weekStart = varchar("week_start", DATE_LENGTH)
        val entryKey = varchar("entry_key", ENTRY_KEY_LENGTH)
        val checkedQuantity = double("checked_quantity").nullable()
        val checkedAt = long("checked_at")
        override val primaryKey = PrimaryKey(weekStart, entryKey)
    }
    // private consts: ID_LENGTH = 36, DATE_LENGTH = 10, INGREDIENT_LENGTH = 80, UNIT_LENGTH = 20, ENTRY_KEY_LENGTH = 120
    ```
  - `findItems`: `where weekStart eq week.monday.toString()`, `orderBy(createdAt, id)`.
  - `addItemIfBelowLimit`: in **einer** `dbQuery`: `count()` der Woche `>= max` → `false`; sonst `insert` → `true`.
  - `deleteItem`/`setItemChecked`: `where (id eq …) and (weekStart eq …)` → `> 0`.
  - `saveCheck`: `upsert` (Exposed v1 `org.jetbrains.exposed.v1.jdbc.upsert`) oder `deleteWhere` + `insert` in einer `dbQuery`.
  - `deleteCheck`: `deleteWhere { (weekStart eq …) and (entryKey eq key) }`.
  - Fake: `mutableListOf<ShoppingItem>()` + `mutableMapOf<Pair<PlanWeek, String>, ShoppingCheck>()`, gleiche Semantik
    (Reihenfolge = Einfügereihenfolge, Limit pro Woche, Woche muss passen).
- **MIRROR**: REPOSITORY_PATTERN, TABLE_PATTERN; Test-Setup wie `ExposedRecipeRepositoryTest` (`DatabaseFactory.create(dir.resolve("t.db"), MutableClock(now))`).
- **IMPORTS**: `org.jetbrains.exposed.v1.core.{and, eq}`, `org.jetbrains.exposed.v1.jdbc.{Database, deleteWhere, insert, selectAll, update}`,
  `de.familyplanner.core.persistence.dbQuery`.
- **GOTCHA**: Keine verschachtelte `dbQuery` (Pool 1 → Deadlock). `selectAll().where{…}.count()` liefert `Long`.
  Falls `upsert` mit SQLite-Dialekt Probleme macht: Fallback `deleteWhere` + `insert` in derselben Transaktion.
- **VALIDATE**: `./gradlew :meals:test --tests '*ExposedShoppingListRepositoryTest*'` grün (Migration V6 läuft über Flyway).

### Task 6: Service (TDD)
- **ACTION**: `ShoppingListServiceTest.kt` (RED), `shopping/ShoppingListService.kt` (GREEN); `PlanFixture` um
  `val shoppingRepository = InMemoryShoppingListRepository()` und
  `val shopping = ShoppingListService(planRepository, shoppingRepository, clock) { "item-${++nextItemId}" }` erweitern.
- **IMPLEMENT**:
  ```kotlin
  suspend fun list(week: PlanWeek): ShoppingList =
      shoppingListOf(week, plan.findBetween(week.monday, week.days.last()), shopping.findItems(week), shopping.findChecks(week))

  suspend fun addItem(week: PlanWeek, ingredient: Ingredient): ShoppingList {
      val item = ShoppingItem(ShoppingItemId(newId()), week, validatedIngredients(listOf(ingredient)).single())
      if (!shopping.addItemIfBelowLimit(item, SHOPPING_ITEMS_MAX_COUNT, clock.instant())) {
          throw AppException.Validation("Höchstens $SHOPPING_ITEMS_MAX_COUNT freie Einträge")
      }
      log.info("Shopping item added: {} {}", week.monday, item.id.value)
      return list(week)
  }

  suspend fun deleteItem(week: PlanWeek, id: ShoppingItemId) {
      if (!shopping.deleteItem(week, id)) throw AppException.NotFound(ENTRY_NOT_FOUND)
      log.info("Shopping item deleted: {}", id.value)
  }

  suspend fun setChecked(week: PlanWeek, key: String, checked: Boolean): ShoppingList {
      if (key.isBlank() || key.length > ENTRY_KEY_MAX) throw AppException.Validation("Ungültiger Eintrag")
      val entry = list(week).entries.firstOrNull { it.key == key } ?: throw AppException.NotFound(ENTRY_NOT_FOUND)
      val itemId = entry.itemId
      when {
          itemId != null -> shopping.setItemChecked(week, itemId, checked)
          checked -> shopping.saveCheck(week, ShoppingCheck(key, entry.quantity), clock.instant())
          else -> shopping.deleteCheck(week, key)
      }
      log.info("Shopping entry checked: {} {}", week.monday, checked)
      return list(week)
  }
  // companion: const val ENTRY_NOT_FOUND = "Eintrag nicht gefunden"
  ```
- **MIRROR**: SERVICE_PATTERN, ERROR_HANDLING, LOGGING_PATTERN.
- **GOTCHA**: `validatedIngredients` wirft bei leerem Namen „Zutat muss zwischen 1 und 80 Zeichen lang sein“ — passt.
  Der Rückgabewert von `setItemChecked` kann ignoriert werden (Eintrag wurde eben gelesen); bei Wettlauf (gleichzeitig
  gelöscht) ist ein stilles No-op akzeptabel — **oder** `if (!…) throw NotFound` (bevorzugt, kein stilles Schlucken).
- **VALIDATE**: `./gradlew :meals:test --tests '*ShoppingListServiceTest*'` grün.

### Task 7: Web – Routen, Mapper, Verdrahtung
- **ACTION**: `web/ShoppingApiMappers.kt`, `web/ShoppingRoutes.kt`; `MealsModule.kt` und `Application.kt` ergänzen.
- **IMPLEMENT**:
  ```kotlin
  fun Route.shoppingRoutes(shopping: ShoppingListService) {
      authenticate(AUTH_DEVICE) {
          get("/api/v1/meals/weeks/{weekStart}/shopping-list") {
              call.principalOrThrow()
              call.respond(ShoppingListResponse(success = true, data = shopping.list(call.weekParameter()).toApi()))
          }
          post("/api/v1/meals/weeks/{weekStart}/shopping-list/items") {
              call.requireParent()
              val week = call.weekParameter()
              val list = shopping.addItem(week, call.receive<ApiIngredient>().toDomain())
              call.respond(HttpStatusCode.Created, ShoppingListResponse(success = true, data = list.toApi()))
          }
          delete("/api/v1/meals/weeks/{weekStart}/shopping-list/items/{itemId}") {
              call.requireParent()
              shopping.deleteItem(call.weekParameter(), ShoppingItemId(call.uuidParameter("itemId")))
              call.respond(HttpStatusCode.NoContent)
          }
          put("/api/v1/meals/weeks/{weekStart}/shopping-list/checks") {
              call.requireParent()
              val request = call.receive<CheckShoppingEntryRequest>()
              val list = shopping.setChecked(call.weekParameter(), request.key, request.checked)
              call.respond(ShoppingListResponse(success = true, data = list.toApi()))
          }
      }
  }
  ```
  Mapper: `ShoppingList.toApi() = ApiShoppingList(weekStart = week.monday.toString(), entries = entries.map { it.toApi() })`,
  `ShoppingEntry.toApi()` (alle Felder; `itemId?.value`), `EntrySource.toApi()` (`date.toString()`).
  Koin: `single<ShoppingListRepository> { ExposedShoppingListRepository(get()) }`, `single { ShoppingListService(get(), get(), clock) }`.
  `Application.kt`: Import `de.familyplanner.meals.web.shoppingRoutes` + `ShoppingListService`; `shoppingRoutes(get<ShoppingListService>())` nach `recipeRoutes`.
- **MIRROR**: ROUTE_PATTERN, KOIN_AND_WIRING; `Ingredient.toDomain()` aus `RecipeApiMappers.kt` wiederverwenden.
- **GOTCHA**: Reihenfolge `requireParent()` **vor** `weekParameter()`/`receive` → Kind bekommt 403 auch bei ungültigem Body
  (wie bei Rezepten). Generierte API-Klassen heißen wie die Domänenklassen → Alias-Importe
  (`de.familyplanner.api.model.ShoppingList as ApiShoppingList`, `ShoppingEntry as ApiShoppingEntry`, `Ingredient as ApiIngredient`).
- **VALIDATE**: `./gradlew :app:compileKotlin` grün.

### Task 8: Integrations-/Contract-Tests
- **ACTION**: `backend/app/src/test/kotlin/de/familyplanner/app/MealsShoppingApiTest.kt`.
- **IMPLEMENT**: Hilfen wie in `MealsRecipeApiTest` (`start()`, `ingredient(...)`, `dishJson(...)`, `makeCook`),
  `shoppingList(token)` → `ShoppingListResponse.data`. Elterngerichte (Koch = Elternteil, `memberIdOf(parentToken)`) sind
  direkt `APPROVED`; ein Kindergericht wird per Kind-Token eingereicht (`SUBMITTED`) und per `POST …/approval` freigegeben.
  Tests siehe „Testing Strategy → Integration“.
- **MIRROR**: TEST_STRUCTURE (`client.call(...)` validiert gegen OpenAPI).
- **GOTCHA**: `WEEK = "2026-10-19"`, Tage `2026-10-20` … `2026-10-25`. Kind darf nur einreichen, wenn es kocht
  (`makeCook`). Eltern können je Tag nur ein Gericht planen → verschiedene Tage verwenden.
- **VALIDATE**: `./gradlew :app:test --tests '*MealsShoppingApiTest*'` grün.

### Task 9: Doku & Traceability
- **ACTION**: `docs/04-verification/traceability.md` Zeilen REQ-MEAL-040/041 füllen; PRD-Phase 6 nach Umsetzung um
  Report-Pfad ergänzen (Status bleibt `in-progress` bis 6b fertig).
- **IMPLEMENT**:
  - 040: Design `[meals](../03-design/meals.md)`; Unit `ShoppingListTest`, `ShoppingListServiceTest`, `ExposedShoppingListRepositoryTest` (`REQ-MEAL-040 …`); Integration `MealsShoppingApiTest` (`REQ-MEAL-040 …`); Abnahme „manuell: Abnahme Phase 6b“; Status „umgesetzt (Backend)“.
  - 041: analog mit `REQ-MEAL-041 …`.
- **VALIDATE**: Tabelle rendert (gleiche Spaltenzahl wie Nachbarzeilen).

---

## Testing Strategy

### Unit Tests – `ShoppingListTest` (reine Funktion)

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `REQ-MEAL-040 same name and unit are merged with summed quantity and sources` | Gericht A „500 g Spaghetti“ (Di), B „250 g spaghetti“ (Mi) | 1 Eintrag „Spaghetti“, 750.0, „g“, Quellen [A, B] | Groß-/Kleinschreibung |
| `REQ-MEAL-040 ingredients without quantity appear once per name` | A „Salz“, B „Salz“ | 1 Eintrag „Salz“, quantity null, Quellen [A, B] | |
| `REQ-MEAL-040 different units stay separate` | „200 g Sahne“, „1 Becher Sahne“ | 2 Einträge | |
| `REQ-MEAL-040 quantity and no quantity stay separate` | „2 Zwiebeln“ (ohne Einheit), „Zwiebeln“ | 2 Einträge | ja |
| `REQ-MEAL-040 submitted and rejected dishes are not included` | SUBMITTED + REJECTED + APPROVED | nur Zutaten des APPROVED | |
| `REQ-MEAL-040 dishes outside the week are ignored` | Gericht am Montag der Folgewoche | leer | ja |
| `REQ-MEAL-040 sums are exact to three decimals` | 0.1 l + 0.2 l | 0.3 | ja |
| `REQ-MEAL-040 duplicate ingredient in one dish counts once as source` | A „100 g Käse“ + „50 g Käse“ | 150, Quellen [A] | ja |
| `REQ-MEAL-040 entries are sorted by name and keep first spelling` | „Zucchini“, „apfel“, „Apfel“ | Reihenfolge Apfel(„apfel“ zuerst gesehen), Zucchini | |
| `REQ-MEAL-041 free items follow computed entries with item key` | 1 Gericht + 2 Items | berechnet zuerst, dann Items in Reihenfolge, `key = "item:<id>"`, `itemId` gesetzt | |
| `REQ-MEAL-041 tick counts only while quantity is unchanged` | Check(key, 500.0), aktuelle Menge 750 | `checked = false`; mit 500 → `true` | ja |
| `REQ-MEAL-041 tick without quantity stays valid` | Check(key, null), Eintrag ohne Menge | `checked = true` | |
| `REQ-MEAL-040 empty week gives empty list` | keine Gerichte, keine Items | `entries` leer | ja |

### Unit Tests – `ShoppingListServiceTest` (Fakes über `PlanFixture`)

| Test | Expected |
|---|---|
| `REQ-MEAL-040 list contains approved dishes of the week` | Elterngericht via `mealPlan.create` → Eintrag vorhanden |
| `REQ-MEAL-041 added item appears in the list` | `addItem` → Liste enthält Eintrag mit `itemId` |
| `REQ-MEAL-041 item with blank name is rejected` | `AppException.Validation` |
| `REQ-MEAL-041 more than 50 items are rejected` | 51. → `Validation` |
| `REQ-MEAL-041 deleting unknown or foreign-week item fails with not found` | `NotFound` |
| `REQ-MEAL-041 checking a computed entry marks it checked` | `checked = true` |
| `REQ-MEAL-041 checked entry reopens when another dish adds quantity` | Häkchen bei 500 g, zweites Gericht +250 g → `checked = false` |
| `REQ-MEAL-041 unchecking removes the tick` | `checked = false` |
| `REQ-MEAL-041 checking a free item` | `checked = true` |
| `REQ-MEAL-041 unknown key fails with not found` / `blank or too long key fails with validation` | `NotFound` / `Validation` |

### Persistenz – `ExposedShoppingListRepositoryTest`

- `REQ-MEAL-041 items are stored per week in creation order` (zwei Wochen, Reihenfolge, Felder inkl. `null`-Menge/-Einheit).
- `REQ-MEAL-041 item limit is enforced atomically` (`max = 2` → drittes `false`).
- `REQ-MEAL-041 delete and check only within the item's week` (falsche Woche → `false`).
- `REQ-MEAL-041 checks are saved, replaced and deleted` (zweimal `saveCheck` gleicher Key → eine Zeile mit neuer Menge; `null`-Menge bleibt `null`).

### Integration – `MealsShoppingApiTest`

| Test | Expected |
|---|---|
| `REQ-MEAL-040 approved dishes are merged with their sources` | Zwei Elterngerichte (Di „500 g Spaghetti“, „Salz“; Mi „250 g Spaghetti“, „Salz“) → Einträge Salz (ohne Menge, 2 Quellen), Spaghetti 750 g (2 Quellen) |
| `REQ-MEAL-040 submitted child dish appears only after approval` | Kind reicht ein → nicht in Liste; nach `approval` → enthalten |
| `REQ-MEAL-040 different units stay separate entries` | „200 g Sahne“ + „1 Becher Sahne“ → 2 Einträge |
| `REQ-MEAL-041 parent adds, checks and deletes a free item` | POST 201 + Eintrag; PUT checks → `checked`; DELETE 204 → weg |
| `REQ-MEAL-041 checked dish entry reopens after more quantity is approved` | Häkchen, weiteres Gericht → `checked = false` |
| `REQ-MEAL-041 child can read but not change the list` | GET 200; POST/PUT/DELETE → 403 |
| `REQ-MEAL-041 unknown key and unknown item return 404` | 404 |
| `REQ-MEAL-040 week must start on a monday` | `GET …/weeks/2026-10-20/shopping-list` → 400 |

### Edge Cases Checklist
- [x] Leere Woche → leere Liste
- [x] Maximale Größe: 50 freie Einträge; Summen > 10000 (Schema ohne `maximum`)
- [x] Ungültige Eingaben: leerer Name, Menge außerhalb, zu lange Einheit, leerer/zu langer Key, Nicht-UUID `itemId`, kein Montag
- [x] Gleichzeitiger Zugriff: Limit atomar in einer Transaktion; Häkchen-Wettlauf unkritisch (letzter gewinnt)
- [ ] Netzwerkfehler — App-Thema (6b)
- [x] Berechtigung: Kind → 403 bei allen Schreibzugriffen

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
EXPECT: alle Tests grün

### Full Test Suite + Coverage
```bash
cd backend && ./gradlew check koverVerify
```
EXPECT: keine Regressionen, Coverage ≥ 80 %

### App-Client kompiliert weiter
```bash
cd android && ./gradlew :core:api:compileDebugKotlin :feature:meals:testDebugUnitTest
```
EXPECT: grün (Vertrag nur additiv erweitert)

### Image
```bash
cd backend && ./gradlew :app:buildFatJar && docker build -t family-planner:local .
```
EXPECT: Build erfolgreich

### Manual Validation
```bash
cd backend && FP_SETUP_TOKEN=local-setup-token-123 ./gradlew :app:run
```
- [ ] Setup, Elterngerät koppeln, zwei Elterngerichte mit gleicher Zutat (gleiche Einheit) anlegen
- [ ] `GET /api/v1/meals/weeks/<Montag>/shopping-list` zeigt zusammengeführten Eintrag mit zwei Quellen
- [ ] Freien Eintrag hinzufügen, abhaken, löschen
- [ ] Log enthält nur IDs/Wochen, keine Zutatennamen

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt
- [ ] Alle Validierungskommandos grün
- [ ] Tests zuerst geschrieben (TDD), Testnamen mit REQ-ID
- [ ] Keine ktlint-/detekt-Befunde
- [ ] API nur additiv erweitert; Contract-Tests grün
- [ ] Regeln aus „Fachliche Entscheidungen“ in `docs/03-design/meals.md` dokumentiert

## Completion Checklist
- [ ] Code folgt den gefundenen Mustern (reine Funktion, Service, Exposed in einer `dbQuery`, Routen)
- [ ] Fehler nur über `AppException`, deutsche Meldungen
- [ ] Logging nur IDs/Wochen
- [ ] Fakes statt Mocks, AAA-Kommentare
- [ ] Keine Magic Numbers (Limits als `const val`)
- [ ] Traceability aktualisiert
- [ ] Kein Scope über „NOT Building“ hinaus

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Namensvarianten („Zwiebel“/„Zwiebeln“, „Bio-Lauch“) werden nicht zusammengeführt | H | L | Bewusst: PRD verlangt gleichen Namen; Herkunft je Eintrag macht es sichtbar; Rezept-Import entfernt bereits Marken-Präfixe |
| Häkchen „springt auf“, wenn sich Mengen ändern — kann irritieren | M | L | Gewollt (nichts geht unter); in 6b ggf. Hinweis „Menge geändert“ |
| Exposed-`upsert` mit SQLite | L | L | Fallback `deleteWhere` + `insert` in einer Transaktion |
| Contract-Test scheitert an Summen > 10000 | L | M | `quantity` an `ShoppingEntry` ohne `maximum` |
| Generierte Klassennamen kollidieren mit Domäne | H | L | Alias-Importe im Web-Paket |

## Notes
- Die Liste wird bei jedem Lesen neu berechnet (eine Woche = höchstens 7 Gerichte × 30 Zutaten) — kein Cache nötig.
- Schreib-Endpunkte (außer DELETE) liefern die ganze aktuelle Liste, damit die App nach jeder Aktion ohne zweiten Request
  den Stand anzeigen kann (wichtig für 6b).
- Phase 6b (App) bekommt einen eigenen Plan: Einkaufslisten-Screen für Eltern (Liste mit Herkunft, Abhaken, freie Einträge),
  Offline-Lesen nach ADR-0011 zu klären.
- Umsetzung **in einer neuen Session** (Sonnet): `/ecc:prp-implement .claude/PRPs/plans/einkaufsliste-backend.plan.md`.
