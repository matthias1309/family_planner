# ADR-0007: Essensplan-Fristen ohne Fernzugriff

- **Status:** Akzeptiert
- **Datum:** 2026-10-11

## Kontext

Im Essensplaner (`meals.prd.md`) reichen zwei Kinder wöchentlich ein Gericht ein; die Eltern bestellen bis
Sonntagabend bei Picnic. Zwei Kinder leben im Wechselmodell (Ankunft Di, Abreise im 2-Wochen-Wechsel Do oder So),
der Älteste ist viel unterwegs. Nach ADR-0006 ist die App nur im Heimnetz erreichbar — außer Haus kann also niemand
einreichen, und Push von außen kommt nicht an.

## Entscheidung

- Die App bleibt **LAN-only** (ADR-0006 gilt weiter).
- Die **Einreichfrist eines Kindes** ist sein letzter Anwesenheitstag vor der Bestellfrist, spätestens die Bestellfrist.
- **Erinnerungen** (2 Tage und 1 Tag vor der Frist) plant die App **lokal** auf dem Gerät ein; sie kommen auch ohne
  Heimnetz an. Statusänderungen (Freigabe/Ablehnung) werden beim nächsten Sync im Heimnetz gemeldet.
- **Fallback** außer Haus: Die Erinnerung weist auf WhatsApp hin; Eltern tragen das Gericht stellvertretend ein.

## Alternativen

- **VPN (Tailscale/WireGuard) für die Familiengeräte:** löst Erreichbarkeit vollständig, aber zusätzliche App/Konfig
  auf jedem Gerät — vorerst nicht nötig, bleibt Option.
- **Offline einreichen, Sync bei Rückkehr:** Rückkehr ist dienstags, also nach der Bestellfrist — verworfen.
- **Push über Firebase:** funktioniert überall, aber Google-Abhängigkeit und Firebase-Projekt — verworfen.
- **Push über ntfy:** zweite App nötig und ebenfalls nur im Heimnetz — verworfen.

## Konsequenzen

- Kein Fernzugriff, keine neue Angriffsfläche, keine zusätzliche App.
- In Wochen mit Abreise am Donnerstag plant das Kind früher (vor der Abreise).
- Fristberechnung hängt am Anwesenheitsmodell — muss gründlich getestet werden.
- Lokale Alarme können durch Android-Energiesparen verzögert werden; auf den Familiengeräten prüfen.
