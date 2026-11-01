# api/avatar

Gemeinsame Avatar-Bausteine für App und Backend (ADR-0017).

## Herkunft

- `avataaars.json` – DiceBear-Stildefinition „Avataaars“, unverändert (minifiziert) aus `@dicebear/styles` **10.6.0**
  (`dist/avataaars.min.json`, Schema `@dicebear/schema` 1.4.0).
- SHA-256: `1e64e2d7832211a341df020aa35a74c90d7fe8469af55ceb5d7878b5068a72b2`
- Datei ist Daten, kein Code: nicht formatieren, nicht von Hand ändern.

## Lizenz

DiceBear-Code: MIT. Artwork: „Avataaars“ von Pablo Stanley, „Free for personal and commercial use“.
Namensnennung (wörtlich aus `meta.license.text`):

> Remix of „Avataaars” (https://avataaars.com/) by „Pablo Stanley”, licensed under „Free for personal and commercial use” (https://avataaars.com/)

Die App zeigt die Nennung ab Phase 4b unter „Familie“ → „Über“.

## Verwendung

- Backend: `AvatarCatalogTest` prüft, dass jedes Katalog-Item (Variante/Farbe) in der Definition existiert.
- App: `core:ui` bindet die Datei als Asset ein und rendert daraus per AndroidSVG (`AvatarSvg`).

## Aktualisieren

Neue Version laden, SHA-256 hier eintragen, beide Testsuiten (Backend `AvatarCatalogTest`, Android `core:ui`) laufen lassen.
Item-IDs (`<slot>.<variante>`) dürfen nie verschwinden – gespeicherte Avatare verweisen darauf.
