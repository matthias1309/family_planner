# Plan: Avatar & Shop – Spike + Backend/API (GAME Phase 4a)

## Summary
Zuerst ein kurzer **Spike**: Die DiceBear-Stildefinition „Avataaars“ (reines JSON, keine JS-Laufzeit) wird als gemeinsamer
Vertrag unter `api/avatar/` abgelegt, und `android/core/ui` bekommt einen kleinen Renderer (Definition + Auswahl → SVG →
AndroidSVG → Compose). Ein Robolectric-Test schreibt Testbilder; sehen sie gut aus, wird ADR-0017 akzeptiert (Go), sonst
greift der dokumentierte Plan B. Danach das **Backend**: fester Shop-Katalog im Code (Slot, Titel, Preis, 0 = Grundsortiment),
Kauf mit atomarer `SHOP`-Abbuchung, Besitz je Kind, Avatar je Kind speichern und für alle lesbar. Die Bildschirme (Editor,
Shop, Avatar in Rangliste/Board/„Heute“, Sparziel-Hinweis) folgen in Phase 4b.

## User Story
Als Kind möchte ich einen Comic-Avatar aus Bausteinen zusammenstellen und weitere Teile mit meinem Guthaben kaufen, damit
ich in der App erkennbar bin und meine Punkte sichtbar etwas wert sind.

## Problem → Solution
Rangliste und Board zeigen nur Anfangsbuchstaben; `SHOP` steht im Journal-`CHECK` und im OpenAPI-Enum, wird aber nie gebucht;
Technik und Lizenz der Avatar-Bausteine sind offen → geprüfte Bausteine (Lizenz + Darstellung) mit ADR, Katalog/Kauf/Besitz/
Avatar über die REST-API, Renderer in `core:ui` für Phase 4b bereit.

## Metadata
- **Complexity**: Large (≈ 30 Dateien; Backend folgt durchgehend den Belohnungs-Mustern, neu ist nur der Renderer)
- **Source PRD**: `docs/01-requirements/gamification.prd.md`
- **PRD Phase**: 4 „Avatar & Shop“ → Teil 4a Spike + Backend/API (4b App folgt)
- **Requirements**: REQ-GAME-030, -031 (Server-Anteil + Renderer). REQ-GAME-041 2. Kriterium (Kauf-Hinweis) ist reine App-Logik → 4b
- **Estimated Files**: ≈ 18 neu, ≈ 12 geändert
- **Entschieden mit Nutzer (2026-11-01)**: Aufteilung 4a/4b; Stil **Avataaars** (Pablo Stanley via DiceBear)

---

## UX Design

N/A für das Backend. Einziges sichtbares Ergebnis in 4a: Testbilder des Renderers (`android/core/ui/build/avatar-spike/*.png`)
und eine Compose-`@Preview` in `core:ui`. Die Bildschirme baut 4b.

### Interaction Changes (API, Vertrag 0.9.0 → 0.10.0, nur additiv)
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| `GET /gamification/avatar/catalog` | – | Katalog: Slots (Reihenfolge, optional?) mit Items (`id`, `slot`, `title`, `price`) | alle Rollen; statisch |
| `GET /gamification/avatars` | – | Avatare aller Kinder, die einen gespeichert haben | alle Rollen (Rangliste/Board/„Heute“) |
| `GET /gamification/members/{memberId}/avatar` | – | `AvatarProfile`: Avatar (oder `null`) + gekaufte Item-IDs | Kind nur selbst, Eltern alle, Geschwister 403 |
| `PUT /gamification/members/{memberId}/avatar` | – | Avatar speichern (ganze Konfiguration) | nur das Kind selbst; Item muss kostenlos oder gekauft sein |
| `POST /gamification/avatar/items/{itemId}/purchase` | – | Item kaufen (Kind, eigenes Guthaben) | 201 `AvatarPurchase` (Item, Preis, neues Guthaben); 409 bei zu wenig Guthaben / schon besessen / kostenlos |
| Journal / Konto | `SHOP` nie gebucht | Buchung `SHOP` −Preis, Titel „Shop: <Item>“ | zählt nicht für „Gesammelt“ → Rangliste unverändert (REQ-GAME-014, 2. Kriterium) |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/ExposedRedemptionRepository.kt` | 30-70, 138-173 | Atomare Abbuchung in **einer** `dbQuery` (Guthaben prüfen → Zeile + Ledger-`insert`) – Kauf spiegelt das exakt |
| P0 | `backend/gamification/src/main/kotlin/de/familyplanner/gamification/rewards/RedemptionService.kt` | all | Service-Muster: Rolle → Repository-Outcome → `AppException`, Logging |
| P0 | `backend/gamification/src/main/kotlin/de/familyplanner/gamification/rewards/RewardService.kt` | 60-110 | „nur das Kind selbst“ (`requireOwnGoal`), Eltern-/Geschwister-Sicht |
| P0 | `backend/gamification/src/main/kotlin/de/familyplanner/gamification/web/RewardRoutes.kt` | all | Routen, `principalOrThrow`, `toActor`, Envelope-Responses |
| P0 | `api/openapi.yaml` | 1042-1258, 2239-2510 | Pfad-/Schema-Stil, Envelope `{success,data,error}`, Standard-Responses |
| P1 | `backend/gamification/src/main/kotlin/de/familyplanner/gamification/badges/Badge.kt` | all | Fester Katalog im Code, IDs nie ändern (Test mit fester ID-Liste) |
| P1 | `backend/gamification/src/main/kotlin/de/familyplanner/gamification/ledger/LedgerEntry.kt` | 12-60 | `EntryKind.SHOP` (zählt nicht für Gesammelt), `*SourceKey`-Funktionen |
| P1 | `backend/gamification/src/main/resources/db/migration/V12__gamification_rewards.sql` | all | Migrationsstil (Kommentare, `CHECK`, `ON DELETE CASCADE`) |
| P1 | `backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/RewardTables.kt` | all | Exposed-Tabellen-Stil, Längen-Konstanten |
| P1 | `backend/gamification/src/test/kotlin/de/familyplanner/gamification/fakes/GamificationFixture.kt`, `InMemoryGamificationRepositories.kt` | all | Fixture + In-Memory-Fakes, in die `AvatarService` eingehängt wird |
| P1 | `backend/gamification/src/test/kotlin/de/familyplanner/gamification/persistence/ExposedRedemptionRepositoryTest.kt` | 1-80 | Integrationstest mit `@TempDir` + `DatabaseFactory.create` |
| P1 | `backend/app/src/test/kotlin/de/familyplanner/app/RewardsApiTest.kt` | 1-80 | API-Test mit Vertragsprüfung (`call(...)`, `setupFamilyWithChild`) |
| P1 | `backend/app/src/main/kotlin/de/familyplanner/app/Application.kt` | 115-130 | Routen-Registrierung |
| P2 | `android/core/ui/build.gradle.kts`, `android/buildSrc/src/main/kotlin/familyplanner.android-*.gradle.kts` | all | Convention-Plugins, Robolectric bereits über `android-compose` |
| P2 | `android/feature/gamification/src/main/kotlin/de/familyplanner/android/feature/gamification/ui/components/LeaderboardList.kt` | 55-60 | heutiger Platzhalter (Anfangsbuchstabe im Kreis, `AvatarSize`) – Ziel des Renderers in 4b |
| P2 | `docs/03-design/gamification.md` | all | Hier kommt der Abschnitt „Avatar & Shop (Phase 4a)“ dazu |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| DiceBear-Lizenzen | https://www.dicebear.com/licenses/ | DiceBear-Code MIT; Avataaars (Pablo Stanley) „Free for personal and commercial use“ (keine CC-Lizenz); CC0-Alternativen: Open Peeps, Lorelei, Notionists |
| Avataaars-Stildefinition | https://cdn.jsdelivr.net/npm/@dicebear/styles@10.6.0/dist/avataaars.min.json | JSON (Schema `@dicebear/schema@1.4.0`): `canvas` 280×280, `components` mit `variants`, `colors` je Name; enthält `meta.license.text` für die Namensnennung |
| Definitions-Schema | https://cdn.jsdelivr.net/npm/@dicebear/schema@1.4.0/dist/definition.min.json | Elemente `{type: element|component, name, attributes, children}`; Farbattribut = String **oder** `{type:"color", name}` |
| AndroidSVG | https://bigbadaboom.github.io/androidsvg/ (Maven `com.caverock:androidsvg-aar:1.4`, Apache-2.0) | `SVG.getFromString(svg)`, `setDocumentWidth/Height`, `renderToCanvas(canvas)`; unterstützt `path`, `rect`, `circle`, `linearGradient`, `transform`, `fill-opacity` |

```
KEY_INSIGHT: DiceBear 10 liefert Stile als deklaratives JSON; ein Renderer ist ein ~100-Zeilen-Baumdurchlauf, kein JS nötig.
APPLIES_TO: Task 2 (AvatarSvg), Task 6 (Katalog-Konsistenztest)
GOTCHA: Komponenten sind verschachtelt (clothes.graphicShirt enthält die Komponente clothesGraphic). Farbreferenzen in
        Varianten: top → "hair" und "hat", facialHair → "facialHair", clothes → "clothes", accessories → "accessories",
        canvas → "skin". Komponenten ohne Auswahl (accessories/facialHair/clothesGraphic = null) werden weggelassen.
```
```
KEY_INSIGHT: Avataaars-Lizenz ist eine eigene Formulierung, kein CC; Namensnennung nicht gefordert, wird aber eingebaut.
APPLIES_TO: Task 1 (README/Lizenzdatei), Task 3 (ADR-0017)
GOTCHA: avataaars.com hatte bei der Recherche ein abgelaufenes Zertifikat → Lizenztext nur über DiceBear (`meta.license`)
        verifiziert. Privates Familienprojekt, keine Weiterverbreitung → Restrisiko gering; Plan B im ADR: Open Peeps (CC0).
```

---

## Patterns to Mirror

### NAMING_CONVENTION
```kotlin
// SOURCE: backend/gamification/src/main/kotlin/de/familyplanner/gamification/rewards/Reward.kt:6-17
const val REWARD_TITLE_MAX = 40
@JvmInline
value class RewardId(
    val value: String,
)
/** A real reward parents offer for points (REQ-GAME-040); [archived] = deactivated, no longer redeemable. */
data class Reward(
```
Paket `de.familyplanner.gamification.avatar` (Domäne + Service), Tabellen in `persistence/AvatarTables.kt`, Routen
`web/AvatarRoutes.kt`, Mapper `web/AvatarApiMappers.kt`. KDoc auf Englisch, Fehlermeldungen auf Deutsch.

### ERROR_HANDLING
```kotlin
// SOURCE: backend/gamification/src/main/kotlin/de/familyplanner/gamification/rewards/RedemptionService.kt:68-76
private fun failure(outcome: RedeemOutcome): AppException =
    when (outcome) {
        is RedeemOutcome.Redeemed -> AppException.Conflict("Die Belohnung wurde bereits eingelöst")
        RedeemOutcome.RewardNotFound -> AppException.NotFound("Belohnung nicht gefunden")
        RedeemOutcome.RewardArchived -> AppException.Conflict("Die Belohnung ist deaktiviert")
        is RedeemOutcome.InsufficientBalance ->
            AppException.Conflict("Nicht genug Guthaben: ${outcome.balance} von ${outcome.price} Punkten")
    }
```
Fehlerreihenfolge wie Phase 3a: Rolle (403) → Eingabe (400) → Existenz (404) → Zustand (409). Kind/Eltern-Prüfung über
`members.requireChild(id)` (`Members.kt`: unbekannt → 404, Eltern → 400).

### LOGGING_PATTERN
```kotlin
// SOURCE: backend/gamification/src/main/kotlin/de/familyplanner/gamification/rewards/RewardService.kt:24,37
private val log = LoggerFactory.getLogger(RewardService::class.java)
log.info("Reward created: {}", reward.id.value)
```
`info` je erfolgreicher Schreibaktion (`Avatar item purchased: {} by {}`, `Avatar saved: {}`), `warn` bei unbekannter
gespeicherter Item-ID beim Lesen (Katalog geändert), nie Guthaben/Namen loggen.

### REPOSITORY_PATTERN (Prüfung + Folgeschreiben in einer Transaktion)
```kotlin
// SOURCE: backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/ExposedRedemptionRepository.kt:44-70
dbQuery(db) {
    val reward = RewardTable.selectAll().where { RewardTable.id eq rewardId.value }.firstOrNull()
        ?: return@dbQuery RedeemOutcome.RewardNotFound
    if (reward[RewardTable.archivedAt] != null) return@dbQuery RedeemOutcome.RewardArchived
    val price = reward[RewardTable.price]
    val balance = balanceOf(memberId)
    if (balance < price) return@dbQuery RedeemOutcome.InsufficientBalance(balance, price)
    insertRedemption(id, memberId, rewardId, reward, at)
    insertEntry(LedgerEntry(LedgerEntryId(debitEntryId), memberId, EntryKind.REWARD, -price, debitTitle, null,
        rewardSourceKey(id.value), memberId, at))
    ...
}
```
`balanceOf` und `insertEntry` (Zeilen 138-173) sind dort `private` → für den Kauf **gleiche zwei Helfer** in
`ExposedAvatarRepository` (oder als `internal` Top-Level-Funktionen in eine neue Datei `persistence/LedgerWrites.kt`
ziehen und von beiden Repositories nutzen – bevorzugt, DRY). Plain `insert`, nie `insertIgnore`.

### SERVICE_PATTERN
```kotlin
// SOURCE: backend/gamification/src/main/kotlin/de/familyplanner/gamification/rewards/RewardService.kt:96-103
private suspend fun requireOwnGoal(actor: Actor, memberId: MemberId) {
    members.requireChild(memberId)
    if (actor.id != memberId) throw AppException.Forbidden("Nur das Kind selbst wählt sein Sparziel")
}
```

### ROUTE_PATTERN
```kotlin
// SOURCE: backend/gamification/src/main/kotlin/de/familyplanner/gamification/web/RewardRoutes.kt:68-73
post("/api/v1/gamification/rewards/{rewardId}/redemptions") {
    val actor = call.principalOrThrow().toActor()
    val redemption = redemptions.redeem(actor, RewardId(call.uuidParameter("rewardId")))
    call.respond(HttpStatusCode.Created, RedemptionResponse(success = true, data = redemption.toApi()))
}
```
`itemId` ist **keine** UUID → eigener Helfer `ApplicationCall.itemIdParameter()` in `AvatarRoutes.kt`:
`parameters["itemId"]?.takeIf { ITEM_ID_PATTERN.matches(it) } ?: throw AppException.Validation("Ungültige Kennung 'itemId'")`.

### TEST_STRUCTURE
```kotlin
// SOURCE: backend/gamification/src/test/kotlin/de/familyplanner/gamification/rewards/RewardServiceTest.kt:14-33
class RewardServiceTest {
    private val fixture = GamificationFixture()
    @Test
    fun `REQ-GAME-040 parent creates reward and every child sees it`() =
        runTest {
            val parent = fixture.parent()
            val created = reward(parent)
            val seen = fixture.rewards.list(includeArchived = false)
            assertEquals(listOf(created), seen)
        }
```
Testnamen beginnen mit der REQ-ID; AAA ohne Kommentare (wie im Bestand).

### ANDROID_MODULE (Convention-Plugins)
```kotlin
// SOURCE: android/core/ui/build.gradle.kts
plugins {
    id("familyplanner.android-library")
    id("familyplanner.android-compose")   // bringt Robolectric + Compose-Testlibs für testImplementation
}
dependencies {
    api(project(":core:data"))
    implementation(libs.lifecycle.runtime.compose)
}
```

---

## Design Decisions (in dieser Phase getroffen)

| Thema | Entscheidung | Begründung |
|---|---|---|
| Bausteine | DiceBear-Definition `avataaars.min.json` aus `@dicebear/styles@10.6.0`, **unverändert** abgelegt unter `api/avatar/avataaars.json` | Gemeinsame Quelle: Backend prüft den Katalog dagegen, App rendert daraus; `api/**` triggert beide CI-Workflows |
| Darstellung | App baut aus Definition + Auswahl einen SVG-String und rendert ihn mit AndroidSVG auf den Compose-Canvas | Offline, keine JS-Laufzeit, kein Server-Rendering auf dem Pi |
| Item-ID | `<slot>.<variante>`; Farben `<slot>.<hex ohne #>` (z. B. `top.winterHat1`, `hairColor.2c1b18`) | Global eindeutig (Kauf-Pfad), lesbar, Variante direkt im JSON nachschlagbar; **nie ändern** (gespeichert) |
| Slots | 13 Slots, siehe Katalog; `accessories`, `facialHair`, `clothesGraphic` optional (`null` = keins) | PRD-Slots (Hautton, Frisur, Haarfarbe, Augen, Mütze, Oberteil, Brille, Hintergrund) + Avataaars-Eigenheiten |
| Mütze vs. Frisur | Beides im Slot `top` (Avataaars ersetzt die Haare durch die Mütze); App zeigt 4b zwei Shop-Rubriken über `ShopCategory` | Ein Slot = eine Komponente im JSON |
| Bart-/Brillenfarbe | Bart nutzt `hairColor`; Brillenfarbe fest `#262e33` | Weniger Slots, kindgerecht |
| Hintergrund | eigener Slot `background` mit eigener Palette (Kreis hinter dem Avatar) – nicht Teil der DiceBear-Definition | Konsistenztest prüft Hintergrund nur als Hex |
| Grundsortiment | Preis 0 = kostenlos, gehört allen, ist nicht kaufbar (409) | PRD: Grundsortiment kostenlos |
| Kein Avatar | `avatar = null`, bis das Kind einmal speichert; Standardauswahl erzeugt die App (erstes kostenloses Item je Pflicht-Slot) | Metrik „Avatar vom Standard abweichend“ = gespeicherter Avatar existiert |
| Speichern | `PUT` ersetzt die ganze Konfiguration (Pflicht-Slots gesetzt, Item passt zum Slot, kostenlos oder gekauft) | Immutability, einfach zu validieren |
| Kauf | Eine Transaktion: Item kostenpflichtig, nicht besessen, Guthaben ≥ Preis → `game_avatar_items` + `SHOP`-Buchung (`source_key = shop:<memberId>:<itemId>`) | Gleiches Muster wie Einlösen; `source_key` UNIQUE verhindert Doppelkauf zusätzlich |
| Kauf legt nicht an | Kauf ändert den Avatar nicht; Anlegen = `PUT` | Trennung „besitzen“/„tragen“ (REQ-GAME-031, 3. Kriterium) |
| Sichtbarkeit | Avatare aller Kinder für alle; Besitzliste nur Kind selbst + Eltern | PRD „Sichtbarkeit“: Avatare für alle, Guthaben-Details nicht |
| Eltern | Kein Avatar, kein Kauf (`requireChild` → 400 bzw. 403 für die Aktion) | Eltern sind Spielleitung |
| Rangliste | **unverändert**; App kombiniert Rangliste + `GET /avatars` | Eine Avatar-Quelle für Rangliste, Board-Lane und „Heute“ (4b) |
| Katalog | Kotlin-Liste im Code wie `Badge`; Eltern-Preisänderung ist *Could* → nicht jetzt | YAGNI |
| Entfernte Items | Liest der Server eine gespeicherte Item-ID, die nicht mehr im Katalog ist → Slot wird beim Lesen weggelassen (optional) bzw. auf erstes kostenloses Item gesetzt (Pflicht), `warn` | Robust bei Katalogpflege; App rendert unbekannte Varianten ebenfalls mit Fallback |

### Katalog (verbindlich; Preis 0 = Grundsortiment)

Reihenfolge der Slots = Editor-Reihenfolge. Titel deutsch, max. 40 Zeichen. Konsistenztest: jede Variante existiert in
`components.<komponente>.variants`, jede Farbe in `colors.<palette>.values` (Hex klein, ohne `#`).

| Slot (`AvatarSlot`) | JSON-Bezug | Optional | Shop-Kategorie | Items `variante=Titel(Preis)` |
|---|---|---|---|---|
| `skin` | `colors.skin` | nein | – | `614335`=Dunkelbraun(0), `ae5d29`=Braun(0), `d08b5b`=Mittelbraun(0), `edb98a`=Beige(0), `ffdbb4`=Hell(0), `fd9841`=Orange(0), `f8d25c`=Gelb(0) |
| `top` | `components.top` | nein | `HAIR` bzw. `HEADWEAR` (Mützen/Tücher) | HAIR: `shortFlat`=Kurz glatt(0), `shortRound`=Kurz rund(0), `shortWaved`=Kurz gewellt(0), `shortCurly`=Kurz lockig(0), `theCaesar`=Caesar(0), `theCaesarAndSidePart`=Caesar mit Scheitel(0), `sides`=Seiten(0), `frizzle`=Wuschelig(0), `shaggy`=Zottelig(0), `bob`=Bob(0), `bun`=Dutt(0), `curly`=Lockig(0), `curvy`=Wellig lang(0), `fro`=Afro(0), `dreads01`=Dreads kurz(0), `longButNotTooLong`=Mittellang(0), `straight01`=Lang glatt(0), `straight02`=Lang glatt 2(0), `bigHair`=Große Mähne(20), `dreads`=Dreads(20), `dreads02`=Dreads lang(20), `frida`=Frida(20), `froBand`=Afro mit Band(20), `miaWallace`=Pony(20), `shaggyMullet`=Vokuhila(20), `shavedSides`=Undercut(20), `straightAndStrand`=Lang mit Strähne(20) · HEADWEAR: `hijab`=Hidschab(0), `turban`=Turban(0), `hat`=Hut(30), `winterHat1`=Wintermütze(30), `winterHat02`=Bommelmütze(30), `winterHat03`=Mütze mit Ohren(30), `winterHat04`=Katzenmütze(30) |
| `hairColor` | `colors.hair` (+ Bart) | nein | `COLORS` | `2c1b18`=Schwarz(0), `4a312c`=Dunkelbraun(0), `724133`=Braun(0), `a55728`=Kastanie(0), `b58143`=Hellbraun(0), `d6b370`=Blond(0), `c93305`=Rot(0), `e8e1e1`=Grau(0), `ecdcbf`=Platinblond(15), `f59797`=Rosa(15) |
| `hatColor` | `colors.hat` | nein | `COLORS` | alle 15 Werte aus `colors.hat.values` kostenlos(0); Titel: `262e33`=Anthrazit, `65c9ff`=Himmelblau, `5199e4`=Blau, `25557c`=Dunkelblau, `e6e6e6`=Hellgrau, `929598`=Grau, `3c4f5c`=Schiefer, `b1e2ff`=Eisblau, `a7ffc4`=Mint, `ffdeb5`=Pfirsich, `ffafb9`=Rosa, `ffffb1`=Hellgelb, `ff488e`=Pink, `ff5c5c`=Rot, `ffffff`=Weiß |
| `eyes` | `components.eyes` | nein | `FACE` | `default`=Normal(0), `happy`=Fröhlich(0), `side`=Seitenblick(0), `squint`=Blinzeln(0), `wink`=Zwinkern(0), `closed`=Geschlossen(0), `surprised`=Überrascht(0), `eyeRoll`=Augenrollen(10), `hearts`=Herzaugen(10), `winkWacky`=Verrückt zwinkern(10), `cry`=Tränen(10), `xDizzy`=Schwindelig(10) |
| `eyebrows` | `components.eyebrows` | nein | `FACE` | alle 13 kostenlos(0): `default`=Normal, `defaultNatural`=Natürlich, `flatNatural`=Gerade, `raisedExcited`=Hochgezogen, `raisedExcitedNatural`=Hochgezogen natürlich, `angry`=Böse, `angryNatural`=Böse natürlich, `frownNatural`=Stirnrunzeln, `sadConcerned`=Besorgt, `sadConcernedNatural`=Besorgt natürlich, `unibrowNatural`=Monobraue, `upDown`=Schief, `upDownNatural`=Schief natürlich |
| `mouth` | `components.mouth` | nein | `FACE` | `default`=Normal(0), `smile`=Lächeln(0), `twinkle`=Schmunzeln(0), `serious`=Ernst(0), `concerned`=Besorgt(0), `sad`=Traurig(0), `disbelief`=Ungläubig(0), `eating`=Mampfen(10), `grimace`=Grimasse(10), `screamOpen`=Schrei(10), `tongue`=Zunge(10) — `vomit` bewusst **nicht** im Katalog |
| `clothes` | `components.clothes` | nein | `CLOTHES` | `shirtCrewNeck`=T-Shirt(0), `shirtVNeck`=V-Ausschnitt(0), `shirtScoopNeck`=Rundhals weit(0), `hoodie`=Hoodie(0), `graphicShirt`=Motiv-Shirt(15), `collarAndSweater`=Pulli mit Kragen(20), `overall`=Latzhose(20), `blazerAndShirt`=Sakko mit Hemd(25), `blazerAndSweater`=Sakko mit Pulli(25) |
| `clothesColor` | `colors.clothes` | nein | `COLORS` | kostenlos(0): `262e33`, `65c9ff`, `5199e4`, `25557c`, `e6e6e6`, `929598`; je 10: `3c4f5c`, `b1e2ff`, `a7ffc4`, `ffafb9`, `ffffb1`, `ff488e`, `ff5c5c`, `ffffff` (Titel wie `hatColor`) |
| `clothesGraphic` | `components.clothesGraphic` | ja (nur sichtbar mit `graphicShirt`) | `CLOTHES` | je 15: `bat`=Fledermaus, `bear`=Bär, `cumbia`=Cumbia, `deer`=Hirsch, `diamond`=Diamant, `hola`=Hola, `pizza`=Pizza, `resist`=Resist, `skull`=Totenkopf, `skullOutline`=Totenkopf-Umriss |
| `accessories` | `components.accessories` | ja | `ACCESSORIES` | `prescription01`=Brille(0), `prescription02`=Brille eckig(0), `round`=Runde Brille(0), `eyepatch`=Augenklappe(20), `kurt`=Kurt-Brille(25), `sunglasses`=Sonnenbrille(25), `wayfarers`=Wayfarer(25) |
| `facialHair` | `components.facialHair` | ja | `FACE` | `beardLight`=Leichter Bart(0), `beardMedium`=Vollbart(0), `beardMajestic`=Prachtbart(20), `moustacheFancy`=Schnurrbart(20), `moustacheMagnum`=Magnum-Schnurrbart(20) |
| `background` | eigene Palette | nein | `COLORS` | kostenlos(0): `b6e3f4`=Hellblau, `c0aede`=Lavendel, `d1d4f9`=Flieder, `ffd5dc`=Rosé, `ffdfbf`=Apricot; je 10: `65c9ff`=Himmelblau, `a7ffc4`=Mint, `ffffb1`=Zitrone, `ff5c5c`=Rot, `262e33`=Nacht |

Summe 157 Items, davon 60 kostenpflichtig (10–30 Punkte; Belohnungen kosten typischerweise 50–200). Komponente `nose` ist fest
`default` (kein Slot).

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `api/avatar/avataaars.json` | CREATE | DiceBear-Definition, unverändert (gepinnt 10.6.0) |
| `api/avatar/README.md` | CREATE | Herkunft, Version, SHA-256, Lizenz/Namensnennung, Aktualisierung |
| `api/README.md` | UPDATE | Verweis auf `avatar/` |
| `android/gradle/libs.versions.toml` | UPDATE | `androidsvg = "1.4"`, Library `androidsvg` |
| `android/core/ui/build.gradle.kts` | UPDATE | AndroidSVG, Serialization-JSON, Assets aus `../api/avatar` |
| `android/core/ui/src/main/kotlin/de/familyplanner/android/core/ui/avatar/AvatarLook.kt` | CREATE | Auswahl (Item-IDs je Slot) → Renderer-Auswahl |
| `android/core/ui/src/main/kotlin/de/familyplanner/android/core/ui/avatar/AvatarSvg.kt` | CREATE | Definition + Auswahl → SVG-String (rein, JVM-testbar) |
| `android/core/ui/src/main/kotlin/de/familyplanner/android/core/ui/avatar/AvatarDefinition.kt` | CREATE | Laden/Parsen aus Assets, einmalig gecacht |
| `android/core/ui/src/main/kotlin/de/familyplanner/android/core/ui/avatar/AvatarImage.kt` | CREATE | Composable (AndroidSVG auf Canvas) + `@Preview` |
| `android/core/ui/src/test/kotlin/de/familyplanner/android/core/ui/avatar/AvatarSvgTest.kt` | CREATE | Unit-Tests Renderer |
| `android/core/ui/src/test/kotlin/de/familyplanner/android/core/ui/avatar/AvatarImageSpikeTest.kt` | CREATE | Robolectric, schreibt Testbilder (Spike-Nachweis) |
| `docs/02-architecture/adr/0017-avatar-bausteine.md` | CREATE | Spike-Ergebnis, Lizenz, Technik, Plan B |
| `api/openapi.yaml` | UPDATE | 0.10.0: 5 Pfade, Schemas, Parameter `AvatarItemId` |
| `backend/gamification/src/main/resources/db/migration/V13__gamification_avatar.sql` | CREATE | `game_avatar_items`, `game_avatar_slots` |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/avatar/AvatarCatalog.kt` | CREATE | `AvatarSlot`, `ShopCategory`, `AvatarItem`, Katalogliste |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/avatar/Avatar.kt` | CREATE | `AvatarConfig`, `AvatarProfile`, `PurchaseOutcome`, Validierung |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/avatar/AvatarRepository.kt` | CREATE | Port |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/avatar/AvatarService.kt` | CREATE | Rechte, Validierung, Kauf, Speichern |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/ledger/LedgerEntry.kt` | UPDATE | `shopSourceKey(memberId, itemId)` |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/AvatarTables.kt` | CREATE | Exposed-Tabellen |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/LedgerWrites.kt` | CREATE | `balanceOf`/`insertEntry` gemeinsam (aus `ExposedRedemptionRepository` gezogen) |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/ExposedRedemptionRepository.kt` | UPDATE | nutzt `LedgerWrites.kt` (reines Refactoring) |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/ExposedAvatarRepository.kt` | CREATE | Adapter, atomarer Kauf |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/web/AvatarRoutes.kt` | CREATE | 5 Endpunkte |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/web/AvatarApiMappers.kt` | CREATE | Domäne ↔ API |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/GamificationModule.kt` | UPDATE | Koin |
| `backend/app/src/main/kotlin/de/familyplanner/app/Application.kt` | UPDATE | `avatarRoutes(get())` |
| `backend/gamification/build.gradle.kts` | UPDATE | Test-Property `avatar.definition` → `../api/avatar/avataaars.json`; `kotlinx-serialization-json` für den Test |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/avatar/AvatarCatalogTest.kt` | CREATE | feste ID-Liste, Konsistenz gegen JSON, Titel-/Preisgrenzen |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/avatar/AvatarServiceTest.kt` | CREATE | Rechte, Validierung, Kauf, Speichern |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/fakes/InMemoryGamificationRepositories.kt` | UPDATE | `InMemoryAvatarRepository` |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/fakes/GamificationFixture.kt` | UPDATE | `avatars`-Service |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/persistence/ExposedAvatarRepositoryTest.kt` | CREATE | Atomarität, Doppelkauf, Cascade, `CHECK` |
| `backend/app/src/test/kotlin/de/familyplanner/app/AvatarApiTest.kt` | CREATE | Vertragsprüfung, Rechte, Rangliste unverändert |
| `docs/03-design/gamification.md` | UPDATE | Abschnitt „Avatar & Shop (Phase 4a)“ |
| `docs/04-verification/traceability.md` | UPDATE | Zeilen REQ-GAME-030, -031 |
| `docs/01-requirements/gamification.prd.md` | UPDATE | Phase 4: Plan/Bericht 4a; offene Frage „Avatar-Technik“ abhaken (→ ADR-0017); „Item-Preise“: Standardpreise im Katalog |

## NOT Building

- Bildschirme: Avatar-Editor, Shop, Vorschau mit Item, Avatar in Rangliste/Board-Lane/„Heute“ → **4b**
- Kauf-Hinweis beim Sparziel (REQ-GAME-041, 2. Kriterium) → **4b** (App rechnet `balance − price < goal.progress.current`)
- Offline-Cache der Avatare/des Katalogs in der App → 4b
- Preise durch Eltern ändern (*Could*), Items verschenken/zurückgeben, Avatare für Eltern
- Server-seitiges SVG-Rendering, Avatar-Bilder als Datei/URL
- Badge-Regeln zu Avatar/Shop (z. B. „erstes Item gekauft“) – nicht im PRD-Katalog
- Änderungen an `Leaderboard`/`LeaderboardEntry` (App kombiniert in 4b selbst)
- Weitere DiceBear-Stile oder Stilwechsel durch Kinder

---

## Step-by-Step Tasks

### Task 1: Avatar-Definition als gemeinsamer Vertrag
- **ACTION**: `api/avatar/avataaars.json` + `api/avatar/README.md` anlegen; `api/README.md` ergänzen.
- **IMPLEMENT**:
  - `curl -sL -o api/avatar/avataaars.json https://cdn.jsdelivr.net/npm/@dicebear/styles@10.6.0/dist/avataaars.min.json`
    (≈ 125 KB, minifiziert, **nicht** formatieren/ändern). `shasum -a 256` ins README.
  - README (deutsch): Herkunft (DiceBear `@dicebear/styles` 10.6.0, Exporter für Figma), Lizenz Code MIT / Artwork
    „Avataaars“ von Pablo Stanley, „Free for personal and commercial use“, Namensnennung wörtlich aus `meta.license.text`;
    Verwendung (Backend-Test `AvatarCatalogTest`, App-Assets `core:ui`); Aktualisieren = neue Version laden, beide Testsuiten
    laufen lassen, Item-IDs dürfen nicht verschwinden.
- **MIRROR**: Stil von `api/README.md` (kurz, deutsch).
- **GOTCHA**: Datei ist Daten, kein Code – nie Teile daraus in Kotlin kopieren. `api/**` triggert Android- **und** Backend-CI.
- **VALIDATE**: `python3 -I -c "import json;d=json.load(open('api/avatar/avataaars.json'));print(sorted(d['components']))"` →
  `['accessories','clothes','clothesGraphic','eyebrows','eyes','facialHair','mouth','nose','top']`.

### Task 2: Spike – Renderer in `core:ui`
- **ACTION**: AndroidSVG einbinden, Assets einhängen, `AvatarLook`, `AvatarSvg`, `AvatarDefinition`, `AvatarImage` schreiben
  (TDD: `AvatarSvgTest` zuerst).
- **IMPLEMENT**:
  - `libs.versions.toml`: `androidsvg = "1.4"`, `androidsvg = { module = "com.caverock:androidsvg-aar", version.ref = "androidsvg" }`.
  - `core/ui/build.gradle.kts`: `implementation(libs.androidsvg)`, `implementation(libs.serialization.json)` (Plugin
    `kotlin.plugin.serialization` kommt schon über `familyplanner.android-library`); Assets:
    `android { sourceSets { getByName("main") { assets.srcDir(rootDir.resolve("../api/avatar")) } } }`
    (AGP 9: falls `srcDir` deprecated/entfernt → `assets.directories.add(...)` bzw.
    `androidComponents { onVariants { it.sources.assets?.addStaticSourceDirectory(rootDir.resolve("../api/avatar").path) } }`).
  - `AvatarLook` (data class, alle Werte = Item-IDs wie im Backend, z. B. `"top.shortFlat"`):
    `skin, top, hairColor, hatColor, eyes, eyebrows, mouth, clothes, clothesColor, background` (String) und
    `clothesGraphic, accessories, facialHair` (String?). Funktion `toSelection(): AvatarSelection` zerlegt `slot.wert`:
    - `components = mapOf("top" to …, "eyes" to …, "eyebrows" to …, "mouth" to …, "clothes" to …, "nose" to "default")`
      plus `clothesGraphic`/`accessories`/`facialHair`, wenn gesetzt.
    - `colors = mapOf("skin" to "#…", "hair" to hairColor, "facialHair" to hairColor, "hat" to hatColor,
      "clothes" to clothesColor, "accessories" to "#262e33")`, `background = "#…"`.
    - Item-ID ohne Punkt → `IllegalArgumentException` (Programmierfehler).
  - `AvatarSvg.render(definition: JsonObject, selection: AvatarSelection): String`:
    - Wurzel `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 280 280" width="280" height="280" fill="none" shape-rendering="auto">`
      (Breite/Höhe und Wurzelattribute aus `canvas` bzw. `attributes` lesen, nicht hart kodieren).
    - Zuerst `<circle cx="140" cy="140" r="140" fill="<background>"/>`, dann `canvas.elements` rekursiv:
      - `type == "element"` → `<name attr="…">children</name>`; Attributwert String → XML-escapen (`&`, `<`, `>`, `"`);
        Attributwert `{type:"color", name}` → `selection.colors[name]` (fehlt → erste Farbe aus `colors.<name>.values`).
      - `type == "component"` → Variante `selection.components[name]`; fehlt sie (optional nicht gesetzt) → nichts;
        unbekannte Variante → erste Variante der Komponente (Fallback, kein Absturz); sonst `<g attrs>` + Elemente der Variante
        (rekursiv, damit `clothesGraphic` in `graphicShirt` erscheint).
    - Reine Funktion, kein Android-Import → JVM-Unit-Test.
  - `AvatarDefinition.load(context): JsonObject` – liest `avataaars.json` aus den Assets einmal (`by lazy`/`AtomicReference`),
    `Json.parseToJsonElement(...).jsonObject`.
  - `@Composable fun AvatarImage(look: AvatarLook?, name: String, modifier: Modifier = Modifier, size: Dp = 40.dp)`:
    `look == null` → heutiger Platzhalter (Anfangsbuchstabe im Kreis, wie `LeaderboardList.kt:58-59` – dorthin in 4b
    verschoben); sonst `val svg = remember(look) { SVG.getFromString(AvatarSvg.render(definition, look.toSelection())) }`,
    `Canvas(Modifier.size(size).clip(CircleShape)) { drawIntoCanvas { c -> svg.documentWidth = this.size.width; svg.documentHeight = this.size.height; svg.renderToCanvas(c.nativeCanvas) } }`.
    `contentDescription` über `Modifier.semantics { contentDescription = "Avatar von $name" }` (String-Ressource in
    `core/ui/src/main/res/values/strings.xml`: `core_avatar_description`).
    Fehler beim Parsen (`SVGParseException`) → Platzhalter + `Log.w` (nicht still schlucken).
  - `@Preview` mit 4 Beispiel-Looks (kostenlos, Mütze, Brille+Bart, Motiv-Shirt).
- **MIRROR**: ANDROID_MODULE; Dateien klein halten (< 200 Zeilen je Datei).
- **IMPORTS**: `com.caverock.androidsvg.SVG`, `kotlinx.serialization.json.*`, `androidx.compose.ui.graphics.drawscope.drawIntoCanvas`,
  `androidx.compose.ui.graphics.nativeCanvas`.
- **GOTCHA**: `SVG`-Objekte sind nicht thread-safe → je Composable eigenes Objekt (über `remember`), nicht global teilen.
  Gradients (`linearGradient` mit `id`) sind je SVG-Dokument lokal – kein Konflikt. Keine `Picture`-Aufzeichnung
  (unter Robolectric unzuverlässig) – direkt `renderToCanvas`. Definition nicht im Composable parsen (ANR) – einmalig laden.
- **VALIDATE**: `cd android && ./gradlew :core:ui:testDebugUnitTest`

### Task 2b: Spike-Nachweis (Go/No-go)
- **ACTION**: `AvatarImageSpikeTest` (Robolectric, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, `createComposeRule`) rendert die
  4 Preview-Looks in 200 dp, `onRoot().captureToImage().asAndroidBitmap()` → PNG nach
  `build/avatar-spike/<name>.png`; Assertions: Bitmap nicht leer (> 30 % deckende Pixel), Hautfarbe des Looks kommt vor.
- **Go-Kriterien** (vom umsetzenden Agenten per Read-Tool auf den PNGs prüfen und im Bericht festhalten):
  1. Gesicht, Haare/Mütze, Kleidung an der richtigen Stelle (Transforms korrekt), keine schwarzen Flächen (Farbreferenzen aufgelöst);
  2. Motiv-Shirt zeigt die Grafik; optionale Slots fehlen sauber;
  3. Rendern eines Avatars < 50 ms im Test (grobe Messung, `measureTimeMillis` um `render` + `renderToCanvas`).
- **No-go → Plan B** (im ADR dokumentieren, nicht umsetzen ohne Rückfrage): (a) Rendering-Fehler → eigener Compose-Zeichner
  über `PathParser` (nur `path/rect/circle/linearGradient` nötig); (b) Lizenzbedenken → Stil „Open Peeps“ (CC0), gleicher
  Renderer, anderer Katalog. Bei No-go **stoppen** und den Nutzer fragen, bevor das Backend gebaut wird.
- **VALIDATE**: PNGs angesehen, Ergebnis + Messwerte notiert.

### Task 3: ADR-0017
- **ACTION**: `docs/02-architecture/adr/0017-avatar-bausteine.md` nach `0000-template.md` (deutsch, Status „Akzeptiert“ bei Go).
- **IMPLEMENT**: Kontext (PRD-Frage „Avatar-Technik“, Lizenzrisiko, offline, Pi ohne Rendering-Last); Entscheidung (Avataaars-
  Definition in `api/avatar/`, App-Renderer AndroidSVG, Katalog/Preise im Backend, Item-ID-Schema, Hintergrund eigener Slot,
  Namensnennung in der App ab 4b unter „Familie“ → „Über“ o. ä.); Alternativen (Server-SVG; VectorDrawables je Teil – Farben
  nicht tönbar; Open Peeps CC0 – keine Kleidung/Mützen; Personas CC BY – 4 Oberteile; selbst zeichnen – PRD „NOT Building“);
  Konsequenzen (Item-IDs dauerhaft, Definitions-Update nur mit Konsistenztest, ~125 KB Asset, AndroidSVG-Abhängigkeit
  seit 2019 stabil, Apache-2.0). Spike-Ergebnis (Testbilder, Messwert) eintragen.
- **VALIDATE**: ADR verlinkt in `docs/03-design/gamification.md` (Task 12).

### Task 4: OpenAPI-Vertrag 0.10.0 (API-first)
- **ACTION**: `info.version` → `0.10.0`; Pfade und Schemas ergänzen (nach dem Sparziel-Block bzw. nach `SavingsGoalResponse`).
- **IMPLEMENT**:
  - Parameter `AvatarItemId`: `name: itemId, in: path, required: true, schema: { type: string, pattern: '^[a-zA-Z]+\.[a-zA-Z0-9]+$', maxLength: 64 }`.
  - Pfade (alle `tags: [gamification]`, Responses-Set wie bestehende inkl. `401`, `429`):
    - `GET /api/v1/gamification/avatar/catalog` `getAvatarCatalog` → `200 AvatarCatalogResponse`
    - `GET /api/v1/gamification/avatars` `listAvatars` → `200 MemberAvatarListResponse`
    - `GET /api/v1/gamification/members/{memberId}/avatar` `getAvatarProfile` → `200 AvatarProfileResponse`, `400/403/404`
    - `PUT /api/v1/gamification/members/{memberId}/avatar` `saveAvatar` (Body `AvatarConfig`) → `200 AvatarProfileResponse`, `400/403/404/409`
    - `POST /api/v1/gamification/avatar/items/{itemId}/purchase` `purchaseAvatarItem` → `201 AvatarPurchaseResponse`, `400/403/404/409`
  - Schemas:
    - `AvatarSlot` enum `[skin, top, hairColor, hatColor, eyes, eyebrows, mouth, clothes, clothesColor, clothesGraphic, accessories, facialHair, background]`
    - `ShopCategory` enum `[HAIR, HEADWEAR, FACE, CLOTHES, ACCESSORIES, COLORS]`
    - `AvatarItem` `{id, slot: AvatarSlot, category: ShopCategory, title (1–40), price (0–1000; 0 = Grundsortiment)}`
    - `AvatarSlotInfo` `{slot, optional: boolean, items: [AvatarItem]}`; `AvatarCatalog` `{slots: [AvatarSlotInfo]}` (Editor-Reihenfolge)
    - `AvatarConfig` – Pflicht `skin, top, hairColor, hatColor, eyes, eyebrows, mouth, clothes, clothesColor, background`
      (string, Item-ID), optional+`nullable: true` `clothesGraphic, accessories, facialHair`
    - `MemberAvatar` `{memberId (uuid), avatar: AvatarConfig, updatedAt (date-time)}`
    - `AvatarProfile` `{memberId, avatar: AvatarConfig nullable, ownedItemIds: [string]}` (nur gekaufte, nicht das Grundsortiment)
    - `AvatarPurchase` `{item: AvatarItem, balance: integer}` (Guthaben nach dem Kauf)
    - Envelopes `AvatarCatalogResponse`, `MemberAvatarListResponse` (data: array), `AvatarProfileResponse`, `AvatarPurchaseResponse`
      exakt im Stil von `RewardResponse`/`RewardListResponse`.
- **MIRROR**: `api/openapi.yaml:1118-1137` (Einlösen), `2376-2465`.
- **GOTCHA**: Kein Feld `entries` (Generator benennt um). Enum-Werte von `AvatarSlot` in camelCase → der Kotlin-Generator
  erzeugt Konstanten wie `skin`/`hairColor` mit `@SerialName` – nach Task 4 `cd backend && ./gradlew :api-contract:compileKotlin`
  und generierte Namen prüfen, bevor die Mapper geschrieben werden. `maximum: 1000` für `price` (Katalog ≤ 30, Spielraum für *Could*).
- **VALIDATE**: `cd backend && ./gradlew :api-contract:compileKotlin` und `cd android && ./gradlew :core:api:compileDebugKotlin`.

### Task 5: Migration V13
- **ACTION**: `backend/gamification/src/main/resources/db/migration/V13__gamification_avatar.sql`
- **IMPLEMENT**:
```sql
-- GAME phase 4: avatar shop. Items are a fixed catalog in code (AvatarCatalog); item ids are never changed.
-- A purchase books a SHOP ledger entry (source_key shop:<member>:<item>) in the same transaction.
CREATE TABLE game_avatar_items (
  member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
  item_id TEXT NOT NULL,
  price INTEGER NOT NULL CHECK (price > 0),
  purchased_at INTEGER NOT NULL,
  PRIMARY KEY (member_id, item_id)
);

-- The avatar a child wears: one row per slot; no rows = no avatar yet.
CREATE TABLE game_avatar_slots (
  member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
  slot TEXT NOT NULL,
  item_id TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (member_id, slot)
);
```
- **GOTCHA**: Kein `CHECK` auf Slot-/Item-Werte (Katalog lebt im Code, Validierung im Service). Versionsnummer 13 – V1–V12 sind belegt.
- **VALIDATE**: `ExposedAvatarRepositoryTest` (Task 10) läuft gegen echte Migrationen.

### Task 6: Domäne `avatar`
- **ACTION**: `AvatarCatalog.kt`, `Avatar.kt` (TDD: `AvatarCatalogTest` zuerst).
- **IMPLEMENT**:
  - `enum class AvatarSlot(val key: String, val optional: Boolean)` in Editor-Reihenfolge (Tabelle oben); `key` = API-/DB-Wert.
  - `enum class ShopCategory { HAIR, HEADWEAR, FACE, CLOTHES, ACCESSORIES, COLORS }`.
  - `data class AvatarItem(val id: String, val slot: AvatarSlot, val category: ShopCategory, val title: String, val price: Int)`;
    `val isFree get() = price == 0`.
  - `object AvatarCatalog { val items: List<AvatarItem>; fun find(id: String): AvatarItem?; fun itemsOf(slot): List<AvatarItem>; fun defaultOf(slot): AvatarItem /* erstes kostenloses */ }`.
    Aufbau kompakt über einen Helfer je Slot, z. B. `items(AvatarSlot.EYES, ShopCategory.FACE, "default" to "Normal" free, "hearts" to "Herzaugen" costs 10)`
    – Lesbarkeit vor Kürze; Datei darf als deklarativer Katalog > 200 Zeilen werden (Begründung als Kommentar oben, wie
    `@Suppress` in `Badge.kt`). `const val AVATAR_ITEM_TITLE_MAX = 40`, `ITEM_ID_PATTERN = Regex("^[a-zA-Z]+\\.[a-zA-Z0-9]+$")`.
  - `data class AvatarConfig(val items: Map<AvatarSlot, String>)` (Item-IDs; fehlende optionale = nicht im Map).
  - `fun validated(config: AvatarConfig, owned: Set<String>): AvatarConfig`:
    Pflicht-Slot fehlt → `Validation("<Slot> fehlt")`; unbekannte ID → `Validation("Unbekanntes Teil: <id>")`;
    Item gehört zu anderem Slot → `Validation`; kostenpflichtig und nicht in `owned` → `Conflict("Noch nicht gekauft: <Titel>")`.
  - `data class MemberAvatar(val memberId: MemberId, val config: AvatarConfig, val updatedAt: Instant)`,
    `data class AvatarProfile(val memberId: MemberId, val avatar: AvatarConfig?, val ownedItemIds: List<String>)`.
  - `sealed interface PurchaseOutcome { data class Purchased(val balance: Int); data object AlreadyOwned; data class InsufficientBalance(val balance: Int, val price: Int) }`.
  - `ledger/LedgerEntry.kt`: `fun shopSourceKey(memberId: String, itemId: String) = "shop:$memberId:$itemId"`.
  - Robustheit beim Lesen: `fun sanitized(stored: Map<String, String>): AvatarConfig` – unbekannte Slot-Keys/Item-IDs verwerfen,
    fehlende Pflicht-Slots mit `defaultOf(slot)` füllen (Aufrufer loggt `warn`, wenn etwas ersetzt wurde).
- **MIRROR**: NAMING_CONVENTION, `Badge.kt` (deklarativer Katalog).
- **GOTCHA**: Farbitems haben Hex **klein** ohne `#`. `clothesGraphic` ohne `graphicShirt` ist erlaubt (wird nur nicht gezeigt).
- **VALIDATE**: `AvatarCatalogTest` grün.

### Task 7: Repository-Port
- **ACTION**: `avatar/AvatarRepository.kt`
- **IMPLEMENT**:
```kotlin
interface AvatarRepository {
    /** Stored slot -> item id of every child with an avatar. */
    suspend fun findAll(): Map<MemberId, StoredAvatar>
    suspend fun find(memberId: MemberId): StoredAvatar?
    /** Replaces all slot rows of [memberId] in one transaction. */
    suspend fun save(memberId: MemberId, items: Map<String, String>, at: Instant)
    suspend fun ownedOf(memberId: MemberId): Set<String>
    /** In one transaction: not owned, balance >= price, inserts game_avatar_items + SHOP debit (title "Shop: <title>"). */
    suspend fun purchase(memberId: MemberId, item: AvatarItem, debitEntryId: String, at: Instant): PurchaseOutcome
}
data class StoredAvatar(val items: Map<String, String>, val updatedAt: Instant)
```
- **MIRROR**: `RedemptionRepository.kt` (KDoc beschreibt die Transaktion).
- **VALIDATE**: kompiliert.

### Task 8: Exposed-Tabellen + Repository
- **ACTION**: `AvatarTables.kt`, `LedgerWrites.kt` (Refactoring), `ExposedAvatarRepository.kt`.
- **IMPLEMENT**:
  - `object AvatarItemTable : Table("game_avatar_items")` (`memberId`, `itemId` varchar 64, `price`, `purchasedAt` long, PK beide),
    `object AvatarSlotTable : Table("game_avatar_slots")` (`memberId`, `slot` varchar 20, `itemId` varchar 64, `updatedAt`, PK beide).
  - `LedgerWrites.kt`: `internal fun JdbcTransaction.balanceOf(memberId)` und `internal fun JdbcTransaction.insertEntry(entry)`
    1:1 aus `ExposedRedemptionRepository` (Zeilen 138-173) verschieben; dort die privaten Kopien löschen → bestehende
    Tests müssen unverändert grün bleiben.
  - `purchase`: `dbQuery(db) { if (owned) return AlreadyOwned; val balance = balanceOf(m); if (balance < price) return InsufficientBalance(balance, price); AvatarItemTable.insert{…}; insertEntry(LedgerEntry(LedgerEntryId(debitEntryId), m, EntryKind.SHOP, -price, "Shop: ${item.title}", null, shopSourceKey(m.value, item.id), m, at)); Purchased(balance - price) }`.
  - `save`: `dbQuery { AvatarSlotTable.deleteWhere { memberId eq m }; items.forEach { insert } }`.
  - `findAll`: alle Zeilen, `groupBy(memberId)`, `updatedAt` = max der Gruppe.
- **MIRROR**: REPOSITORY_PATTERN.
- **GOTCHA**: Titel „Shop: …“ passt in `game_ledger.title` (`GamificationTables.kt:38`, `TITLE_LENGTH = 200`; „Shop: “ + 40). `insert`, nie `insertIgnore`, sonst würde ein Fehler beim Ledger-Schreiben den Besitz trotzdem anlegen.
- **VALIDATE**: `cd backend && ./gradlew :gamification:test --tests '*ExposedRedemptionRepositoryTest*' --tests '*ExposedAvatarRepositoryTest*'`

### Task 9: `AvatarService`
- **ACTION**: `avatar/AvatarService.kt` (TDD: `AvatarServiceTest` zuerst, mit `InMemoryAvatarRepository` in den Fakes).
- **IMPLEMENT** (`class AvatarService(members, avatars: AvatarRepository, clock, newId = { UUID… })`):
  - `fun catalog(): List<Pair<AvatarSlot, List<AvatarItem>>>` – Slots in Reihenfolge.
  - `suspend fun all(): List<MemberAvatar>` – nur Mitglieder mit Rolle CHILD (Eltern-Zeilen ignorieren), sanitized, sortiert nach Name.
  - `suspend fun profile(actor, memberId)`: `requireChild(memberId)`; `!actor.isParent && actor.id != memberId` → `Forbidden("Nur eigener Avatar")`.
  - `suspend fun save(actor, memberId, config)`: `requireChild(memberId)`; `actor.id != memberId` → `Forbidden("Nur das Kind selbst gestaltet seinen Avatar")`
    (gilt auch für Eltern); `validated(config, avatars.ownedOf(memberId))`; `avatars.save(...)`; `log.info`; Rückgabe `profile`.
  - `suspend fun purchase(actor, itemId)`: Eltern → `Forbidden("Nur Kinder kaufen im Shop")`; `requireChild(actor.id)`;
    `AvatarCatalog.find(itemId) ?: NotFound("Teil nicht gefunden")`; `isFree` → `Conflict("Dieses Teil ist kostenlos")`;
    Outcome → `AlreadyOwned` → `Conflict("Gehört dir schon")`, `InsufficientBalance` → `Conflict("Nicht genug Guthaben: x von y Punkten")`.
  - Reihenfolge der Prüfungen: Rolle (403) → Eingabe (400, Validierung/Pattern) → Existenz (404) → Zustand (409).
- **MIRROR**: SERVICE_PATTERN, ERROR_HANDLING.
- **GOTCHA**: Kauf löst **keine** Badge-Auswertung aus (SHOP zählt nicht für Gesammelt) – `BadgeService` nicht injizieren.
- **VALIDATE**: `cd backend && ./gradlew :gamification:test --tests '*AvatarServiceTest*'`

### Task 10: Koin, Routen, Mapper, App
- **ACTION**: `GamificationModule.kt`: `single<AvatarRepository> { ExposedAvatarRepository(get()) }`, `single { AvatarService(get(), get(), clock) }`;
  `web/AvatarRoutes.kt` (`fun Route.avatarRoutes(avatars: AvatarService)` in `authenticate(AUTH_DEVICE)`), `web/AvatarApiMappers.kt`;
  `Application.kt` Zeile ~124: `avatarRoutes(get<AvatarService>())`.
- **IMPLEMENT**: Mapper `AvatarItem.toApi()`, `AvatarConfig.toApi()` (Slot-Key → Feld), `ApiAvatarConfig.toDomain()`
  (`null`-Felder weglassen), `MemberAvatar.toApi()`, `AvatarProfile.toApi()`, `toApi(purchase, item)`. Routen exakt wie Tabelle
  „Interaction Changes“; `POST …/purchase` → `HttpStatusCode.Created`.
- **MIRROR**: ROUTE_PATTERN, `RewardApiMappers.kt`.
- **VALIDATE**: `cd backend && ./gradlew :app:compileKotlin`

### Task 11: Tests (Unit → Integration → API)
- **ACTION**: `AvatarCatalogTest`, `AvatarServiceTest`, `ExposedAvatarRepositoryTest`, `AvatarApiTest`; Fakes/Fixture ergänzen;
  `gamification/build.gradle.kts`: `tasks.test { systemProperty("avatar.definition", rootDir.resolve("../api/avatar/avataaars.json").absolutePath) }`
  und `testImplementation(libs.serialization.json)` (Alias `serialization-json` existiert in `backend/gradle/libs.versions.toml:49`).
- **IMPLEMENT**: siehe Testing Strategy. `AvatarApiTest` nutzt `setupFamilyWithChild()`, `call(...)` (Vertragsprüfung an) und
  bucht Guthaben über `POST …/entries` wie `RewardsApiTest.credit`.
- **MIRROR**: TEST_STRUCTURE, `ExposedRedemptionRepositoryTest` (`@TempDir`, `DatabaseFactory.create`), `RewardsApiTest`.
- **VALIDATE**: `cd backend && ./gradlew :gamification:test :app:test`

### Task 12: Dokumentation
- **ACTION**:
  - `docs/03-design/gamification.md`: Abgedeckte Anforderungen um REQ-GAME-030/031 (Phase 4a) ergänzen; neuer Abschnitt
    „Avatar & Shop (Phase 4a)“ im Stil des Belohnungs-Abschnitts: Begriffe, Katalog (Verweis auf `AvatarCatalog.kt` + Regeln
    Item-ID/Preis 0/IDs nie ändern), Rechte-Tabelle, Endpunkte, Atomarität Kauf, Tabellen V13, Renderer in `core:ui` (Verweis ADR-0017), Tests.
  - `docs/04-verification/traceability.md`: Zeilen REQ-GAME-030 (`AvatarServiceTest`, `AvatarCatalogTest`, `AvatarSvgTest` /
    `ExposedAvatarRepositoryTest`, `AvatarApiTest` / App folgt 4b) und REQ-GAME-031 (Kauf), Status „Backend 4a umgesetzt; App 4b offen“;
    REQ-GAME-014-Zeile: `AvatarApiTest` (`REQ-GAME-014 purchase does not change leaderboard`) ergänzen.
  - `docs/01-requirements/gamification.prd.md`: Phase-4-Zeile → `in-progress`, PRP-Spalte „4a: Plan `.claude/PRPs/plans/avatar-spike-backend.plan.md`“
    (nach Umsetzung + Bericht); Open Question „Avatar-Technik“ abhaken mit Verweis ADR-0017; „Item-Preise“ abhaken: Standardpreise im Katalog, Eltern-Anpassung bleibt *Could*.
  - `docs/02-architecture/` Modulübersicht (`backend/gamification` Zeile): „Punkte, Badges, Belohnungen, Avatar-Shop“.
- **VALIDATE**: Links prüfen (`grep -n "0017" docs -r`).

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `AvatarCatalogTest` `REQ-GAME-031 catalog item ids never change` | Katalog | feste, sortierte ID-Liste (157 IDs, im Test ausgeschrieben) | ja |
| `AvatarCatalogTest` `REQ-GAME-030 every item exists in the avataaars definition` | JSON aus `avatar.definition` | jede Komponenten-Variante / Farbe vorhanden; `background` gültiges Hex | ja |
| `AvatarCatalogTest` `REQ-GAME-030 every required slot has a free default` | Katalog | `defaultOf(slot).price == 0` für alle Pflicht-Slots | – |
| `AvatarCatalogTest` limits | Katalog | Titel 1–40, Preis 0–1000, IDs eindeutig, passen auf `ITEM_ID_PATTERN` | ja |
| `AvatarServiceTest` `REQ-GAME-030 child saves avatar from free items and everyone sees it` | Pflicht-Slots kostenlos | `all()` enthält Avatar; Geschwister liest `all()` | – |
| `AvatarServiceTest` `REQ-GAME-030 child without avatar has null profile` | – | `avatar == null`, `ownedItemIds` leer | ja |
| `AvatarServiceTest` `REQ-GAME-030 missing required slot / unknown item / wrong slot → 400` | defekte Configs | `AppException.Validation` | ja |
| `AvatarServiceTest` `REQ-GAME-031 wearing an unowned paid item → 409` | `top.hat` ohne Kauf | `Conflict` | ja |
| `AvatarServiceTest` `REQ-GAME-031 balance 10, item 8 → balance 2, collected unchanged, item owned` | Guthaben 10 (MANUAL), Item à 8* | `Purchased(2)`, Konto Gesammelt 10, `ownedItemIds` enthält Item | – |
| `AvatarServiceTest` `REQ-GAME-031 balance 5, item 8 → 409 with missing points` | Guthaben 5 | `Conflict` „5 von 8“ | ja |
| `AvatarServiceTest` `REQ-GAME-031 owned item can be worn and taken off for free and not bought twice` | Kauf, `save` mit/ohne Item | `save` ok beide Male, zweiter Kauf `Conflict`, Guthaben unverändert | ja |
| `AvatarServiceTest` `REQ-GAME-031 free item cannot be bought` | `top.shortFlat` | `Conflict` | ja |
| `AvatarServiceTest` rights | Eltern/Geschwister | Eltern kaufen/speichern → 403; Geschwister `profile` → 403; Eltern `profile` ok; Ziel Eltern → 400; unbekannt → 404 | ja |
| `AvatarServiceTest` `REQ-GAME-014 purchase does not change leaderboard position` | 2 Kinder, eins kauft | `accounts.leaderboard()` gleich | – |
| `AvatarServiceTest` sanitized read | gespeicherte unbekannte ID im Fake | Pflicht-Slot → Default, optional → weg | ja |
| `AvatarSvgTest` | Look mit allen Slots | SVG enthält `viewBox="0 0 280 280"`, Hintergrund-Hex, Hautfarbe, keine `"type"`-Reste, wohlgeformt (über `DocumentBuilderFactory` parsebar) | – |
| `AvatarSvgTest` optional/unknown | ohne Brille / unbekannte Variante | keine Brillen-Pfade / Fallback auf erste Variante, kein Absturz | ja |
| `AvatarSvgTest` nested component | `clothes.graphicShirt` + `clothesGraphic.pizza` | Pizza-Pfade enthalten; mit `hoodie` nicht | ja |
| `AvatarSvgTest` escaping | Attribut mit `&` (künstliche Mini-Definition) | `&amp;` | ja |

\* Katalogpreise sind 10–30; für das PRD-Beispiel „10 / 8“ im Service-Test einen **Test-Katalog** nicht einführen – stattdessen
Werte an echtes Item anpassen (z. B. Guthaben 12, `eyes.hearts` à 10 → 2) und im Testnamen das PRD-Kriterium nennen.

### Integration (`ExposedAvatarRepositoryTest`)
- Kauf schreibt Besitz + `SHOP`-Buchung (`source_key shop:<m>:<i>`, Betrag −Preis, Titel „Shop: …“) in einer Transaktion.
- Zweiter Kauf → `AlreadyOwned`, genau eine Buchung.
- Zu wenig Guthaben → `InsufficientBalance`, nichts geschrieben.
- Rollback: vorab eine Ledger-Zeile mit gleichem `source_key` einfügen → Kauf wirft, **kein** Besitz-Eintrag (beweist `insert` statt `insertIgnore`).
- `save` ersetzt alle Slots; `findAll` liefert `updatedAt`; Mitglied löschen → Cascade auf beide Tabellen.
- `CHECK price > 0` greift.

### API (`AvatarApiTest`, Vertragsprüfung an)
- Katalog lesen (Kind + Eltern), Struktur: 13 Slots, Reihenfolge, `optional` korrekt.
- Kind speichert Avatar → `GET /avatars` als Eltern enthält ihn; Profil als Geschwister → 403.
- Kauf 201 → Konto: Guthaben gesunken, Gesammelt gleich, Verlauf „Shop: …“ `kind SHOP`; Rangliste unverändert.
- Kauf mit zu wenig Guthaben → 409; ungültige `itemId` (`a/b`, `top`) → 400; unbekannt `top.unknown` → 404; Eltern-Kauf → 403.

### Edge Cases Checklist
- [x] Leere Eingabe (fehlende Pflicht-Slots, kein Avatar gespeichert)
- [x] Maximale Größe (Item-ID-Länge 64, Titel 40)
- [x] Ungültige Typen (Item eines anderen Slots, ungültiges Pattern im Pfad)
- [x] Gleichzeitiger Zugriff (Doppelkauf → PK + `source_key` UNIQUE, eine Transaktion; Pool 1 + SERIALIZABLE wie 3a)
- [ ] Netzwerkfehler – App-Thema (4b)
- [x] Rechte (Eltern/Geschwister/fremdes Kind)

---

## Validation Commands

### Spike / Android
```bash
cd android && ./gradlew :core:ui:testDebugUnitTest
```
EXPECT: grün; PNGs unter `android/core/ui/build/avatar-spike/` angesehen (Go-Kriterien Task 2b)

```bash
cd android && ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerifyDebug assembleDebug
```
EXPECT: grün wie CI (neue Modelle aus dem Vertrag kompilieren, `core:ui` Kover ≥ 80 %)

### Static Analysis (Backend)
```bash
cd backend && ./gradlew ktlintCheck detekt
```
EXPECT: keine Funde

### Unit + Integration Tests
```bash
cd backend && ./gradlew :gamification:test :app:test
```
EXPECT: alle grün (inkl. unveränderter `ExposedRedemptionRepositoryTest` nach dem `LedgerWrites`-Refactoring)

### Full Test Suite (wie CI)
```bash
cd backend && ./gradlew check koverVerify && ./gradlew :app:buildFatJar
```
EXPECT: grün, Kover ≥ 80 %, Fat-JAR baut

### Database Validation
```bash
cd backend && FP_SETUP_TOKEN=test-setup-token-123456 FP_DB_PATH=/tmp/fp-avatar.db FP_TLS_ENABLED=false ./gradlew :app:run
```
```bash
sqlite3 /tmp/fp-avatar.db "select version, success from flyway_schema_history order by installed_rank"
```
EXPECT: V1–V13 erfolgreich, Tabellen `game_avatar_items`, `game_avatar_slots`. Zusätzlich auf einer **Kopie** der
Testinstanz-DB starten: Migration läuft, Konten/Belohnungen unverändert.

### Manual Validation (lokal gegen `:app:run`, curl)
- [ ] Familie einrichten, Kind koppeln (wie `deploy/README.md`)
- [ ] `GET /gamification/avatar/catalog` → 13 Slots, `top` mit 34 Items
- [ ] Kind `PUT /members/{id}/avatar` nur mit kostenlosen Items → 200; `GET /avatars` als Eltern → Avatar sichtbar
- [ ] Kind `PUT` mit `top.hat` → 409 „Noch nicht gekauft“
- [ ] Eltern buchen +40; Kind `POST /avatar/items/top.hat/purchase` → 201, `balance 10`; Konto: Gesammelt 40, Verlauf „Shop: Hut −30“
- [ ] Zweiter Kauf → 409; Kauf `top.winterHat1` (30) mit Guthaben 10 → 409 „10 von 30“
- [ ] Kind `PUT` mit `top.hat` → 200; Rangliste unverändert

---

## Acceptance Criteria
- [ ] Spike: Testbilder erfüllen die Go-Kriterien, ADR-0017 akzeptiert (bei No-go: gestoppt und Nutzer gefragt)
- [ ] Vertrag 0.10.0 additiv, Backend und App bauen mit den generierten Modellen
- [ ] Katalog konsistent mit `api/avatar/avataaars.json` (Test)
- [ ] Kauf atomar, Doppelkauf unmöglich, `SHOP` ändert Gesammelt/Rangliste nicht
- [ ] Rechte wie Tabelle; Fehlerreihenfolge 403 → 400 → 404 → 409
- [ ] Alle Validation Commands grün, keine Lint-/Detekt-Funde
- [ ] Doku (Moduldesign, Traceability, PRD, ADR) aktualisiert

## Completion Checklist
- [ ] Code folgt den Belohnungs-Mustern (Service/Repository/Routes/Mapper)
- [ ] Fehlermeldungen deutsch, KDoc englisch, Logging nur IDs
- [ ] Tests mit REQ-ID im Namen, AAA
- [ ] Keine hartkodierten Werte außerhalb des deklarativen Katalogs (Konstanten für Grenzen/Pattern)
- [ ] Keine Scope-Erweiterung (keine Screens, kein Cache, keine Eltern-Preise)
- [ ] Bericht `.claude/PRPs/reports/avatar-spike-backend-report.md` mit Spike-Ergebnis (Bilder beschrieben, Messwert)

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| AndroidSVG rendert Avataaars fehlerhaft (Transforms/Gradients) | L | H | Spike zuerst; Plan B eigener `PathParser`-Zeichner; Stopp + Rückfrage bei No-go |
| Robolectric Native Graphics liefert leere Bilder auf CI | M | M | Spike-PNG-Test nur lokal aussagekräftig – Assertion „> 30 % deckend“; falls CI leer rendert: Test mit `Assume` auf Native-Graphics absichern statt löschen, Ergebnis im Bericht |
| Lizenz „Free for personal and commercial use“ ohne einsehbaren Volltext | L | M | Privat, keine Weiterverbreitung; Namensnennung; Plan B Open Peeps (CC0) im ADR |
| Generierte Enum-Namen für camelCase-`AvatarSlot` unerwartet | M | L | Nach Task 4 generierte Klassen ansehen, Mapper danach schreiben |
| AGP 9 `assets.srcDir` geändert | M | L | Alternativen im Task 2 genannt |
| Katalogpflege bricht gespeicherte Avatare | L | M | Feste-ID-Test, `sanitized` beim Lesen |
| Kinder finden Preise zu hoch/niedrig | M | L | Preise im Testbetrieb (Phase 5) anpassen; Eltern-Preise bleiben *Could* |

## Notes
- Umsetzen in einer **neuen Session** (Standard Sonnet): `/ecc:prp-implement .claude/PRPs/plans/avatar-spike-backend.plan.md`.
- Task-Reihenfolge ist bindend: 1 → 2 → 2b (Go/No-go) → 3 → 4 … Backend erst nach Go.
- Phase 4b (App) braucht aus 4a: `AvatarImage`/`AvatarLook` in `core:ui`, Vertrag 0.10.0. In 4b: Platzhalter aus
  `LeaderboardList.kt` durch `AvatarImage` ersetzen, Board-Lane (`feature:tasks`) bekommt Avatare über `app` (Feature-Module
  kennen `gamification` nicht – Slot/Port wie `familyContent`), Namensnennung „Avataaars by Pablo Stanley“ sichtbar machen.
- Recherche-Rohdaten: Komponenten/Varianten-Listen oben stammen direkt aus `avataaars.min.json` 10.6.0 (2026-11-01).
