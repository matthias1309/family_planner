# ADR-0018: Zuletzt geladene Liste sofort zeigen

- **Status:** Akzeptiert
- **Datum:** 2026-11-14

## Kontext

Beim Öffnen von Listen wie der Rezeptliste wartet die App 1–2 Sekunden. Zwei Ursachen addieren sich:

- Navigation-Compose blendet ohne eigene Angabe bei jedem Bildschirmwechsel 700 ms über.
- Jeder Aufruf lädt die Liste neu vom Pi und zeigt bis zur Antwort eine leere Liste. Nach einer Pause kommt ein neuer
  TLS-Handshake dazu.

Der Server ist dabei nicht der Engpass: Die Rezeptabfrage ist ein einzelnes `SELECT`, die Token-Prüfung ein SHA-256.

Der Lese-Cache aus ADR-0011 hilft hier nicht. Er zeigt den gespeicherten Stand nur, wenn die Verbindung fehlschlägt.

## Entscheidung

1. **Kurze Übergänge:** Beide `NavHost` blenden 150 ms über (`NavTransitions.kt`) statt 700 ms.
2. **Zuletzt geladene Rezeptliste im Arbeitsspeicher** (`RecipeListCache`, Koin-`single`). Beim Öffnen zeigt die Liste
   sofort diesen Stand und lädt im Hintergrund neu (Pull-to-Refresh-Anzeige läuft). Die Antwort ersetzt den Stand.
   Schlägt das Laden fehl, bleibt die Liste stehen und der Fehler wird angezeigt.
3. **Besitzer:** Der Stand gehört dem angemeldeten Mitglied (`memberId`). Passt er nicht zur Session oder ist niemand
   angemeldet, gilt er als leer.
4. **Eigene Änderungen:** Speichern ersetzt den Eintrag oder hängt ihn an, Löschen (auch „schon gelöscht“) entfernt ihn.
   So taucht ein gelöschtes Rezept nicht kurz wieder auf. Die Reihenfolge stellt die nächste Server-Antwort her.
   Eine Antwort wird nur gespeichert, wenn seit ihrer Anfrage keine eigene Änderung stattfand, und gehört dem Mitglied,
   das sie angefordert hat (Mitgliederwechsel während des Ladens).
5. **Nicht persistent:** Nach einem Neustart der App wartet das erste Öffnen wie bisher. Offline-Lesen bleibt Aufgabe
   von ADR-0011.

### Nachtrag 2026-11-14: Wochenplan, Einkaufsliste und Aufgaben

6. **Gemeinsamer Speicher:** Mitglied, Ticket und Änderungszähler liegen in `LastLoadedCache<Schlüssel, Wert>` (Modul
   `meals`); `RecipeListCache` (ein Wert) und `ShoppingListCache` (je Woche, höchstens 8) nutzen ihn.
7. **Wochenplan:** Er nutzt den vorhandenen dauerhaften Cache aus ADR-0011. Beim Öffnen und beim Wochenwechsel zeigt er
   die gespeicherte Woche sofort (ohne Offline-Hinweis) und lädt neu; das wirkt auch nach einem App-Neustart.
   Bearbeiten, Absagen und das Öffnen von Tagen sind erst mit dem frischen Stand möglich, damit niemand an einem
   veralteten Gericht arbeitet. Schlägt das Laden fehl, gilt ADR-0011 unverändert (Offline-Hinweis oder Fehler).
8. **Einkaufsliste:** Arbeitsspeicher je Woche. Abhaken und Hinzufügen übernehmen die vom Server zurückgegebene Liste,
   Löschen entfernt den Eintrag; Abhaken bleibt während des Nachladens möglich, der Server prüft jeden Schlüssel.
   Scheitert das Nachladen an der Verbindung, bleibt die Liste mit Fehlerhinweis stehen, aber nur lesbar; andere
   Fehler verdrängen sie (wie ADR-0011).

9. **Aufgaben-Board:** wie der Wochenplan. Es nutzt den vorhandenen `BoardCache` (DataStore), zeigt den Stand beim
   Öffnen sofort und lädt neu; Kartenaktionen gibt es erst mit dem frischen Stand. Lehnt der Server das Neuladen ab,
   verschwindet der gespeicherte Stand hinter dem Fehler; ein Verbindungsfehler zeigt ihn wie bisher als offline.

## Alternativen

- **DataStore wie ADR-0011:** Würde auch den ersten Aufruf nach dem Start beschleunigen, braucht aber Serializer,
  Versionierung und Aufräumen. Für eine Liste, die in Millisekunden nachlädt, ist das zu viel (YAGNI).
- **OkHttp-HTTP-Cache:** Der Server sendet keine Cache-Header; „sofort zeigen und dahinter neu laden“ lässt sich damit
  nicht abbilden.
- **Übergänge ganz abschalten:** Wirkt abrupt; 150 ms sind kaum spürbar und zeigen trotzdem den Wechsel.

## Konsequenzen

- Die Liste kann für einen Augenblick einen Stand zeigen, den ein anderes Gerät inzwischen geändert hat; die Antwort
  korrigiert ihn sofort.
- Use Cases, die die Rezeptliste ändern, müssen den `RecipeListCache` mitführen.
