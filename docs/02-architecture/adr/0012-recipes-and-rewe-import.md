# ADR-0012: Rezeptsammlung mit strukturierten Zutaten und Rezept-Import in der App

- **Status:** Akzeptiert
- **Datum:** 2026-10-18

## Kontext

Das Meals-PRD sah nach der Einreichung (Phase 4) direkt die Einkaufsliste aus **Freitext-Zutaten** vor; Rezepte und
strukturierte Zutaten standen unter „Later“. Beim Planen von Phase 5 zeigte sich (Anwender, 2026-10-18):

- Die Familie kocht immer wieder dieselben Lieblingsgerichte. Kinder und Eltern sollen ein Rezept **aus einer Liste
  wählen** können, statt die Zutaten jede Woche neu abzutippen.
- Eine Einkaufsliste aus Freitext lässt sich weder **zusammenführen** (2 × „Zwiebel“) noch auf die **Anzahl der Esser**
  umrechnen. Die Esser wechseln je Tag (Wechselmodell, ADR-0007), die Anwesenheit kennt das System bereits.
- Die Lieblingsrezepte stammen aus der REWE-Rezeptwelt (z. B. `rewe.de/rezepte/spaghetti-lauch-carbonara-mit-ohne-speck/`,
  `rewe.de/rezepte/bunte-gemuese-quesadilla/`) und sollen importierbar sein.

Prüfung der beiden REWE-Seiten (2026-10-18):

- Jede Seite enthält ein schema.org-`Recipe` als JSON-LD (`<script id="recipe-schema" type="application/ld+json">`,
  verschachtelt als `mainEntity` eines `Webpage`-Objekts) mit `name`, `recipeYield` („4“), `recipeIngredient`,
  `recipeInstructions` (`HowToStep`) und `url`.
- Zutaten sind Zeichenketten der Form `"500.0 g Vollkornspaghetti"`, `"2.0 Stange(n) Lauch"`, `"Salz"` (ohne Menge) oder
  `"Saft von 1 Limette"` (Menge im Text). Sie lassen sich überwiegend, aber nicht immer sicher in Menge/Einheit/Name zerlegen.
- Ein Abruf **ohne Browser** (curl, auch mit Browser-Headern) wird mit **HTTP 403 und Captcha-Seite** abgewiesen. Im echten
  Browser ist die Seite frei lesbar.

## Entscheidung

1. **Rezepte gehören zum Modul `meals`** (kein eigenes Feature-Modul): Sie werden nur vom Essensplaner genutzt (ADR-0001, ADR-0010).
2. **Strukturierte Zutaten:** Eine Zutat hat `name` (Pflicht), `quantity` (Dezimalzahl, optional) und `unit` (Freitext, optional).
   Eine Freitext-Zeile ist eine Zutat nur mit `name`. Damit bleiben Gerichte ohne Rezept wie bisher möglich, und die
   vorhandenen Zutatenzeilen (`meal_dish_ingredients.text`) werden per Migration zu `name` ohne Menge.
3. **Rezept:** Name, Basis-Portionen, Zutatenliste, optional Zubereitungsschritte und Quell-URL. Bilder werden nicht übernommen.
4. **Gericht aus Rezept = Kopie:** Wählt jemand ein Rezept für ein Gericht, werden Name und Zutaten **auf die gewählten
   Portionen umgerechnet und kopiert**; das Gericht merkt sich `recipeId` und `servings`. Danach ist das Gericht frei
   änderbar. Spätere Änderungen am Rezept ändern keine bestehenden (womöglich schon freigegebenen) Gerichte.
   Vorschlag für `servings`: Anzahl der an diesem Tag anwesenden Familienmitglieder (änderbar).
5. **Import läuft in der App, nicht auf dem Server:**
   - Anlegen, Importieren, Ändern und Löschen von Rezepten nur durch **Eltern**; Kinder lesen und wählen aus.
   - Einstieg über das Android-**Teilen-Menü** (Browser/REWE-App → Family Planner) oder durch Einfügen einer URL.
   - Die App lädt die Seite in einer **unsichtbaren WebView**, liest alle `application/ld+json`-Blöcke und sucht ein
     `Recipe` (direkt, in `@graph` oder als `mainEntity`). Das ist allgemeines schema.org, also nicht an REWE gebunden;
     abgenommen wird aber nur mit REWE.
   - Die Zutatenzeilen werden mit einer reinen, unit-getesteten Funktion zerlegt (`"<Zahl> <Einheit> <Name>"`,
     `"<Zahl> <Name>"`, sonst alles als `name`); `"500.0"` wird zu `500`.
   - **Eigenmarken-Präfixe** am Anfang des Namens werden entfernt, längster Treffer zuerst, nur als ganzes Wort:
     „REWE Bio“, „REWE Beste Wahl“, „REWE Feine Welt“, „ja!“, „REWE“ (Liste als Konstante, erweiterbar). Aus
     „REWE Bio Rapsöl“ wird „Rapsöl“, aus „REWE Beste Wahl Kidney-Bohnen“ „Kidney-Bohnen“. Bleibt danach nichts übrig,
     gilt der ursprüngliche Name. Wörter wie „Bio“ allein (z. B. „Bio-Bacon“) bleiben stehen.
   - Das Ergebnis erscheint **immer zuerst im Rezept-Editor zur Prüfung**; gespeichert wird über die normale Rezept-API.
   - Wird kein `Recipe` gefunden (anderes Seitenformat, Captcha), meldet die App das und bietet das manuelle Anlegen an.
6. **API-Änderung additiv:** Strukturierte Zutaten kommen als neues Feld hinzu; das bisherige `ingredients: string[]` bleibt
   lesbar, bis alle Geräte die neue App haben (Pi aktualisiert sich selbst, Apps nicht gleichzeitig; ADR-0005).
7. **Einkaufsliste danach (eigene Phase):** führt Zutaten mit gleichem Namen und gleicher Einheit zusammen (Mengen addiert,
   Vergleich ohne Groß-/Kleinschreibung und Leerzeichen am Rand); keine Einheiten-Umrechnung im ersten Schritt.

## Alternativen

- **Einkaufsliste zuerst aus Freitext, Rezepte später (bisheriger Plan):** schneller sichtbar, aber die Liste müsste mit den
  strukturierten Zutaten noch einmal gebaut werden, und das tägliche Abtippen bleibt.
- **Import auf dem Server (Pi lädt die URL):** scheitert an der Bot-Abwehr (403/Captcha). Diese zu umgehen kommt nicht in
  Frage; außerdem bräuchte das Backend dafür ausgehenden Internetzugriff.
- **Inoffizielle REWE-APIs / Scraping des HTML-Layouts:** bricht bei jeder Layout-Änderung und ist rechtlich heikler als
  das Lesen der für Suchmaschinen veröffentlichten schema.org-Daten.
- **Mealie/Tandoor als Rezeptdienst daneben betreiben:** guter Import, aber zweites System, eigene Konten, keine Verbindung
  zu Kochtagen und Freigabe (siehe PRD „Bauen oder übernehmen“).
- **Rezept live verknüpfen statt kopieren:** Rezeptänderungen würden freigegebene Gerichte und die Einkaufsliste
  nachträglich verändern.
- **Zutaten nur als Freitext lassen und auf der Einkaufsliste heuristisch zusammenführen:** unzuverlässig und ohne
  Portionen-Umrechnung.

## Konsequenzen

- Phase 5 des Meals-PRD wird die Rezeptsammlung, die Einkaufsliste rückt auf Phase 6, Erinnerungen auf 7, Pilot auf 8.
- Das Datenmodell der Gerichte wird erweitert (Migration der Zutatenzeilen, `recipeId`, `servings`). Die Freitext-Eingabe
  bleibt; Kinder müssen kein Rezept anlegen, um einzureichen.
- Der Importweg hängt an den schema.org-Daten der Quelle. Fehlen sie oder ändert REWE das Format, bleibt das manuelle
  Anlegen; der Zerleger muss Grenzfälle wie „Saft von 1 Limette“ oder „Optional: Bio-Bacon“ ohne Fehler als Name übernehmen.
- Importierte Rezepte sind nur für den privaten Familiengebrauch; die Quell-URL wird gespeichert, Inhalte werden nicht
  weiterverbreitet.
- Die App braucht eine WebView und einen Teilen-Ziel-Eintrag (`ACTION_SEND` mit `text/plain`) im Manifest.
- Die Grundlage für die spätere Picnic-Übergabe (strukturierte Zutaten) ist damit gelegt.
