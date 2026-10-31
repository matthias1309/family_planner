# Punkte, Badges, Avatar & Belohnungen (Modul `GAME`)

## Problem Statement

Unsere drei Kinder (18, 16, 10) sammeln am Küchenboard schon Punkte für erledigte Aufgaben und schauen darauf wie
auf ein Leaderboard — aber die Punkte sind nur Striche: Man kann sie nicht ausgeben, das Kochen zählt nicht
automatisch mit, und außer dem Vergleich gibt es keinen Anreiz. Wir wollen die Punkte digital führen und ihnen einen
Wert geben: Badges wie in Videospielen, einen Avatar, den man mit Punkten ausstattet, und echte Belohnungen wie einen
Freizeitparkbesuch, die nur wir Eltern ausloben.

## Evidence

- Punkte werden am Küchenboard bereits gesammelt und als Rangliste wahrgenommen (Aussage Eltern).
- Die Kinder kochen im Essensplaner einmal pro Woche (REQ-MEAL-020 ff.); das zählt heute nicht als Punkt in der App.
- Erfolg ist, wenn die Kinder ihre Avatare **verwenden** (Aussage Eltern) — Annahme, dass auch der 18-Jährige einen
  Comic-Avatar nutzt (Aussage Eltern, im Testbetrieb zu prüfen).

## Proposed Solution

Ein Modul `gamification` führt für jedes Kind ein **Punktekonto** als unveränderliches Buchungsjournal. Punkte
entstehen aus abgenommenen Aufgaben (Ereignis aus `TASK`) und aus Kochen, das ein Elternteil im Essensplan bestätigt
(Ereignis aus `MEAL`). Jedes Kind hat zwei Werte: **Gesammelt** (steigt nur, bestimmt Rangliste und Badges) und
**Guthaben** (wird für Avatar-Items und echte Belohnungen ausgegeben) — so rutscht niemand in der Rangliste ab, weil
er sich eine Mütze kauft. **Badges** werden automatisch nach festen Regeln vergeben; es gibt eine Liste des noch
Erreichbaren und geheime Badges. Der **Avatar** wird aus freien Comic-Bausteinen zusammengesetzt; Kleidung, Mützen,
Frisuren usw. werden im Shop mit Guthaben gekauft. **Echte Belohnungen** loben nur die Eltern mit Preis aus; jedes Kind
spart mit eigenem Guthaben darauf, löst ein, und die Eltern bestätigen die Einlösung.

## Key Hypothesis

Wir glauben, dass digitale Punkte mit Rangliste, Badges, Avatar und echten Belohnungen das Küchenboard ersetzen und
die Kinder stärker motivieren.
Wir wissen, dass wir richtig liegen, wenn im Testbetrieb **jedes Testkind seinen Avatar eingerichtet und mindestens
ein Item gekauft hat** und das Küchenboard **4 Wochen** nicht mehr benötigt wird (gemeinsam mit
[tasks.prd.md](tasks.prd.md)).

## What We're NOT Building

- Punkte für Eltern — Eltern sind Spielleitung (Aussage Eltern).
- Badges, die man mit Punkten kauft — Badges werden verdient, nicht gekauft (Aussage Eltern).
- Eigene Badge-Regeln durch Eltern — fester Katalog im Code reicht für den Start.
- Gemeinsames Ansparen mehrerer Kinder auf eine Belohnung — jedes Kind spart für sich (Aussage Eltern).
- Getrennte Spartöpfe (Avatar-Punkte vs. Belohnungs-Punkte) — siehe Decisions Log; ein Sparziel reicht zunächst.
- Taschengeld/Geldwert der Punkte, Handel zwischen Kindern, Punktabzug als Strafe.
- Selbst gezeichnete Grafiken — wir nutzen freie Avatar-Bausteine.

## Success Metrics

| Metric | Target | How Measured |
|--------|--------|--------------|
| Avatar eingerichtet | jedes Testkind in Woche 1 | Daten: Avatar vom Standard abweichend |
| Avatar-Items gekauft | ≥ 1 je Testkind in 4 Wochen | Buchungsjournal (Shop-Käufe) |
| Küchenboard abgeschafft | 4 Wochen Testbetrieb ohne Küchenboard | Beobachtung der Eltern |
| Kochen zählt | 100 % der gekochten Kindergerichte bestätigt | Bestätigte vs. freigegebene Kindergerichte im Zeitraum |
| Echte Belohnung eingelöst | ≥ 1 im Testzeitraum (wenn eine ausgelobt ist) | Einlösungen |

## Open Questions

- [ ] **Avatar-Technik:** Bausteine als Vektorgrafiken in der App zusammensetzen oder SVG vom Backend? Spike am Anfang
  von Phase 5; Lizenz der Bausteine prüfen (Kandidaten: DiceBear-Stile mit CC0, „Avataaars“).
- [ ] **Item-Preise:** fest im Katalog oder von den Eltern einstellbar? Vorschlag: Standardpreise, Eltern dürfen
  später anpassen (Could).
- [x] **Rangliste:** nur „Gesammelt gesamt“ oder zusätzlich Woche/Monat? Vorschlag: gesamt im MVP; Zeitraum als Could,
  falls der Altersunterschied (10 vs. 18) die Rangliste unfair wirken lässt. *Entschieden 2026-10-25: gesamt.*
- [x] **Punkte fürs Kochen:** Standardwert? Vorschlag: einstellbar durch Eltern, Startwert 3 (Kochen ist mehr Arbeit
  als eine Karte). *Entschieden 2026-10-25: wie vorgeschlagen (einstellbar, Standard 3, Bereich 1–20).*
- [x] Startpunkte vom Küchenboard: Übernehmen die Eltern den aktuellen Stand je Kind als Startbuchung? (Vorschlag: ja,
  über REQ-GAME-013, zählt für Gesammelt und Guthaben.) *Entschieden 2026-10-25: ja.*

---

## Users & Context

**Primary Users**
- **Kinder (18, 16, 10):** sammeln Punkte, vergleichen sich, schauen Badges an, gestalten ihren Avatar, sparen auf
  Belohnungen.
- **Eltern (2):** Spielleitung — bestätigen Kochen, loben Belohnungen aus, bestätigen Einlösungen, korrigieren Punkte.
- Test zunächst nur auf zwei Testhandys (ein Eltern-, ein Kindgerät), kein Release an die Familie.

- **Current behavior**: Punkte als Striche am Küchenboard; Rangliste durch Hinschauen.
- **Trigger**: Eine Aufgabe ist abgenommen oder ein Kind hat gekocht.
- **Success state**: Kinder öffnen „Ich“, um Punkte, Badges und Avatar anzusehen; das Küchenboard ist weg.

**Job to Be Done**
Wenn ich im Haushalt etwas erledigt oder gekocht habe, möchte ich als Kind sehen, dass es zählt, und mir dafür etwas
holen — für meinen Avatar oder einen echten Ausflug —, damit sich Mithelfen lohnt.

**Non-Users**
Alle außer den fünf Familienmitgliedern; Eltern sammeln nicht.

---

## Solution Detail

### Fachliche Regeln

- **Buchungsjournal:** Jede Punkteänderung ist eine unveränderliche Buchung (Kind, Betrag, Art, Quelle, Zeitpunkt,
  optional Notiz). Arten: `TASK` (+), `COOKING` (+), `MANUAL` (±, nur Eltern), `SHOP` (−), `REWARD` (−),
  `REWARD_REFUND` (+). Korrekturen sind Gegenbuchungen, nie Änderungen.
- **Idempotenz:** Je Quelle (Karte, Kochtag) höchstens eine Gutschrift.
- **Gesammelt** = Summe aller Buchungen der Arten `TASK`, `COOKING` und `MANUAL` (eine negative Korrektur senkt
  also auch Gesammelt). **Guthaben** = Summe aller Buchungen. Käufe und Einlösungen senken nur das Guthaben.
- **Guthaben** kann nicht durch Käufe oder Einlösungen negativ werden; eine negative Eltern-Korrektur darf es (z. B.
  versehentliche Doppelgutschrift nach einem Kauf).
- **Kochpunkte:** Ein Elternteil bestätigt im Essensplan „gekocht“ für ein freigegebenes Kindergericht am oder nach dem
  Kochtag (REQ-MEAL-034) → Ereignis *Kochen bestätigt* → Gutschrift mit dem eingestellten Kochpunktwert.
- **Badges:** fester Katalog im Code; Regeln werten Journal und Ereignisse aus (z. B. „erste Aufgabe“, „10× gekocht“,
  „100 Punkte gesammelt“, „7 Tage in Folge eine Aufgabe“). Ein Badge wird je Kind höchstens einmal vergeben und nie
  entzogen. Geheime Badges erscheinen bis zur Vergabe nur als „???“ mit optionalem Hinweis.
- **Avatar:** Jedes Kind hat genau einen Avatar aus Bausteinen je Slot (z. B. Hautton, Frisur, Haarfarbe, Augen,
  Mütze, Oberteil, Brille, Hintergrund). Ein Grundsortiment ist kostenlos; weitere Items kosten Guthaben, gehören nach
  dem Kauf dauerhaft dem Kind und können beliebig an- und abgelegt werden.
- **Echte Belohnungen:** Eltern legen Titel, Preis (Punkte) und optional Beschreibung an und können sie deaktivieren.
  Einlösen bucht den Preis sofort ab (Status „wartet auf Eltern“); Eltern markieren „erfüllt“ oder lehnen mit
  Begründung ab (Rückbuchung). Ein Kind kann eine Belohnung als **Sparziel** markieren und sieht den Fortschritt.
- **Sichtbarkeit:** Alle sehen Rangliste, Badges und Avatare aller Kinder; Guthaben-Details und Einlösungen sieht nur
  das Kind selbst und die Eltern.

### Anforderungen

#### Punktekonto & Anzeige

##### REQ-GAME-010 Punkte für abgenommene Aufgaben
Als Kind möchte ich für jede abgenommene Aufgabe deren Punkte erhalten, damit sich Mithelfen lohnt.

**Abnahmekriterien**
- Given eine Karte mit 2 Punkten, When ein Elternteil sie abnimmt (REQ-TASK-023), Then steigen Gesammelt und Guthaben
  des Kindes um 2.
- Given das Ereignis kommt für dieselbe Karte ein zweites Mal, Then entsteht keine zweite Gutschrift.

##### REQ-GAME-011 Punkte fürs Kochen
Als Kind möchte ich für ein gekochtes Gericht Punkte erhalten, sobald ein Elternteil es bestätigt.

**Abnahmekriterien**
- Given ein bestätigtes Kochen (REQ-MEAL-034) und Kochpunktwert 3, Then steigen Gesammelt und Guthaben um 3.
- Given die Eltern ändern den Kochpunktwert, Then gilt der neue Wert für künftige Bestätigungen.

##### REQ-GAME-012 Punktestand und Verlauf
Als Kind möchte ich meinen Stand und woher die Punkte kommen sehen, damit ich verstehe, wofür ich was bekommen habe.

**Abnahmekriterien**
- Given Buchungen, When ich „Ich“ öffne, Then sehe ich Gesammelt, Guthaben und die Buchungen mit Datum, Art und Titel.
- Given ich bin ohne Heimnetz, Then sehe ich den letzten Stand mit Sync-Zeitpunkt.

##### REQ-GAME-013 Punkte manuell buchen
Als Elternteil möchte ich Punkte mit Begründung gutschreiben oder abziehen, damit ich den Stand vom Küchenboard
übernehmen und Fehler korrigieren kann.

**Abnahmekriterien**
- Given ich bin Elternteil, When ich einem Kind +12 mit „Übernahme Küchenboard“ buche, Then steigen Gesammelt und
  Guthaben um 12.
- Given ich buche −2 mit Begründung, Then sinken Gesammelt und Guthaben um 2, und die Buchung steht im Verlauf.
- Given ich bin Kind, Then kann ich nicht manuell buchen (403).

##### REQ-GAME-014 Rangliste
Als Familienmitglied möchte ich eine Rangliste der Kinder nach gesammelten Punkten sehen, wie heute am Küchenboard.

**Abnahmekriterien**
- Given drei Kinder, When ich die Rangliste öffne, Then sind sie nach Gesammelt absteigend sortiert, mit Avatar.
- Given ein Kind kauft ein Item, Then ändert sich seine Position in der Rangliste nicht.

#### Badges

##### REQ-GAME-020 Badges automatisch erhalten
Als Kind möchte ich Badges automatisch bekommen, wenn ich eine Bedingung erfülle, wie in Videospielen.

**Abnahmekriterien**
- Given die Regel „10× gekocht“, When das zehnte Kochen bestätigt wird, Then erhalte ich das Badge mit Datum und sehe
  beim nächsten Öffnen einen Hinweis.
- Given ich habe ein Badge, Then wird es mir nicht ein zweites Mal vergeben und nicht entzogen.

##### REQ-GAME-021 Badge-Übersicht
Als Kind möchte ich meine Badges und die noch erreichbaren sehen, damit ich weiß, worauf ich hinarbeiten kann.

**Abnahmekriterien**
- Given die Übersicht, Then sehe ich erhaltene Badges und offene Badges mit Bedingung und Fortschritt (z. B. 7/10).
- Given ein geheimes Badge, das ich nicht habe, Then sehe ich nur „???“ und ggf. einen Hinweis, nicht die Bedingung.
- Given ich schaue mir ein Geschwisterkind an, Then sehe ich dessen erhaltene Badges.

#### Avatar

##### REQ-GAME-030 Avatar gestalten
Als Kind möchte ich einen Comic-Avatar aus Bausteinen zusammenstellen, damit ich in der App erkennbar bin.

**Abnahmekriterien**
- Given ich habe noch keinen Avatar, When ich „Ich“ öffne, Then kann ich aus dem kostenlosen Grundsortiment je Slot
  wählen und sehe eine Vorschau.
- Given ich speichere, Then erscheint mein Avatar in Rangliste, Board-Lane und auf „Heute“.

##### REQ-GAME-031 Items im Shop kaufen
Als Kind möchte ich Kleidung, Mützen, Frisuren usw. mit meinem Guthaben kaufen, damit mein Avatar besonders wird.

**Abnahmekriterien**
- Given Guthaben 10 und ein Item für 8, When ich kaufe, Then sinkt das Guthaben auf 2, Gesammelt bleibt, und das Item
  gehört mir.
- Given Guthaben 5 und ein Item für 8, Then ist der Kauf nicht möglich und ich sehe, wie viele Punkte fehlen.
- Given ich besitze ein Item, Then kann ich es kostenlos an- und ablegen und nicht erneut kaufen.
- Given ein Item im Shop, When ich es antippe, Then sehe ich vor dem Kauf meinen Avatar mit dem Item.

#### Echte Belohnungen

##### REQ-GAME-040 Belohnung ausloben
Als Elternteil möchte ich echte Belohnungen mit Punktepreis anlegen, ändern und deaktivieren.

**Abnahmekriterien**
- Given ich bin Elternteil, When ich „Freizeitpark“ für 200 Punkte anlege, Then sehen alle Kinder die Belohnung.
- Given ich deaktiviere sie, Then ist sie nicht mehr einlösbar; laufende Einlösungen bleiben.
- Given ich bin Kind, Then kann ich keine Belohnung anlegen (403).

##### REQ-GAME-041 Auf eine Belohnung sparen
Als Kind möchte ich eine Belohnung als Sparziel wählen, damit ich sehe, wie weit ich bin.

**Abnahmekriterien**
- Given Sparziel „Freizeitpark“ (200) und Guthaben 120, Then sehe ich 120/200 auf „Ich“ und „Heute“.
- Given ein Item würde mein Guthaben unter den Fortschritt drücken, When ich kaufen will, Then werde ich darauf
  hingewiesen, kann aber trotzdem kaufen.

##### REQ-GAME-042 Belohnung einlösen
Als Kind möchte ich eine Belohnung mit meinem Guthaben einlösen.

**Abnahmekriterien**
- Given Guthaben ≥ Preis, When ich einlöse, Then wird der Preis abgebucht und die Einlösung wartet auf die Eltern;
  die Eltern erhalten einen Hinweis.
- Given Guthaben < Preis, Then ist Einlösen nicht möglich.

##### REQ-GAME-043 Einlösung bestätigen oder ablehnen
Als Elternteil möchte ich eine Einlösung als erfüllt markieren oder ablehnen.

**Abnahmekriterien**
- Given eine wartende Einlösung, When ich „erfüllt“ markiere, Then ist sie abgeschlossen.
- Given eine wartende Einlösung, When ich mit Begründung ablehne, Then wird der Preis zurückgebucht und das Kind sieht
  die Begründung.

#### Voraussetzung aus `MEAL`

- **REQ-MEAL-034** Kochen bestätigen — siehe [meals.prd.md](meals.prd.md).

### Core Capabilities (MoSCoW)

| Priority | Capability | Rationale |
|----------|------------|-----------|
| Must | Buchungsjournal mit Gesammelt/Guthaben (REQ-GAME-010–013) | Grundlage für alles Weitere; ersetzt die Striche |
| Must | Kochpunkte über Bestätigung im Essensplan (REQ-GAME-011, REQ-MEAL-034) | Ausdrücklicher Wunsch: Kochen zählt |
| Must | Rangliste (REQ-GAME-014) | Wird am Küchenboard schon so genutzt |
| Must | Avatar gestalten und Items kaufen (REQ-GAME-030/031) | Erfolgsmetrik „Kinder verwenden ihre Avatare“ |
| Must | Echte Belohnungen ausloben und einlösen (REQ-GAME-040/042/043) | Gibt den Punkten einen echten Wert |
| Should | Badges mit Übersicht und geheimen Badges (REQ-GAME-020/021) | Motivation über die Rangliste hinaus |
| Should | Sparziel (REQ-GAME-041) | Trennt gedanklich „sparen“ und „ausgeben“ ohne zweites Konto |
| Could | Rangliste je Woche/Monat | Falls die Gesamt-Rangliste unfair wirkt |
| Could | Eltern passen Item-Preise an | Feinsteuerung nach Testbetrieb |
| Won't | Gekaufte Badges, Eltern-Punkte, gemeinsames Sparen, getrennte Spartöpfe, eigene Badge-Regeln | Siehe „What We're NOT Building“ |

### MVP Scope

Alle *Must*-Anforderungen. Badges (Should) kommen in derselben Testphase dazu, weil sie im Testbetrieb mitgeprüft
werden sollen, blockieren aber Avatar und Belohnungen nicht.

### User Flow

1. Eltern übernehmen den Punktestand vom Küchenboard als Startbuchung je Kind.
2. Kind richtet seinen Avatar aus dem Grundsortiment ein.
3. Aufgaben werden abgenommen bzw. Kochen bestätigt → Punkte, ggf. ein neues Badge.
4. Kind kauft im Shop ein Item oder spart auf eine ausgelobte Belohnung und löst sie ein.
5. Eltern bestätigen die Einlösung, der Ausflug findet statt.

---

## Technical Approach

**Feasibility**: MITTEL — Journal, Rangliste und Belohnungen sind einfach; Aufwand und Risiko liegen im Avatar
(Grafikbausteine, Lizenz, Darstellung) und im ersten modulübergreifenden Ereignisfluss.

**Architecture Notes**
- Neues Backend-Modul `backend/gamification` und Android-Feature-Modul `android/feature/gamification` (ADR-0001,
  ADR-0010); `gamification` abonniert die Ereignisse *Aufgabe abgenommen* (`tasks`) und *Kochen bestätigt* (`meals`)
  über die Schnittstelle in `core` (ADR aus `TASK` Phase 2).
- Gutschrift in derselben Transaktion wie der auslösende Statuswechsel (SQLite), eindeutiger Schlüssel je Quelle →
  idempotent.
- Gesammelt/Guthaben werden aus dem Journal berechnet; bei Bedarf später zwischengespeichert (YAGNI).
- Badge-Regeln als reine Funktionen über Journal/Statistik, nach jeder Buchung ausgewertet; Katalog im Code.
- Avatar: Konfiguration als Zuordnung Slot → Item-ID im Backend; Darstellung in der App. Grafikbausteine liegen in der
  App (offline, keine externe Abhängigkeit); Shop-Katalog (IDs, Slot, Preis) im Backend, damit Käufe geprüft werden.
  Genaues Vorgehen nach Spike → ADR.
- API zuerst (ADR-0004), nur additiv; Familie nutzt die aktuelle App weiter.
- Hinweise (neues Badge, Einlösung, Ablehnung) per Hintergrund-Abgleich im Heimnetz (ADR-0013).

**Technical Risks**

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Lizenz der Avatar-Bausteine erlaubt Nutzung/Änderung nicht | M | Nur CC0/MIT-lizenzierte Sets; Lizenzprüfung im Spike, Ergebnis im ADR |
| Avatar-Darstellung aus Bausteinen sieht in Compose schlecht aus oder ist aufwendig | M | Spike vor der Umsetzung; notfalls kleineres Set mit weniger Slots |
| Doppelte Gutschrift durch doppelte Ereignisse | M | Eindeutiger Schlüssel je Quelle, Integrationstest |
| Rangliste demotiviert das jüngste Kind | M | Im Testbetrieb beobachten; Zeitraum-Rangliste als Could |
| Kinder geben alles für Items aus und erreichen nie eine echte Belohnung | M | Sparziel mit Hinweis; Preise nach Testbetrieb anpassen |
| Testdaten in Familien-Datenbank | H | Testinstanz aus `TASK` Phase 1 |

---

## Implementation Phases

<!--
  STATUS: pending | in-progress | complete
  PARALLEL: phases that can run concurrently (e.g., "with 3" or "-")
  DEPENDS: phases that must complete first (e.g., "1, 2" or "-")
  PRP: link to generated plan file once created
  Abhängigkeiten auf TASK-Phasen sind als „TASK n“ notiert.
-->

| # | Phase | Description | Status | Parallel | Depends | PRP Plan |
|---|-------|-------------|--------|----------|---------|----------|
| 1 | Punktekonto | Journal, Punkte aus Aufgaben und Kochen (inkl. REQ-MEAL-034), manuelle Buchung, Stand, Verlauf, Rangliste, Bereich „Ich“ — aufgeteilt in 1a Backend/API und 1b App (REQ-GAME-010–014) | in-progress | - | TASK 2 (1b: TASK 1) | 1a: `.claude/PRPs/reports/punktekonto-backend-report.md` (Backend umgesetzt); 1b: `.claude/PRPs/plans/punktekonto-app.plan.md` → Bericht `.claude/PRPs/reports/punktekonto-app-report.md` (App umgesetzt, Geräte-Abnahme offen) |
| 2 | Badges | Badge-Katalog, Regeln, Vergabe, Übersicht inkl. geheimer Badges (REQ-GAME-020/021) — aufgeteilt in 2a Backend/API und 2b App | in-progress | with 3, 4 | 1 | 2a: `.claude/PRPs/plans/badges-backend.plan.md`; 2b: Plan folgt |
| 3 | Echte Belohnungen | Ausloben, Sparziel, Einlösen, Bestätigen (REQ-GAME-040–043) | pending | with 2, 4 | 1 | - |
| 4 | Avatar & Shop | Spike + ADR zu Bausteinen/Lizenz, Avatar gestalten, Shop, Avatar in Rangliste/Board (REQ-GAME-030/031) | pending | with 2, 3 | 1 | - |
| 5 | Testbetrieb | 4 Wochen auf zwei Testhandys gegen die Testinstanz; Küchenboard parallel abbauen; Messung | pending | - | 2, 3, 4, TASK 3 | - |

### Phase Details

**Phase 1: Punktekonto**
- **Goal**: Punkte entstehen automatisch und sind für jeden sichtbar.
- **Scope**: Modul `gamification`, Abonnement beider Ereignisse, Kochbestätigung im Essensplan (`meals`, Backend + App),
  Kochpunktwert-Einstellung, manuelle Buchung, Bereich „Ich“ mit Stand und Verlauf, Rangliste, Eltern-Ansicht unter
  „Familie“.
- **Success signal**: Abnahme einer Karte und Bestätigung eines Kochens ergeben auf dem Testhandy genau die erwarteten
  Punkte; Startbuchung übernimmt den Küchenboard-Stand.

**Phase 2: Badges**
- **Goal**: Erfolge werden wie in Videospielen sichtbar.
- **Scope**: Badge-Katalog (ca. 10–15 Badges, davon einige geheim), Regelauswertung nach jeder Buchung, Übersicht mit
  Fortschritt, Hinweis bei neuem Badge.
- **Success signal**: Badges werden in Tests und auf dem Testhandy korrekt vergeben; geheime bleiben verborgen.

**Phase 3: Echte Belohnungen**
- **Goal**: Punkte haben einen echten Wert.
- **Scope**: Eltern-Verwaltung unter „Familie“, Kind-Ansicht unter „Ich“, Sparziel, Einlösen mit Abbuchung,
  Bestätigen/Ablehnen mit Rückbuchung, Hinweise.
- **Success signal**: Eine Testbelohnung lässt sich auf den Testhandys ausloben, ansparen, einlösen und bestätigen.

**Phase 4: Avatar & Shop**
- **Goal**: Jedes Kind hat einen Avatar, den es mit Punkten ausbaut.
- **Scope**: Spike (Bausteine, Lizenz, Darstellung in Compose) → ADR; Shop-Katalog im Backend, Kauf, Besitz,
  An-/Ablegen; Avatar-Editor; Avatar in Rangliste, Board-Lane und „Heute“.
- **Success signal**: Testkind gestaltet einen Avatar und kauft ein Item; Avatar erscheint überall.

**Phase 5: Testbetrieb**
- **Goal**: Hypothesen aus beiden PRDs prüfen, bevor das Feature an die Familie geht.
- **Scope**: 4 Wochen auf den beiden Testhandys, Kennzahlen aus Daten und Beobachtung, Liste der Änderungen für den
  Familien-Release.
- **Success signal**: Metriken aus „Success Metrics“ beider PRDs erreicht oder bewusst angepasst.

### Parallelism Notes

Nach Phase 1 sind Badges, Belohnungen und Avatar fachlich unabhängig (eigene Tabellen und Routen). Alle drei bauen aber
Screens im Bereich „Ich“ und ändern `api/openapi.yaml` — parallel nur in getrennten Sessions mit klaren Dateigrenzen,
OpenAPI-Änderungen nacheinander zusammenführen. Phase 4 hat das größte Risiko (Spike) und sollte zuerst starten,
wenn nur eine Phase gleichzeitig läuft.

---

## Decisions Log

| Decision | Choice | Alternatives | Rationale |
|----------|--------|--------------|-----------|
| Kochpunkte (2026-10-24) | Elternteil bestätigt „gekocht“ im Essensplan, Punkt geht ans Kind | Kochen als Karte auf dem Board | Aussage Eltern: einfach im Essensplan bestätigen |
| Zwei Werte (2026-10-24) | Gesammelt (Rangliste/Badges) und Guthaben (ausgeben) | Ein Punktestand | Wie Habitica XP/Gold: Ausgeben darf in der Rangliste nicht bestrafen; Zustimmung Eltern |
| Badges (2026-10-24) | Automatisch nach Regeln, Übersicht mit Erreichbarem, geheime Badges | Mit Punkten kaufen | Aussage Eltern: „wie in Videospielen“ |
| Eltern (2026-10-24) | Nur Spielleitung, keine Punkte | Eltern spielen mit | Aussage Eltern |
| Avatar-Stil (2026-10-24) | Comic-Avatar für alle Kinder | Schlichter Stil für Ältere | Aussage Eltern: auch der 18-Jährige nutzt Comic |
| Belohnungen (2026-10-24) | Nur Eltern loben aus mit Preis; jedes Kind spart für sich | Gemeinsames Sparen | Aussage Eltern |
| Spartöpfe (2026-10-24) | Ein Guthaben plus optionales Sparziel mit Hinweis beim Kauf | Getrennte Konten für Avatar und Belohnungen | KISS: ein Konto ist für Kinder verständlicher; Sparziel macht das Sparen sichtbar. Nach dem Testbetrieb neu bewerten, falls Items das Sparen verhindern |
| Phase 1a (2026-10-25) | Offene Fragen Rangliste/Kochpunkte/Startpunkte wie vorgeschlagen entschieden; Gutschrift per Ereignis mindestens einmal + idempotent (ADR-0016) statt „derselben Transaktion“ | Gutschrift in Statuswechsel-Transaktion | Modulgrenze `tasks`/`meals` ↔ `gamification`; Details in `docs/03-design/gamification.md` |
| Journal | Unveränderliche Buchungen, Korrektur per Gegenbuchung | Punktestand als Zahl überschreiben | Nachvollziehbar für Kinder und Eltern; Immutability-Regel des Projekts |
| Grafiken | Freie Comic-Bausteine | Selbst zeichnen; KI-generiert | Kein Grafikaufwand; Lizenz klar prüfbar |

---

## Research Summary

**Market Context**
- Habitica: Avatar mit kaufbarer Ausrüstung, getrennte Erfahrung (steigt) und Gold (wird ausgegeben); Erfolge
  („Achievements“) automatisch. (Aus Produktkenntnis, nicht neu recherchiert.)
- OurHome, S'moresUp, Joon: Punkte für Aufgaben, Eltern definieren echte Belohnungen und bestätigen Einlösungen.
- Videospiel-Achievements (Steam, Xbox): Liste offener Erfolge mit Fortschritt, „versteckte Erfolge“ bis zur
  Freischaltung.
- Avatar-Baukästen: DiceBear (mehrere Stile, teils CC0, teils CC BY 4.0), „Avataaars“ (frei nutzbar) — Lizenz je Stil
  im Spike prüfen.

**Technical Context**
- Kindergericht kennt die Zustände `SUBMITTED / APPROVED / REJECTED` (Einreichung) bzw. zusätzlich `NOT_SUBMITTED` /
  `CANCELLED`; einen Zustand „gekocht“ gibt es nicht → REQ-MEAL-034.
- Rollen und `RoleGuard` vorhanden; Offline-Cache (ADR-0011) und Hintergrund-Abgleich mit lokalen Hinweisen
  (ADR-0013) wiederverwendbar.

---

*Generated: 2026-10-24*
*Status: DRAFT - needs validation*
