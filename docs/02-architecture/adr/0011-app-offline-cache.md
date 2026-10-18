# ADR-0011: Lokaler Lese-Cache der App

- **Status:** Akzeptiert
- **Datum:** 2026-10-18

## Kontext

REQ-MEAL-002 verlangt, dass der Essensplan ohne Heimnetz lesbar bleibt und der Zeitpunkt der letzten Synchronisierung
sichtbar ist. Der Server ist nur im Heimnetz erreichbar (ADR-0006, ADR-0007); die Kinder sind oft außer Haus und wollen
nachsehen, was es gibt. Nötig ist nur **Lesen**; es geht um wenige Wochen kleiner Daten (7 Tage × Name, Koch, Zutaten).

## Entscheidung

1. **Lese-Cache im Feature-Modul, ein DataStore** (`meal_plan_cache.json`, Serializer mit kotlinx.serialization) statt Room.
2. **Read-through:** Jede erfolgreich geladene Woche wird mit `syncedAt` gespeichert. Bei Verbindungsfehlern
   (`Network`, `Certificate`, `Server`) zeigt die App den gespeicherten Stand samt Zeitpunkt. Andere Fehler
   (`Forbidden`, `Validation`, `NotFound`, `Unauthorized`) werden angezeigt und nie vom Cache verdeckt.
3. **Beim Öffnen** des Essensplans werden diese und die nächste Woche geladen (die nächste im Hintergrund).
4. **Aufräumen:** Behalten werden Wochen ab „vorige Woche“, höchstens 8.
5. **Besitzer:** Der Cache gehört dem angemeldeten Mitglied (`memberId`). Passt er nicht zur Session, gilt er als leer und
   wird beim nächsten Schreiben ersetzt.
6. **Unverschlüsselt:** Der Plan enthält keine Geheimnisse; er liegt in der App-Sandbox, Cloud-Backup und Gerätetransfer
   sind ausgeschlossen (`data_extraction_rules.xml`).
7. **Offline kein Schreiben:** Bearbeiten ist offline ausgeblendet.

## Alternativen

- **Room:** Codegenerierung (KSP) und Schema-Migrationen — für höchstens 8 Wochen überdimensioniert.
- **OkHttp-HTTP-Cache:** Der Server sendet keine Cache-Header, und der Synchronisierungszeitpunkt ließe sich nicht abbilden.
- **Verschlüsselter DataStore (wie die Session):** Kein Schutzbedarf, nur Aufwand.
- **Offline-Schreiben mit Warteschlange:** Konflikte (ein Gericht pro Tag), nicht gefordert (YAGNI).

## Konsequenzen

- Das Dateiformat trägt eine Version (`version = 1`); eine unbekannte Version oder eine beschädigte Datei gilt als leer.
- Cache-Fehler sind nie ein Nutzerfehler: Lesen liefert „nichts gespeichert“, Schreiben wird nur protokolliert.
- Spätere Module können das Muster übernehmen; ein Hintergrund-Sync (Phase 7, Erinnerungen) schreibt in denselben Cache.
- Es gibt genau eine `DataStore`-Instanz je Datei und Prozess (Koin-`single`).
