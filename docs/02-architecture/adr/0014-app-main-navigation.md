# ADR-0014: Hauptnavigation der App

- **Status:** Akzeptiert
- **Datum:** 2026-10-24

## Kontext

Der Startbildschirm war eine Spalte aus Buttons, die jedes Feature-Modul ergänzt (ADR-0001: Module kennen sich nicht,
Einstiege kommen über einen Slot). Mit Aufgaben-Board (TASK) und Punkten (GAME) wächst die Spalte unübersichtlich.
Die Kinder (10, 16, 18) nutzen die App am Handy mit dem Daumen.

## Entscheidung

1. **Material-3-`NavigationBar`** mit vier Hauptbereichen: Kinder *Heute · Essen · Aufgaben · Ich*, Eltern
   *Heute · Essen · Aufgaben · Familie* (REQ-TASK-001). „Familie verwalten“ zieht vom Startbildschirm in „Familie“.
2. **Leiste nur auf den vier Hauptseiten.** Unterseiten (Essensplan, Rezept, Editoren …) behalten ihren „Zurück“-Button und
   zeigen keine Leiste; bestehende Screens bleiben unverändert, es braucht keinen verschachtelten Back-Stack je Bereich.
3. **Zustand je Bereich:** Wechsel mit `saveState`/`restoreState` und `launchSingleTop`, Start ist „Heute“. System-Zurück
   auf einer Hauptseite führt zu „Heute“, auf „Heute“ schließt die App.
4. **Slots statt Modulabhängigkeiten:** „Heute“ (`feature:auth`) nimmt Inhalte anderer Module über `todayContent` auf;
   `feature:meals` liefert die Karte „Heute gibt es“ und die Übersicht „Essen“ (`MealsHomeRoute`). `app` verdrahtet sie.
5. **Deep-Links über „Essen“:** Benachrichtigung (Essensplan) und geteilte Rezepte wechseln zuerst zu „Essen“, damit
   „Zurück“ dort landet (ADR-0012, ADR-0013).
6. **Platzhalter** für „Aufgaben“ und „Ich“ („Kommt bald“), bis TASK Phase 3 bzw. GAME Phase 1 sie ersetzen.

## Alternativen

- **Navigation Drawer:** Bereiche versteckt, schlechter mit dem Daumen erreichbar.
- **Kachel-Startseite:** wächst mit jedem Modul wieder zur Button-Spalte.
- **Leiste überall mit verschachtelten Graphen je Tab:** größerer Umbau aller Essen-Screens; später möglich, falls die Tests auf
  den Testhandys die fehlende Leiste auf Unterseiten bemängeln.

## Konsequenzen

- Neue Hauptbereiche entstehen nur per ADR (maximal fünf Ziele in der Leiste).
- Die Platzhalter werden mit TASK Phase 3 und GAME Phase 1 ersetzt.
- Jeder Hauptbereich ist ein eigener Einstieg; Unterseiten kehren per „Zurück“ zur Übersicht ihres Bereichs zurück.
