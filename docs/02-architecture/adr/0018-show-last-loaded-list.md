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

## Alternativen

- **DataStore wie ADR-0011:** Würde auch den ersten Aufruf nach dem Start beschleunigen, braucht aber Serializer,
  Versionierung und Aufräumen. Für eine Liste, die in Millisekunden nachlädt, ist das zu viel (YAGNI).
- **OkHttp-HTTP-Cache:** Der Server sendet keine Cache-Header; „sofort zeigen und dahinter neu laden“ lässt sich damit
  nicht abbilden.
- **Übergänge ganz abschalten:** Wirkt abrupt; 150 ms sind kaum spürbar und zeigen trotzdem den Wechsel.

## Konsequenzen

- Die Liste kann für einen Augenblick einen Stand zeigen, den ein anderes Gerät inzwischen geändert hat; die Antwort
  korrigiert ihn sofort.
- Wochenplan und Einkaufsliste können das Muster übernehmen, falls sie sich ebenfalls träge anfühlen.
- Use Cases, die die Rezeptliste ändern, müssen den `RecipeListCache` mitführen.
