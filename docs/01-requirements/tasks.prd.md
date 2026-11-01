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

**Erweiterung (2026-11-01):** Eltern können einer zugewiesenen Karte eine **Frist** (Datum + Uhrzeit) geben; das Kind
wird vorher erinnert, überfällige Karten sind markiert. Außerdem gibt es einen **Aufgaben-Pool**: Katalogaufgaben, die
die Eltern dafür freigeben, kann sich ein Kind selbst nehmen („der Mülleimer ist voll“) — in sein Todo oder, wenn
schon erledigt, direkt zur Abnahme.

## What We're NOT Building

- Aufgaben, die Kinder sich selbst *anlegen* — Kinder nehmen nur freigegebene Katalogaufgaben aus dem Pool.
- Automatisch wiederkehrende Aufgaben (Zeitpläne) — die Eltern verteilen von Hand aus dem Katalog.
- Pflicht-Fristen und Punktabzug bei Verspätung — die Frist ist optional und nur ein Hinweis.
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
- **Frist (optional):** Eine Karte kann eine Frist (Zeitpunkt, Europe/Berlin) haben; nur Eltern setzen, ändern oder
  entfernen sie, solange die Karte in `TODO` oder `REVIEW` steht. Eine neue Frist muss in der Zukunft liegen.
  - Eingehalten ist die Frist, wenn das Kind **vor Fristende „erledigt“ meldet**; die Abnahme darf später kommen.
  - **Überfällig** ist eine Karte in `TODO` nach Fristende — auch nach einer Zurückweisung.
  - Wird nach Fristende gemeldet, trägt die Karte den Hinweis **„zu spät gemeldet“**. Keine Auswirkung auf Punkte.
- **Pool:** Eltern geben Katalogaufgaben einzeln für Kinder frei (Standard: nicht freigegeben). Ein Kind kann eine
  freigegebene, nicht archivierte Aufgabe für sich **nehmen** (Karte in eigenem `TODO`) oder **sofort als erledigt
  melden** (Karte direkt in `REVIEW`). Titel, Symbol und Punktwert werden wie beim Zuweisen kopiert. Solche Karten
  sind als **„selbst genommen“** gekennzeichnet und haben keine Frist; eine selbst genommene Karte in `TODO` darf das
  Kind wieder zurücklegen (entfernen). Missbrauch lösen die Eltern durch Zurückweisen oder Entfernen.

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

##### REQ-TASK-011 Katalogaufgabe für den Pool freigeben
Als Elternteil möchte ich festlegen, welche Katalogaufgaben sich Kinder selbst nehmen dürfen, damit der Pool nur
sinnvolle Aufgaben enthält.

**Abnahmekriterien**
- Given eine neue Katalogaufgabe, Then ist sie nicht für den Pool freigegeben.
- Given „Mülleimer leeren“, When ich „für Kinder frei wählbar“ einschalte, Then steht sie im Pool der Kinder.
- Given eine freigegebene Aufgabe, When ich die Freigabe ausschalte oder die Aufgabe archiviere, Then steht sie nicht
  mehr im Pool; bestehende Karten bleiben.

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

##### REQ-TASK-026 Frist setzen
Als Elternteil möchte ich einer zugewiesenen Aufgabe ein Enddatum mit Uhrzeit geben, damit das Kind weiß, bis wann
sie erledigt sein muss.

**Abnahmekriterien**
- Given ich weise eine Aufgabe zu, When ich „Frist: Sa 18:00“ wähle, Then hat die Karte diese Frist und das Kind sieht
  sie auf der Karte und auf „Heute“.
- Given ich weise zu, When ich keine Frist wähle, Then hat die Karte keine Frist (wie bisher).
- Given eine Karte in Todo oder Abnahme, When ich die Frist ändere oder entferne, Then gilt die neue Frist bzw. keine.
- Given ich wähle einen Zeitpunkt in der Vergangenheit, Then wird die Frist abgelehnt (Server antwortet 400).
- Given ich bin Kind, When ich eine Frist setzen oder ändern will, Then ist das nicht möglich (403).
- Given eine Karte in Done, When ich die Frist ändern will, Then ist das nicht möglich.

##### REQ-TASK-027 Überfällige Aufgaben erkennen
Als Familienmitglied möchte ich sehen, welche Aufgaben ihre Frist verpasst haben, damit nichts untergeht.

**Abnahmekriterien**
- Given eine Karte in Todo mit Frist in der Vergangenheit, When ich das Board oder „Heute“ öffne, Then ist sie als
  „überfällig“ markiert und steht in Todo oben.
- Given das Kind meldet vor Fristende, When das Fristende vorbei ist, Then ist die Karte nicht überfällig.
- Given das Kind meldet nach Fristende, Then trägt die Karte in Abnahme und Done den Hinweis „zu spät gemeldet“ und
  bringt bei Abnahme die vollen Punkte.
- Given eine Karte wird nach Fristende zurückgewiesen, Then ist sie in Todo wieder überfällig.
- Given ich bin ohne Heimnetz, Then wird „überfällig“ aus dem Cache und der Geräteuhr berechnet.

##### REQ-TASK-028 Aufgabe aus dem Pool nehmen
Als Kind möchte ich mir eine freigegebene Aufgabe selbst nehmen, damit ich etwas erledigen kann, das ich gerade sehe,
ohne auf die Eltern zu warten.

**Abnahmekriterien**
- Given ich bin Kind, When ich den Pool öffne, Then sehe ich alle freigegebenen, nicht archivierten Katalogaufgaben
  mit Punktwert.
- Given „Mülleimer leeren“ im Pool, When ich „nehmen“ tippe, Then steht eine Karte „selbst genommen“ in meinem Todo.
- Given „Mülleimer leeren“ im Pool, When ich „schon erledigt“ tippe, Then steht die Karte direkt in meiner „Abnahme“
  und die Eltern erhalten einen Hinweis (REQ-TASK-030).
- Given eine selbst genommene Karte in meinem Todo, When ich sie zurücklege, Then ist sie vom Board verschwunden.
- Given eine von den Eltern zugewiesene Karte, When ich sie zurücklegen will, Then ist das nicht möglich.
- Given eine nicht freigegebene oder archivierte Aufgabe, When ich sie nehmen will, Then lehnt der Server ab (403/409).
- Given ich bin Elternteil, Then sehe ich am Kennzeichen „selbst genommen“, welche Karten sich ein Kind genommen hat,
  und nehme sie wie jede Karte ab oder weise zurück.

#### Benachrichtigungen

##### REQ-TASK-030 Status-Hinweise
Als Familienmitglied möchte ich über Änderungen an meinen Karten informiert werden, damit ich nicht nachschauen muss.

**Abnahmekriterien**
- Given ein Kind meldet eine Aufgabe, When das Elterngerät im Heimnetz abgleicht, Then erhalten die Eltern einen
  Hinweis (wie REQ-MEAL-053, ADR-0013).
- Given ein Elternteil weist zu, nimmt ab oder weist zurück, When das Kindgerät abgleicht, Then erhält das Kind einen
  Hinweis.

##### REQ-TASK-031 Erinnerung vor Fristende
Als Kind möchte ich vor Ablauf einer Frist erinnert werden, damit ich sie nicht verpasse.

**Abnahmekriterien**
- Given eine Karte in meinem Todo mit Frist, When das Fristende in 2 Stunden erreicht ist, Then erhalte ich eine
  Erinnerung — auch ohne Heimnetz (lokaler Alarm, ADR-0013; Android darf bis etwa 1 h verschieben).
- Given ich habe die Karte gemeldet oder die Frist wurde entfernt, When die Erinnerungszeit kommt, Then erhalte ich
  keine Erinnerung (sofern das Gerät seither abgeglichen hat).
- Given die Frist wurde geändert, When mein Gerät abgleicht, Then gilt die Erinnerung für die neue Frist.

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
| Should | Frist setzen, überfällig erkennen (REQ-TASK-026/027) | Eltern brauchen „bis Samstag“ für manche Aufgaben |
| Should | Pool freigeben und nehmen (REQ-TASK-011/028) | Kinder sollen spontan Gesehenes erledigen und gutschreiben lassen |
| Should | Erinnerung vor Fristende (REQ-TASK-031) | Frist nützt nur, wenn das Kind daran denkt |
| Could | Karten per Drag & Drop verschieben | Buttons reichen auf dem Handy |
| Won't | Wiederkehrende Aufgaben, Punktabzug bei Verspätung, Eltern-Aufgaben, Küchendisplay | Siehe „What We're NOT Building“ |

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
| 3 | Board App | Katalog-, Zuweisungs- und Board-Screens, Offline-Lesen, Aufgaben auf „Heute“, Abnahme-Badge, Hinweise (REQ-TASK-002/003, 020–025, 030) | complete | - | 1, 2 | `.claude/PRPs/plans/completed/board-app.plan.md`, Bericht: `.claude/PRPs/reports/board-app-report.md` (Geräte-Abnahme 2026-10-25 bestanden) |
| 4 | Fristen & Pool Backend/API | Frist auf Karten (setzen/ändern/entfernen), „zu spät gemeldet“, Pool-Freigabe am Katalog, Nehmen/Sofort-Melden/Zurücklegen für Kinder (REQ-TASK-011, 026–028) | in-progress | - | 2 | `.claude/PRPs/plans/fristen-pool-backend.plan.md` |
| 5 | Fristen & Pool App | Frist beim Zuweisen und an der Karte, Überfällig-Markierung auf Board und „Heute“, Freigabe-Schalter im Katalog, Pool-Screen für Kinder, Erinnerung (REQ-TASK-011, 026–028, 031) | pending | - | 3, 4 | - |

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

**Phase 4: Fristen & Pool Backend/API**
- **Goal**: Karten können eine Frist haben, Kinder können sich freigegebene Aufgaben nehmen — mit korrekten Rechten.
- **Scope**: OpenAPI (nur additiv: optionale Felder `dueAt`, `reportedLate`, `selfAssigned` an der Karte,
  `childSelectable` am Katalog, neue Endpunkte für Pool und Frist), Migration, Domäne, Routen, Integrationstests.
- **Success signal**: Integrationstests decken Frist-Regeln und Pool-Rechte je Rolle ab; die aktuelle Familien-App
  läuft unverändert gegen das neue Backend.

**Phase 5: Fristen & Pool App**
- **Goal**: Eltern setzen Fristen, Kinder sehen sie, werden erinnert und können sich Aufgaben aus dem Pool nehmen.
- **Scope**: Zuweisungs- und Katalog-Screens erweitern, Karte/„Heute“ mit Frist und Überfällig, Pool-Screen,
  Erinnerung über den Abgleich nach ADR-0013, Cache.
- **Success signal**: Auf den Testhandys: Frist setzen → Erinnerung kommt → überfällig sichtbar; Kind nimmt Aufgabe
  aus dem Pool und meldet sie sofort → Eltern nehmen ab.

### Parallelism Notes

Phase 1 ist reine App-Arbeit (plus Deploy-Konfiguration), Phase 2 reine Backend-Arbeit — sie berühren getrennte
Dateien und können parallel laufen. Phase 3 braucht beide. Phase 5 braucht die API aus Phase 4.

---

## Decisions Log

| Decision | Choice | Alternatives | Rationale |
|----------|--------|--------------|-----------|
| Wer legt Aufgaben an (2026-10-24) | Nur Eltern, aus einem Katalog | Kinder legen selbst an | Entspricht dem Küchenboard mit vorgefertigten Schildern |
| Zuordnung (2026-10-24) | Eltern weisen zu, eine Lane je Kind | Kinder nehmen sich Aufgaben | Entspricht dem Küchenboard — ergänzt am 2026-11-01 um den Pool (s. u.) |
| Punktwert (2026-10-24) | Je Katalogaufgabe einstellbar, Standard 1, beim Zuweisen kopiert | Fester Punkt je Karte | Heute 1 Punkt je Karte; App soll anpassbar sein |
| Wann erledigen (2026-10-24) | Kind entscheidet, keine Fälligkeit | Fälligkeitsdatum | Aussage Eltern — abgelöst am 2026-11-01 (s. u.) |
| Fristen (2026-11-01) | Optionale Frist je Karte, nur Eltern; Markierung + Erinnerung, keine Punktfolgen | Pflichtfrist; Punktabzug bei Verspätung | Aussage Eltern: bei bestimmten Aufgaben soll das Kind wissen, bis wann |
| Frist eingehalten (2026-11-01) | Wenn vor Fristende gemeldet | Wenn vor Fristende abgenommen | Kind soll nicht für langsame Abnahme der Eltern haften |
| Erinnerung (2026-11-01) | 2 h vor Fristende, lokaler Alarm (ADR-0013) | 1 h vorher; Push | Inexakte Alarme können bis ~1 h später kommen; kein Fernzugriff |
| Pool (2026-11-01) | Eltern geben Katalogaufgaben einzeln frei; Kind nimmt in Todo oder meldet sofort | Alle Katalogaufgaben; nur Todo; nur Sofort-Melden | Aussage Eltern: „Mülleimer ist voll“ spontan erledigen, aber Eltern steuern, was frei wählbar ist |
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
