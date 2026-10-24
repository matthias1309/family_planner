# ADR-0013: Lokale Erinnerungen und Status-Abgleich in der App

- **Status:** Akzeptiert
- **Datum:** 2026-10-24

## Kontext

Das PRD (Phase 7) verlangt Erinnerungen vor der Kind-Frist (REQ-MEAL-050), einen WhatsApp-Hinweis für unterwegs
(REQ-MEAL-051) und Meldungen bei Statusänderungen (REQ-MEAL-052/-053). Der Server ist nur im Heimnetz erreichbar
(ADR-0006, ADR-0007); Push über Firebase oder ntfy ist im PRD verworfen. Die Kinder sind oft außer Haus, die
Erinnerung muss trotzdem pünktlich kommen.

## Entscheidung

1. **Lokaler Abgleich-Stand** in einem DataStore (`meal_reminders.json`, Muster ADR-0011): zuletzt bekannte Frist, Kochzustand,
   geplante Erinnerungen und Status der Gerichte der Planwoche. Daraus kommen Alarme auch offline und nach einem Neustart.
2. **Abgleich mit WorkManager:** periodisch alle 30 Minuten (`NetworkType.CONNECTED`) sowie sofort beim App-Start und nach
   eigenen Änderungen (Gericht speichern/löschen, Absagen, Absage zurücknehmen). Ohne erreichbaren Server bleiben Stand und
   Alarme unverändert.
3. **Inexakte Alarme:** `AlarmManagerCompat.setAndAllowWhileIdle` statt exakter Alarme. Android verschiebt sie um höchstens
   etwa eine Stunde; für eine Erinnerung 1–3 Tage vor der Frist genügt das, und es braucht keine Sonderberechtigung
   (`SCHEDULE_EXACT_ALARM` ist ab Android 14 standardmäßig verweigert). Das weicht von der PRD-Risikominderung
   „Exakte Alarme anfragen“ ab.
4. **Neu-Einplanung** bei `BOOT_COMPLETED` und `MY_PACKAGE_REPLACED` aus dem lokalen Stand.
5. **Basislinie:** Der erste Abgleich einer Woche meldet nichts, damit nach Installation oder Wochenwechsel keine Flut alter
   Meldungen entsteht.
6. **Keine API-Änderung:** `GET …/status` und `GET …/plan` dürfen alle Rollen lesen.

## Alternativen

- **Exakte Alarme mit Berechtigung:** zusätzliche Sonderberechtigung und Einstellungsdialog für ein bis zwei Stunden Genauigkeit.
- **Nur WorkManager mit Startverzögerung:** im Doze-Modus stundenlang verzögert, nicht zeitgenau genug.
- **ntfy/Firebase-Push:** im PRD verworfen (Fernzugriff, ADR-0006).

## Konsequenzen

- Erinnerungen können bis etwa eine Stunde später kommen; Statusmeldungen nur, wenn das Gerät den Server erreicht
  (Heimnetz), höchstens 30 Minuten verzögert.
- Hersteller-Akkuoptimierung kann Alarme und WorkManager verzögern → Abnahme auf den Familienhandys.
- Android 13+ braucht die Laufzeit-Berechtigung `POST_NOTIFICATIONS`; ohne sie bleibt die App funktionsfähig, zeigt aber nichts.
- Es gibt genau eine `DataStore`-Instanz je Datei und Prozess (Koin-`single`).
