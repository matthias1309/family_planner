# Aufgaben-Board (Modul `TASK`)

## Problem Statement

In unserer Küche hängt ein Kanban-Board mit vorgefertigten Magnetschildern: Die Eltern schieben Aufgaben in die
Todo-Spalte eines Kindes, das Kind entscheidet selbst, wann es sie erledigt, und jede erledigte Karte bringt einen
Punkt. Das Board funktioniert, ist aber nur in der Küche sichtbar, die Punkte werden von Hand gezählt, und für die
geplanten Belohnungen (Badges, Avatar, echte Ausflüge — siehe [gamification.prd.md](gamification.prd.md)) braucht es
die Daten digital. Gleichzeitig wird der Startbildschirm der App mit jedem Feature voller.

## Evidence

- Heutiger Prozess: physisches Board mit Magnetschildern, eine Reihe (Swimlane) je Kind, Spalten Todo/Abnahme/Done
  (Aussage Eltern).
- Die Kinder sammeln dort bereits Punkte und behandeln das Board „mehr oder weniger“ als Leaderboard (Aussage Eltern).
- Startbildschirm ist heute eine Spalte von Buttons, die jedes Feature-Modul ergänzt (`MealsHomeActions` in
  `android/app/.../AppNavHost.kt`) — mit `TASK` und `GAME` kämen weitere hinzu.

## Proposed Solution

Ein Modul `tasks` bildet das Küchenboard 1:1 nach: Die Eltern pflegen einen **Aufgabenkatalog** (die Magnetschilder)
mit Punktwert je Aufgabe und weisen Aufgaben einem Kind zu. Jedes Kind hat eine Swimlane mit **Todo → Abnahme →
Done**; das Kind meldet eine Aufgabe als erledigt, ein Elternteil nimmt sie ab. Die Abnahme löst ein Ereignis aus, aus
dem das Modul `GAME` die Punkte bucht — `tasks` kennt keine Punktekonten.
Zugleich bekommt die App eine **untere Navigationsleiste**, damit Essen, Aufgaben und Punkte gleichrangige Bereiche
werden und der Startbildschirm nicht weiter wächst.

## Key Hypothesis

Wir glauben, dass ein digitales Board mit derselben Logik wie das Küchenboard das Papierboard ersetzt.
Wir wissen, dass wir richtig liegen, wenn das Küchenboard **4 Wochen lang nicht mehr benutzt** wird und alle
Aufgaben in der App zugewiesen und abgenommen werden.

## What We're NOT Building

- Aufgaben, die Kinder sich selbst anlegen — heute verteilen nur die Eltern (Aussage Eltern).
- Automatisch wiederkehrende Aufgaben (Zeitpläne) — die Eltern verteilen von Hand aus dem Katalog.
- Fälligkeitsdaten und Erinnerungen an Aufgaben — das Kind entscheidet, wann es etwas tut.
- Ein Tablet/Wanddisplay in der Küche — jeder sieht das Board auf dem eigenen Handy (Aussage Eltern).
- Aufgaben für Eltern — Eltern sind Spielleitung.
- Punktekonten, Rangliste, Badges — gehören zu `GAME`.

## Success Metrics

| Metric | Target | How Measured |
|--------|--------|--------------|
| Küchenboard abgeschafft | ja, nach 4 Wochen Testbetrieb | Beobachtung der Eltern |
| Aufgaben laufen über die App | 100 % der zugewiesenen Aufgaben im Testzeitraum | Board-Daten (zugewiesen → abgenommen) |
| Wartezeit in „Abnahme“ | Median < 24 h | Zeitstempel „gemeldet“ → „abgenommen“ |

## Open Questions

- [ ] **Testumgebung:** Die Testhandys sollen nicht mit Familiendaten arbeiten. Vorschlag: zweiter Container
  `family-planner-test` auf dem Pi (eigener `./data`, Port **8097**) und ein App-Build mit `fp.serverUrl` auf diesen
  Port. Zu klären bei der Planung von Phase 1.
- [ ] Wie lange bleiben Karten in „Done“ sichtbar? Vorschlag: 7 Tage, danach in einer Verlaufsliste.
- [ ] Sollen Eltern eine Abnahme zurücknehmen können? Vorschlag: nein; Fehler korrigieren die Eltern über eine
  manuelle Punktebuchung (REQ-GAME-013).

---

## Users & Context

**Primary Users**
- **Eltern (2):** pflegen den Katalog, verteilen Aufgaben, nehmen erledigte Aufgaben ab.
- **Kinder (18 J. dauerhaft, 16 J. und 10 J. je 50 %):** erledigen ihre Aufgaben, melden sie, schauen auf die Lanes
  der Geschwister.
- Alle haben ein eigenes Android-Handy; Test zunächst nur auf zwei Testhandys (ein Eltern-, ein Kindgerät).

- **Current behavior**: Magnetschild in die Todo-Spalte schieben; Kind schiebt nach „Abnahme“; Eltern nach „Done“.
- **Trigger**: Eltern sehen, dass etwas zu tun ist, und verteilen Schilder.
- **Success state**: Alle Aufgaben stehen in der App; niemand schaut mehr aufs Küchenboard.

**Job to Be Done**
Wenn im Haushalt etwas zu tun ist, möchte ich als Elternteil mit zwei Tippern eine vorbereitete Aufgabe einem Kind
geben und später abnehmen, damit die Arbeit fair verteilt und anerkannt wird — ohne Magnettafel.

**Non-Users**
Alle außer den fünf Familienmitgliedern.

---

## Solution Detail

### Fachliche Regeln

- **Katalog:** Eine Katalogaufgabe hat Titel (Pflicht, max. 40 Zeichen), Punktwert (ganze Zahl 1–100, Standard 1) und
  optional ein Emoji als Symbol. Katalogaufgaben werden archiviert statt gelöscht, damit der Verlauf lesbar bleibt.
- **Karte:** Zuweisen erzeugt eine **Karte** in der Todo-Spalte eines Kindes. Titel, Symbol und Punktwert werden
  **kopiert** (wie Gericht aus Rezept, ADR-0012); spätere Katalogänderungen wirken nicht auf bestehende Karten.
  Dieselbe Katalogaufgabe kann mehreren Kindern und einem Kind mehrfach zugewiesen werden.
- **Status:** `TODO → REVIEW → DONE`.
  - Kind: `TODO → REVIEW` („erledigt melden“) und `REVIEW → TODO` (zurückziehen).
  - Eltern: `REVIEW → DONE` (abnehmen), `REVIEW → TODO` (zurückweisen, optional mit Begründung),
    `TODO → DONE` (direkt abnehmen, wenn das Kind das Melden vergessen hat), Karte entfernen (nur `TODO`/`REVIEW`).
  - `DONE` ist endgültig.
- **Ereignis:** Beim Übergang nach `DONE` entsteht genau einmal das Ereignis *Aufgabe abgenommen* (Karte, Kind,
  Punktwert, Zeitpunkt). `GAME` bucht daraus die Punkte.
- **Sichtbarkeit:** Wie am Küchenboard sehen alle alle Lanes; ein Kind verändert nur Karten der eigenen Lane.
- **Offline:** Das Board ist aus dem Cache lesbar (ADR-0011); Änderungen nur im Heimnetz.

### Anforderungen

#### Navigation (UI-Cleanup)

##### REQ-TASK-001 Untere Navigationsleiste
Als Familienmitglied möchte ich die Bereiche der App über eine feste Leiste erreichen, damit der Startbildschirm
übersichtlich bleibt.

**Abnahmekriterien**
- Given ich bin angemeldet, When ich die App öffne, Then sehe ich unten die Ziele **Heute · Essen · Aufgaben · Ich**
  (Kind) bzw. **Heute · Essen · Aufgaben · Familie** (Eltern).
- Given ich bin auf der Hauptseite eines Bereichs und wechsle in einen anderen, When ich zurückwechsle, Then behält der
  Bereich seinen Zustand (die Leiste erscheint nur auf den Hauptseiten, ADR-0014).
- Given ich bin im Bereich „Essen“, When ich ihn öffne, Then erreiche ich Woche, Planung, Rezepte und Einkaufsliste
  ohne Umweg über den Startbildschirm.
- Given ein Benachrichtigungs- oder Teilen-Link (Plan öffnen, Rezept importieren), When er ankommt, Then öffnet sich
  der passende Bereich wie bisher.

##### REQ-TASK-002 Startseite „Heute“
Als Familienmitglied möchte ich auf einen Blick sehen, was heute für mich ansteht, damit ich nicht suchen muss.

**Abnahmekriterien**
- Given ich öffne „Heute“, Then sehe ich das heutige Gericht und meine offenen Aufgaben (Kind) bzw. die Anzahl der
  Aufgaben in „Abnahme“ (Eltern).
- Given ich tippe auf einen Eintrag, Then lande ich im passenden Bereich.

##### REQ-TASK-003 Abnahme-Hinweis in der Leiste
Als Elternteil möchte ich an der Leiste sehen, dass Aufgaben auf Abnahme warten, damit nichts liegen bleibt.

**Abnahmekriterien**
- Given n > 0 Karten in „Abnahme“, When ich die App öffne, Then zeigt das Ziel „Aufgaben“ ein Badge mit n.

#### Aufgabenkatalog

##### REQ-TASK-010 Katalog pflegen
Als Elternteil möchte ich vorbereitete Aufgaben mit Punktwert anlegen, ändern und archivieren, damit ich sie wie
Magnetschilder schnell verteilen kann.

**Abnahmekriterien**
- Given ich bin Elternteil, When ich „Zimmer aufsaugen“ mit 2 Punkten anlege, Then steht die Aufgabe im Katalog.
- Given eine Katalogaufgabe, When ich den Punktwert auf 3 ändere, Then haben bereits zugewiesene Karten weiterhin 2.
- Given eine Katalogaufgabe, When ich sie archiviere, Then ist sie nicht mehr zuweisbar, bestehende Karten bleiben.
- Given ich bin Kind, When ich den Katalog öffnen will, Then ist das nicht möglich (Server antwortet 403).

#### Board

##### REQ-TASK-020 Aufgabe zuweisen
Als Elternteil möchte ich eine oder mehrere Katalogaufgaben einem Kind zuweisen, damit sie in dessen Todo-Spalte
stehen.

**Abnahmekriterien**
- Given der Katalog, When ich ein Kind und zwei Aufgaben auswähle und zuweise, Then stehen zwei Karten in dessen Todo.
- Given eine Aufgabe ist dem Kind schon zugewiesen, When ich sie erneut zuweise, Then entsteht eine zweite Karte.

##### REQ-TASK-021 Board ansehen
Als Familienmitglied möchte ich das Board mit einer Lane je Kind sehen, damit ich weiß, wer was zu tun hat.

**Abnahmekriterien**
- Given ich bin Kind, When ich „Aufgaben“ öffne, Then sehe ich zuerst meine Lane mit Todo, Abnahme und Done und kann
  zu den Lanes der Geschwister wechseln.
- Given ich bin Elternteil, When ich „Aufgaben“ öffne, Then sehe ich alle Lanes und eine Sammelansicht „Zur Abnahme“.
- Given ich bin ohne Heimnetz, When ich das Board öffne, Then sehe ich den letzten Stand mit Sync-Zeitpunkt.

##### REQ-TASK-022 Erledigt melden
Als Kind möchte ich eine Aufgabe als erledigt melden, damit ein Elternteil sie abnimmt.

**Abnahmekriterien**
- Given eine Karte in meinem Todo, When ich „erledigt“ tippe, Then steht sie in „Abnahme“.
- Given eine Karte in meiner „Abnahme“, When ich sie zurückziehe, Then steht sie wieder in Todo.
- Given eine Karte in der Lane eines Geschwisters, When ich sie verschieben will, Then ist das nicht möglich.

##### REQ-TASK-023 Aufgabe abnehmen oder zurückweisen
Als Elternteil möchte ich gemeldete Aufgaben abnehmen oder zurückweisen, damit nur Erledigtes Punkte bringt.

**Abnahmekriterien**
- Given eine Karte in „Abnahme“, When ich abnehme, Then steht sie in Done und das Ereignis *Aufgabe abgenommen* wird
  genau einmal ausgelöst.
- Given eine Karte in „Abnahme“, When ich mit Begründung zurückweise, Then steht sie in Todo und das Kind sieht die
  Begründung.
- Given eine Karte in Todo, When ich sie direkt abnehme, Then steht sie in Done (Ereignis wie oben).
- Given eine Karte in Done, When ich oder das Kind sie verschieben will, Then ist das nicht möglich.
- Given zwei Eltern nehmen dieselbe Karte gleichzeitig ab, Then entsteht das Ereignis nur einmal.

##### REQ-TASK-024 Karte entfernen
Als Elternteil möchte ich eine falsch zugewiesene Karte entfernen, damit das Board stimmt.

**Abnahmekriterien**
- Given eine Karte in Todo oder Abnahme, When ich sie entferne, Then ist sie nicht mehr auf dem Board und es gibt
  keine Punkte.

##### REQ-TASK-025 Done-Spalte aufräumen
Als Familienmitglied möchte ich in Done nur die letzten Tage sehen, damit die Spalte nicht endlos wächst.

**Abnahmekriterien**
- Given eine Karte wurde vor mehr als 7 Tagen abgenommen, When ich das Board öffne, Then steht sie nicht mehr in Done,
  sondern im Verlauf der Lane.

#### Benachrichtigungen

##### REQ-TASK-030 Status-Hinweise
Als Familienmitglied möchte ich über Änderungen an meinen Karten informiert werden, damit ich nicht nachschauen muss.

**Abnahmekriterien**
- Given ein Kind meldet eine Aufgabe, When das Elterngerät im Heimnetz abgleicht, Then erhalten die Eltern einen
  Hinweis (wie REQ-MEAL-053, ADR-0013).
- Given ein Elternteil weist zu, nimmt ab oder weist zurück, When das Kindgerät abgleicht, Then erhält das Kind einen
  Hinweis.

### Core Capabilities (MoSCoW)

| Priority | Capability | Rationale |
|----------|------------|-----------|
| Must | Untere Navigationsleiste (REQ-TASK-001) | Ohne sie passt `TASK`/`GAME` nicht mehr sinnvoll in die App |
| Must | Katalog pflegen (REQ-TASK-010) | Entspricht den vorgefertigten Magnetschildern |
| Must | Zuweisen, ansehen, melden, abnehmen (REQ-TASK-020–023) | Kern des Küchenboards |
| Must | Ereignis *Aufgabe abgenommen* in `core` | Grundlage für Punkte in `GAME` |
| Should | Startseite „Heute“ (REQ-TASK-002), Abnahme-Badge (REQ-TASK-003) | Ersetzt die Button-Spalte; macht Abnahmen sichtbar |
| Should | Karte entfernen, Done aufräumen (REQ-TASK-024/025) | Board bleibt korrekt und übersichtlich |
| Should | Status-Hinweise (REQ-TASK-030) | Kurze Wartezeit in „Abnahme“ |
| Could | Karten per Drag & Drop verschieben | Buttons reichen auf dem Handy |
| Won't | Wiederkehrende Aufgaben, Fälligkeiten, Eltern-Aufgaben, Küchendisplay | Siehe „What We're NOT Building“ |

### MVP Scope

Alle *Must*-Anforderungen: Navigation, Katalog, Board mit Abnahme und das Abnahme-Ereignis. Damit lässt sich das
Küchenboard auf den Testhandys vollständig nachspielen; Punkte kommen mit `GAME` Phase 1 dazu.

### User Flow

1. Eltern legen einmalig den Katalog an (die Magnetschilder abschreiben).
2. Eltern wählen ein Kind und eine oder mehrere Aufgaben → Karten in Todo.
3. Kind erledigt eine Aufgabe, wann es will, und tippt „erledigt“ → Karte in „Abnahme“, Eltern erhalten einen Hinweis.
4. Elternteil nimmt ab → Done; `GAME` bucht die Punkte.

---

## Technical Approach

**Feasibility**: HOCH — Zustandsmaschine mit drei Zuständen, Rollen (`PARENT`/`CHILD`) und Offline-Cache existieren
bereits; neu sind das Modul, das erste Domänenereignis und der Umbau der Navigation.

**Architecture Notes**
- Neues Backend-Modul `backend/tasks` und Android-Feature-Modul `android/feature/tasks` (ADR-0001, ADR-0010).
- API zuerst in `api/openapi.yaml` (ADR-0004), **nur additiv**: die Familie nutzt weiter die aktuelle App-Version
  gegen dasselbe Backend.
- Erstes Domänenereignis über Modulgrenzen: Schnittstelle in `core` (z. B. `DomainEvents` mit synchroner Zustellung
  in derselben Transaktion), `tasks` veröffentlicht, `gamification` abonniert → **neues ADR** vor der Umsetzung.
- Zustandsübergänge als reine Domänenfunktion (Rolle × Status → erlaubt/verboten), gut unit-testbar.
- Navigation: Material-3-`NavigationBar` in `android/app`; Feature-Module liefern ihre Ziele und Graphen,
  rollenabhängig. Bestehende Deep-Links (`PlanOpenRequests`, `SharedRecipeInbox`) bleiben erhalten.

**Technical Risks**

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Doppelte Abnahme → doppelte Punkte | M | Statuswechsel mit Bedingung (`WHERE status = 'REVIEW'`), Ereignis nur bei tatsächlichem Wechsel; Idempotenz in `GAME` |
| Navigationsumbau bricht bestehende Abläufe (Deep-Links, Zurück-Taste) | M | UI-Tests für bestehende Einstiege; Umbau als eigene Phase vor neuen Features |
| Testdaten landen in der Familien-Datenbank | H | Separate Testinstanz (Open Question) |
| Backend-Änderung bricht die App der Familie | L | Nur additive API-Änderungen; Pi-Deploy nur mit grüner CI |
| Board auf kleinen Displays unübersichtlich (3 Spalten × 3 Kinder) | M | Eine Lane pro Bildschirm mit Wechsel zwischen Kindern; Sammelansicht „Zur Abnahme“ für Eltern |

---

## Implementation Phases

<!--
  STATUS: pending | in-progress | complete
  PARALLEL: phases that can run concurrently (e.g., "with 3" or "-")
  DEPENDS: phases that must complete first (e.g., "1, 2" or "-")
  PRP: link to generated plan file once created
-->

| # | Phase | Description | Status | Parallel | Depends | PRP Plan |
|---|-------|-------------|--------|----------|---------|----------|
| 1 | Navigation & Testinstanz | Untere Leiste, „Essen“-Bereich, Startseite „Heute“ (nur Essen-Inhalte), Testinstanz auf dem Pi + Test-Build (REQ-TASK-001/002) | complete | with 2 | - | `.claude/PRPs/plans/completed/navigation-testinstanz.plan.md`, Bericht: `.claude/PRPs/reports/navigation-testinstanz-report.md` (Geräte-Abnahme 2026-10-24 bestanden) |
| 2 | Board Backend/API | ADR Domänenereignisse, Modul `tasks`, Katalog, Karten, Statusübergänge, Ereignis (REQ-TASK-010, 020–025) | complete | with 1 | - | `.claude/PRPs/plans/completed/board-backend.plan.md`, Bericht: `.claude/PRPs/reports/board-backend-report.md` |
| 3 | Board App | Katalog-, Zuweisungs- und Board-Screens, Offline-Lesen, Aufgaben auf „Heute“, Abnahme-Badge, Hinweise (REQ-TASK-002/003, 020–025, 030) | in-progress | - | 1, 2 | `.claude/PRPs/plans/board-app.plan.md` |

Fortsetzung in [gamification.prd.md](gamification.prd.md): Punkte, Rangliste, Badges, Belohnungen, Avatar und die
gemeinsame Testphase auf den beiden Testhandys.

### Phase Details

**Phase 1: Navigation & Testinstanz**
- **Goal**: Die App hat Platz für neue Bereiche, und neue Features lassen sich gefahrlos testen.
- **Scope**: `NavigationBar` mit rollenabhängigen Zielen; Essen-Screens unter „Essen“; „Heute“ zeigt das heutige
  Gericht; Platzhalter für „Aufgaben“ und „Ich“/„Familie“ (Familie verwalten zieht dorthin). Zweiter Container auf dem
  Pi und App-Build gegen die Testinstanz. Keine API-Änderung.
- **Success signal**: Alle bisherigen Abläufe (Plan, Einreichen, Rezepte, Einkauf, Deep-Links) funktionieren auf dem
  Testhandy über die neue Leiste.

**Phase 2: Board Backend/API**
- **Goal**: Das Board existiert als API mit korrekten Rechten und genau einem Abnahme-Ereignis.
- **Scope**: ADR, OpenAPI, Migrationen, Domäne, Routen, Integrationstests; Ereignis-Schnittstelle in `core`.
- **Success signal**: Integrationstests decken alle Übergänge je Rolle ab; doppelte Abnahme erzeugt ein Ereignis.

**Phase 3: Board App**
- **Goal**: Das Küchenboard ist auf dem Handy nachgebildet.
- **Scope**: Screens, ViewModels, Cache, Hinweise per Hintergrund-Abgleich (ADR-0013).
- **Success signal**: Eine Woche Küchenboard lässt sich auf den Testhandys vollständig nachspielen.

### Parallelism Notes

Phase 1 ist reine App-Arbeit (plus Deploy-Konfiguration), Phase 2 reine Backend-Arbeit — sie berühren getrennte
Dateien und können parallel laufen. Phase 3 braucht beide.

---

## Decisions Log

| Decision | Choice | Alternatives | Rationale |
|----------|--------|--------------|-----------|
| Wer legt Aufgaben an (2026-10-24) | Nur Eltern, aus einem Katalog | Kinder legen selbst an | Entspricht dem Küchenboard mit vorgefertigten Schildern |
| Zuordnung (2026-10-24) | Eltern weisen zu, eine Lane je Kind | Kinder nehmen sich Aufgaben | Entspricht dem Küchenboard |
| Punktwert (2026-10-24) | Je Katalogaufgabe einstellbar, Standard 1, beim Zuweisen kopiert | Fester Punkt je Karte | Heute 1 Punkt je Karte; App soll anpassbar sein |
| Wann erledigen | Kind entscheidet, keine Fälligkeit | Fälligkeitsdatum | Aussage Eltern |
| Sichtbarkeit | Alle sehen alle Lanes | Nur eigene Lane | Küchenboard ist öffentlich; Leaderboard-Gefühl |
| Küchendisplay (2026-10-24) | Keins, nur Handys | Tablet in der Küche | Aussage Eltern |
| Navigation (2026-10-24) | Untere Leiste mit 4 Zielen, rollenabhängig; Unterbereiche innerhalb von „Essen“ | Navigation Drawer; Startseite mit Kacheln | Material-3-Empfehlung für 3–5 gleichrangige Bereiche; mit dem Daumen erreichbar, immer sichtbar; Drawer versteckt Bereiche, Kacheln wachsen wie die heutige Button-Spalte |
| Kopplung zu `GAME` | Ereignis *Aufgabe abgenommen* über `core` | `tasks` ruft `gamification` direkt | Feature-Module kennen sich nicht (ADR-0001) |
| Testbetrieb (2026-10-24) | Nur zwei Testhandys, kein Release an die Familie | Familien-Release je Phase | Aussage Eltern; nächstes Feature steht nach der Testphase bereit |

---

## Research Summary

**Market Context**
- OurHome, S'moresUp, Joon (Familien-/Kinder-Chore-Apps): Eltern legen Aufgaben an und weisen sie zu, Kinder melden
  Erledigung, Eltern bestätigen — dieselbe Abnahme wie am Küchenboard. Echte Belohnungen legen die Eltern fest.
  (Aus Produktkenntnis, nicht neu recherchiert.)
- Habitica: Aufgaben als Spiel mit Avatar; trennt Erfahrungspunkte (steigen nur) von Gold (wird ausgegeben).
- Material Design 3: `NavigationBar` für 3–5 gleichrangige Ziele auf kompakten Displays; Drawer für mehr Ziele.

**Technical Context**
- Rollen `PARENT`/`CHILD` bestehen (`api/openapi.yaml`, `Role`); `RoleGuard` im Backend-`core`.
- `core` hat noch keinen Mechanismus für modulübergreifende Ereignisse.
- Startbildschirm: `HomeScreen` in `feature/auth` mit `homeActions`-Slot, gefüllt durch `MealsHomeActions`.
- Server-URL der App ist zur Build-Zeit konfigurierbar (`fp.serverUrl` in `android/app/build.gradle.kts`) — Grundlage
  für einen Test-Build gegen eine Testinstanz.

---

*Generated: 2026-10-24*
*Status: DRAFT - needs validation*
