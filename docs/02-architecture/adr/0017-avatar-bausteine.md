# ADR-0017: Avatar-Bausteine (Avataaars-Definition, Rendering in der App)

- **Status:** Akzeptiert
- **Datum:** 2026-11-01

## Kontext

Rangliste und Board zeigen nur Anfangsbuchstaben. Kinder sollen sich einen Comic-Avatar aus Bausteinen (Hautton, Frisur, Mütze,
Oberteil, Brille, Hintergrund …) zusammenstellen und weitere Teile mit Punkten kaufen (REQ-GAME-030/031). Offene Fragen aus dem
PRD: Technik und Lizenz der Bausteine. Randbedingungen: offline nutzbar, keine Rendering-Last auf dem Pi, privates
Familienprojekt, keine Weiterverbreitung, kein selbst gezeichnetes Artwork.

## Entscheidung

1. **Stil „Avataaars“** (Pablo Stanley, „Free for personal and commercial use“) als DiceBear-Stildefinition
   (`@dicebear/styles` 10.6.0), unverändert unter `api/avatar/avataaars.json` — gemeinsamer Vertrag für App und Backend,
   Herkunft und Lizenz in `api/avatar/README.md`. Reines JSON, keine JS-Laufzeit.
2. **Rendering in der App:** `core:ui` baut aus Definition + Auswahl einen SVG-String (`AvatarSvg`, reiner Baumdurchlauf) und
   zeichnet ihn mit **AndroidSVG** (Apache-2.0) auf den Compose-Canvas (`AvatarImage`). Die Definition kommt als Asset.
3. **Katalog und Preise im Backend** als Kotlin-Liste (`AvatarCatalog`, wie `Badge`). Der Server prüft Besitz und Slot-Zuordnung;
   ein Test vergleicht den Katalog mit der Definition.
4. **Item-ID-Schema** `<slot>.<variante>`, Farben `<slot>.<hex ohne #>` (z. B. `top.winterHat1`, `hairColor.2c1b18`).
   IDs werden gespeichert und **nie geändert**; ein Test hält die ID-Liste fest.
5. **Hintergrund** ist ein eigener Slot mit eigener Palette (Kreis hinter dem Avatar), nicht Teil der Definition.
6. **Namensnennung** „Avataaars von Pablo Stanley“ in der App ab Phase 4b (Familie → Über).

## Spike-Ergebnis (2026-11-01)

`AvatarImageSpikeTest` (Robolectric, Native Graphics) rendert vier Beispiel-Avatare (kostenlos, Bommelmütze, große Mähne mit
Brille und Bart, Motiv-Shirt mit Pizza) nach `android/core/ui/build/avatar-spike/*.png`. Geprüft per Bildansicht:
Gesicht, Haare/Mütze und Kleidung sitzen richtig (Transforms korrekt), keine schwarzen Flächen (Farbreferenzen aufgelöst),
Motiv-Shirt zeigt die Grafik, optionale Slots fehlen sauber. Dauer je Avatar (SVG bauen + parsen + zeichnen) ca. 15 ms,
der erste Avatar mit JSON-Laden und Warm-up ca. 750 ms. **Go.**

## Alternativen

- **SVG serverseitig rendern:** Rechenlast und Netzwerk pro Avatar auf dem Pi; kein Offline.
- **VectorDrawables je Teil:** Farben nicht frei tönbar, hoher Handaufwand.
- **Open Peeps (CC0):** unkritische Lizenz, aber keine Kleidung/Mützen → zu wenig Shop-Inhalt. **Plan B**, falls die Lizenz
  zum Problem wird: gleicher Renderer, anderer Katalog.
- **Personas (CC BY):** nur vier Oberteile.
- **Selbst zeichnen:** widerspricht dem PRD (kein eigenes Artwork).
- **Eigener Compose-Zeichner über `PathParser`:** Plan B bei Rendering-Fehlern von AndroidSVG; nicht nötig geworden.

## Konsequenzen

- Item-IDs gelten dauerhaft; ein Definitions-Update ist nur mit grünem Konsistenztest zulässig (IDs dürfen nicht verschwinden).
- Asset ca. 125 KB, zusätzliche Abhängigkeit AndroidSVG (seit 2019 stabil, Apache-2.0).
- Lizenztext von avataaars.com war bei der Recherche nicht abrufbar (Zertifikat abgelaufen) — Wortlaut nur über DiceBear
  (`meta.license`) belegt. Restrisiko gering (privat, keine Weiterverbreitung).
- Entfernte Katalog-Items führen beim Lesen zu einem Fallback (Pflicht-Slot → erstes kostenloses Item), nicht zu einem Fehler.

Verweise: ADR-0001, ADR-0004, `docs/03-design/gamification.md`.
