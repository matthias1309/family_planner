# ADR-0006: Zugriff nur im Heimnetz

- **Status:** Akzeptiert
- **Datum:** 2026-10-11

## Kontext

Die App wird zunächst nur zu Hause genutzt; ein Fernzugriff ist für den Start nicht nötig. Android erlaubt
Klartext-HTTP standardmäßig nicht, und Login-Daten sollen auch im WLAN nicht mitlesbar sein.
Auf dem Pi laufen Pi-hole (lokales DNS) und bereits eine App mit eigenem Zertifikat (FinanzFlow).

## Entscheidung

- Das Backend ist **nur im Heimnetz** erreichbar (Port 8095), keine Portfreigabe im Router, kein Tailscale.
- Lokaler Name über Pi-hole, z. B. `planner.home.arpa` → IP des Pi.
- **HTTPS mit selbstsigniertem Zertifikat** (SAN: lokaler Name + IP des Pi), Ablage in `certs/` neben der Compose-Datei.
- Die App vertraut **nur diesem Zertifikat** für diesen Host (Android `network_security_config` mit Trust Anchor in `res/raw`).
- Server-Adresse ist in der App konfigurierbar (Build-Config), nicht fest im Code.

## Alternativen

- **Klartext-HTTP im LAN:** am einfachsten, aber Passwörter/Tokens wären im WLAN mitlesbar — verworfen.
- **Tailscale + `tailscale serve`:** gültiges Zertifikat und Fernzugriff, aber jedes Gerät braucht Tailscale — für später vorgemerkt.
- **Öffentlich mit Reverse Proxy + Let's Encrypt:** große Angriffsfläche, nicht nötig.

## Konsequenzen

- Unterwegs ist die App offline bzw. zeigt nur zwischengespeicherte Daten.
- Zertifikatswechsel erfordert ein App-Update → Zertifikat mit langer Laufzeit (z. B. 5 Jahre) erzeugen.
- Fernzugriff lässt sich später per neuem ADR (Tailscale) ergänzen, ohne Backend-Änderung.
