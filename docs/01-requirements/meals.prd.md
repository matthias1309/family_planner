# Essensplaner (Modul `MEAL`)

## Problem Statement

Unsere fünfköpfige Familie plant das Essen der Woche auf einem handgeschriebenen Zettel am Kühlschrank und stimmt
sich mündlich bzw. per WhatsApp ab. Die beiden älteren Kinder kochen je einmal pro Woche selbst und müssen dafür
rechtzeitig Gericht und Zutaten melden; zwei Kinder leben nur zu 50 % bei uns, der Älteste ist viel unterwegs.
Ergebnis: Rückfragen und Hinterherlaufen vor der Picnic-Bestellung am Sonntagabend, und nicht jeder weiß
verlässlich, wann es was gibt — besonders wichtig für das mittlere Kind (leicht autistische Züge).

## Evidence

- Heutiger Prozess: Zettel am Kühlschrank + WhatsApp/mündlich — fehleranfällig (Aussage Eltern).
- Picnic-Bestellung muss bis Sonntagabend stehen; fehlende Infos der Kinder führen zu Rückfragen (Aussage Eltern).
- Wechselmodell (Ankunft Di, Abreise im 2-Wochen-Wechsel Do oder So) macht synchrone Absprache schwierig.
- Bestehende Self-Hosted-Lösungen (Mealie, Tandoor, Grocy) kennen keine Kinderrollen, Kochtage, Fristen pro
  Wechselmodell oder Elternfreigabe — siehe Research Summary.

## Proposed Solution

Ein Modul `meals` im Familienplaner: ein gemeinsamer Wochenplan (Mo–So), in dem die Eltern ihre Gerichte planen und
die kochenden Kinder ihr Gericht samt Zutaten einreichen. Die Eltern geben Kindergerichte frei (Qualitätsregeln wie
„enthält Gemüse“ zunächst manuell). Lieblingsgerichte liegen als Rezepte mit strukturierten Zutaten vor (auch aus der
REWE-Rezeptwelt importierbar) und lassen sich beim Planen direkt auswählen. Aus freigegebenen Gerichten entsteht eine
Einkaufsliste mit zusammengeführten Mengen für die Picnic-Bestellung.
Ein Anwesenheitskalender je Kind bestimmt Kochtage und Einreichfrist; die App erinnert die Kinder lokal vorher.
Eigenbau statt Mealie, weil die familienspezifische Logik (Rollen, Fristen, Freigabe) der Kern ist und sich ohnehin
nicht in vorhandene Tools einbauen lässt.

## Key Hypothesis

Wir glauben, dass ein gemeinsamer digitaler Wochenplan mit Fristen und Elternfreigabe die Abstimmung per WhatsApp und
Zuruf ersetzt.
Wir wissen, dass wir richtig liegen, wenn wir **4 Wochen in Folge** die Picnic-Bestellung am Sonntagabend
**ohne eine einzige Rückfrage** abschicken.

## What We're NOT Building

- Nährwerte/Kalorien — kein Bedarf geäußert.
- Vorratshaltung / Bestandsführung — Grocy-Domäne, nicht das Problem.
- Budget-Tracking — kein Bedarf geäußert.
- Zugang für den anderen Elternhaushalt, Großeltern, Gäste — nur die fünf Familienmitglieder.
- iOS-App — alle haben Android.
- Fernzugriff von unterwegs — App bleibt LAN-only (ADR-0006, ADR-0007).
- Push über Firebase/ntfy — lokale Benachrichtigungen reichen (siehe Decisions Log).

## Success Metrics

| Metric | Target | How Measured |
|--------|--------|--------------|
| Bestellungen ohne Rückfrage | 4 Wochen in Folge | Eltern notieren pro Woche: Rückfrage nötig ja/nein |
| Wochenplan vollständig bis Bestellfrist | ≥ 90 % der Wochen | Wochenstatus zur Bestellfrist (alle 7 Tage geplant, Kindergerichte freigegeben) |
| Kindergerichte fristgerecht eingereicht | ≥ 80 % | Einreichzeitpunkt vs. Kind-Frist (aus Daten) |
| Zettel am Kühlschrank abgeschafft | ja, nach 4 Wochen | Beobachtung der Eltern |

## Open Questions

- [x] Nach Ablauf der Kind-Frist: **keine Sperre** — Einreichen bleibt möglich, wird als „nach Frist“ markiert.
- [x] Uhrzeiten: Bestellfrist und Kind-Frist jeweils **16:00 Uhr** (konfigurierbar).
- [x] Ferien/Abweichungen: Anwesenheit **frei eintragbar und überschreibbar** (beliebige Zeiträume).
- [x] Zutaten: zunächst **Freitext**; Übernahme aus hinterlegten Rezepten **vor** der Einkaufsliste (Phase 5, ADR-0012).
- [x] Rezepte anlegen, importieren, ändern und löschen dürfen **nur Eltern**; Kinder sehen alle Rezepte und wählen sie
  für ihr Gericht aus (Anwender, 2026-10-18).
- [x] Portionen-Vorschlag beim Übernehmen: **Anzahl der an dem Tag anwesenden Mitglieder**, änderbar (Anwender, 2026-10-18).
- [x] Kind darf das Kochen einer Woche **absagen** (z. B. Krankheit, Termin).
- [x] Absage muss mit **Freitext begründet** werden; keine Bestätigung durch Eltern nötig (sie sehen sie im Wochenstatus).

---

## Users & Context

**Primary Users**
- **Eltern (2):** schließen die Woche ab, planen ihre Gerichte, geben Kindergerichte frei, bestellen bei Picnic.
- **Kochende Kinder (16 J., 50 %; 18 J., dauerhaft, viel unterwegs):** planen und kochen einmal pro Woche ein Gericht.
- **Mitlesende:** alle Kinder, insbesondere das mittlere Kind (16 J.), für das Vorhersehbarkeit besonders wichtig ist.
  Das jüngste Kind (10 J., 50 %) liest nur mit.
- Alle haben ein eigenes Android-Handy.

- **Current behavior**: Zettel am Kühlschrank; Gericht/Zutaten der Kinder per WhatsApp oder mündlich.
- **Trigger**: Die neue Woche steht an; die Picnic-Bestellung muss bis Sonntagabend raus.
- **Success state**: Sonntagabend ist die Woche vollständig geplant, die Einkaufsliste steht, alle sehen den Plan.

**Job to Be Done**
Wenn die neue Woche ansteht, möchte ich ohne Hinterherlaufen von allen wissen, was gekocht wird und was dafür
gebraucht wird, damit ich Sonntagabend bei Picnic bestellen kann.

**Non-Users**
Alle außer den fünf Familienmitgliedern (anderer Elternhaushalt, Großeltern, Gäste).

---

## Solution Detail

### Fachliche Regeln

- **Planwoche:** Montag bis Sonntag. Geplant wird die *kommende* Woche; Bestellfrist ist Sonntag 16:00 Uhr der laufenden Woche.
- **Anwesenheit (50 %-Kinder):** Ankunft immer dienstags; Abreise im Wechsel alle zwei Wochen donnerstags bzw.
  sonntags. Ferien weichen ab → manuelle Ausnahmen durch Eltern.
- **Kind-Einreichfrist:** letzter Anwesenheitstag des Kindes in der laufenden Woche um 16:00 Uhr, spätestens die
  Bestellfrist (Standard: Sonntag 16:00 Uhr). Für das dauerhaft anwesende Kind gilt die Bestellfrist.
  Nach der Frist bleibt Einreichen möglich, wird aber als „nach Frist“ markiert.
- **Absage:** Ein Kind kann sein Kochen für eine Woche absagen; der Tag fällt dann den Eltern zu.
- **Zutaten:** je Zutat Name (Pflicht), Menge und Einheit (optional). Freitext-Zeilen bleiben möglich (nur Name).
- **Rezepte:** Name, Basis-Portionen, Zutaten, optional Zubereitungsschritte und Quelle. Ein Gericht kann aus einem Rezept
  entstehen: Name und Zutaten werden auf die gewählten Portionen umgerechnet und **kopiert** (ADR-0012); danach ist das
  Gericht frei änderbar, spätere Rezeptänderungen wirken nicht auf bestehende Gerichte.
- **Einkaufsliste:** führt Zutaten mit gleichem Namen und gleicher Einheit zusammen; keine Einheiten-Umrechnung.
- **Kochtage:** Ein Kind kann nur an einem Tag kochen, an dem es in der Planwoche anwesend ist.
- **Qualitätsregeln** (z. B. „enthält Gemüse“): zunächst durch Elternfreigabe geprüft, nicht automatisch.

### Anforderungen

#### Wochenplan

##### REQ-MEAL-001 Wochenplan ansehen
Als Familienmitglied möchte ich den Essensplan der Woche (Mo–So) sehen, damit ich weiß, wann es was gibt.

**Abnahmekriterien**
- Given ein geplanter Wochenplan, When ich den Essensplan öffne, Then sehe ich für jeden Tag Gericht und Koch/Köchin.
- Given ein Tag ohne Gericht, When ich den Plan öffne, Then ist der Tag als „noch offen“ erkennbar.
- Given es ist Sonntag, When ich den Plan öffne, Then kann ich zur kommenden Woche wechseln.

##### REQ-MEAL-002 Plan ohne Heimnetz lesen
Als Familienmitglied möchte ich den zuletzt synchronisierten Plan auch unterwegs sehen, damit ich mich auch außer
Haus darauf verlassen kann.

**Abnahmekriterien**
- Given der Plan wurde im WLAN synchronisiert, When ich ohne Heimnetz die App öffne, Then sehe ich den letzten Stand
  mit Hinweis auf den Zeitpunkt der letzten Synchronisierung.

#### Anwesenheit & Fristen

##### REQ-MEAL-010 Anwesenheitsmuster pflegen
Als Elternteil möchte ich je Kind das Wechselmodell hinterlegen, damit Kochtage und Fristen automatisch stimmen.

**Abnahmekriterien**
- Given ein Kind mit Muster „Ankunft Di, Abreise im 2-Wochen-Wechsel Do/So“ und Referenzwoche, When ich eine beliebige
  Woche ansehe, Then sind die Anwesenheitstage des Kindes korrekt berechnet.
- Given ein dauerhaft anwesendes Kind, When ich eine Woche ansehe, Then ist es an allen Tagen anwesend.

##### REQ-MEAL-011 Anwesenheit frei eintragen und überschreiben
Als Elternteil möchte ich die Anwesenheit eines Kindes für beliebige Zeiträume frei eintragen, damit Ferien und
andere Abweichungen abgebildet werden.

**Abnahmekriterien**
- Given ein Zeitraum, für den das Muster „abwesend“ ergibt, When ich ihn als „anwesend“ eintrage, Then gelten alle
  Tage darin als Anwesenheitstage (Kochtag möglich, Frist neu berechnet).
- Given ein Zeitraum, für den das Muster „anwesend“ ergibt, When ich ihn als „abwesend“ eintrage, Then gelten die
  Tage als Abwesenheitstage.
- Given eine Ausnahme, When ich sie entferne, Then gilt wieder das Muster.

##### REQ-MEAL-012 Kind-Einreichfrist berechnen
Als kochendes Kind möchte ich sehen, bis wann ich mein Gericht einreichen muss.

**Abnahmekriterien**
- Given ein Kind reist in der laufenden Woche am Donnerstag ab, When die Frist berechnet wird, Then ist sie Donnerstag
  16:00 Uhr.
- Given ein Kind ist bis Sonntag anwesend, When die Frist berechnet wird, Then ist sie gleich der Bestellfrist.
- Given das dauerhaft anwesende Kind, When die Frist berechnet wird, Then ist sie gleich der Bestellfrist.

##### REQ-MEAL-013 Bestellfrist konfigurieren
Als Elternteil möchte ich Wochentag und Uhrzeit der Bestellfrist sowie die Uhrzeit der Kind-Frist einstellen
(Standard: Sonntag 16:00 Uhr bzw. 16:00 Uhr am Abreisetag).

**Abnahmekriterien**
- Given ich ändere die Bestellfrist, When Fristen berechnet werden, Then gelten sie ab der nächsten Planwoche.

#### Kindergericht

##### REQ-MEAL-020 Kochgericht einreichen
Als kochendes Kind möchte ich mein Gericht mit Tag und Zutatenliste einreichen, damit die Eltern es bestellen können.

**Abnahmekriterien**
- Given ich bin zur Planwoche Koch/Köchin, When ich Name, Tag und Zutaten eingebe und einreiche, Then ist das Gericht
  im Plan als „wartet auf Freigabe“ sichtbar.
- Given ich habe kein Gericht eingegeben, When ich einreiche, Then werden fehlender Name oder fehlende Zutaten gemeldet.
- Given die Zutaten, When ich sie eingebe, Then erfasse ich sie als Freitext (eine Zeile pro Zutat).
- Given meine Frist ist abgelaufen, When ich einreiche, Then wird das Gericht angenommen und als „nach Frist“ markiert.

##### REQ-MEAL-021 Nur Anwesenheitstage wählbar
Als Elternteil möchte ich, dass ein Kind nur an seinen Anwesenheitstagen kochen kann.

**Abnahmekriterien**
- Given ein Kind ist in der Planwoche Di–Do anwesend, When es einen Tag wählt, Then sind nur Di, Mi, Do auswählbar.
- Given ein bereits gewählter Tag wird durch eine Ausnahme zum Abwesenheitstag, When der Plan angezeigt wird, Then
  ist das Gericht als „Tag ungültig“ markiert.

##### REQ-MEAL-022 Eingereichtes Gericht ändern
Als kochendes Kind möchte ich mein Gericht bis zur Frist ändern können.

**Abnahmekriterien**
- Given ein eingereichtes, noch nicht freigegebenes Gericht vor der Frist, When ich es ändere, Then bleibt es
  „wartet auf Freigabe“.
- Given ein freigegebenes Gericht, When ich es ändere, Then muss es erneut freigegeben werden.

##### REQ-MEAL-023 Kochen absagen
Als kochendes Kind möchte ich mein Kochen für eine Woche absagen können (z. B. Krankheit, Termin).

**Abnahmekriterien**
- Given ich bin Koch/Köchin der Planwoche, When ich mit Begründung (Freitext) absage, Then sehen die Eltern Absage und
  Begründung im Wochenstatus und ich erhalte keine Erinnerungen mehr für diese Woche.
- Given ich gebe keine Begründung an, When ich absage, Then wird die Begründung verlangt.
- Given ich hatte schon ein Gericht eingereicht, When ich absage, Then wird der Tag wieder „offen“ und die Zutaten
  verschwinden von der Einkaufsliste.

#### Freigabe & Elternplanung

##### REQ-MEAL-030 Kindergericht freigeben oder ablehnen
Als Elternteil möchte ich ein eingereichtes Gericht freigeben oder mit Begründung ablehnen (z. B. „kein Gemüse“).

**Abnahmekriterien**
- Given ein Gericht „wartet auf Freigabe“, When ich freigebe, Then ist es „freigegeben“ und seine Zutaten stehen auf
  der Einkaufsliste.
- Given ein Gericht „wartet auf Freigabe“, When ich ohne Begründung ablehne, Then wird eine Begründung verlangt.
- Given ein abgelehntes Gericht, When das Kind die App öffnet, Then sieht es die Begründung und kann überarbeiten.

##### REQ-MEAL-031 Gericht stellvertretend eintragen
Als Elternteil möchte ich ein Kindergericht selbst eintragen, wenn das Kind es mir per WhatsApp schickt.

**Abnahmekriterien**
- Given das Kind hat nichts eingereicht, When ich für das Kind Gericht, Tag und Zutaten eintrage, Then ist das Gericht
  dem Kind zugeordnet und direkt „freigegeben“.

##### REQ-MEAL-032 Elterngerichte planen
Als Elternteil möchte ich die übrigen Tage mit eigenen Gerichten und Zutaten belegen.

**Abnahmekriterien**
- Given ein offener Tag, When ich ein Gericht mit Zutaten eintrage, Then ist der Tag belegt und die Zutaten stehen
  auf der Einkaufsliste.

##### REQ-MEAL-033 Wochenstatus für Eltern
Als Elternteil möchte ich auf einen Blick sehen, was vor der Bestellung noch fehlt.

**Abnahmekriterien**
- Given eine Planwoche, When ich den Wochenstatus öffne, Then sehe ich offene Tage, ausstehende Einreichungen (mit
  Frist je Kind) und ausstehende Freigaben.
- Given alle Tage sind belegt und alle Kindergerichte freigegeben, When ich den Status öffne, Then ist die Woche
  „bereit zur Bestellung“.

#### Rezepte

##### REQ-MEAL-060 Rezepte anlegen und pflegen
Als Elternteil möchte ich unsere Lieblingsrezepte mit Zutaten hinterlegen, damit wir sie nicht jede Woche neu
abtippen.

**Abnahmekriterien**
- Given ich bin ein Kind, When ich die Rezeptliste öffne, Then kann ich alle Rezepte ansehen, aber keine anlegen,
  ändern, löschen oder importieren (Server lehnt mit 403 ab).
- Given ich lege ein Rezept mit Name, Basis-Portionen und Zutaten (Name, optional Menge und Einheit) an, When ich
  speichere, Then erscheint es in der Rezeptliste aller Familienmitglieder.
- Given ein Rezept ohne Name, ohne Zutaten oder mit Portionen < 1, When ich speichere, Then wird der Fehler gemeldet.
- Given ein Rezept, When ich es ändere oder lösche, Then bleiben Gerichte, die schon daraus übernommen wurden, unverändert.
- Given die Rezeptliste, When ich einen Suchbegriff eingebe, Then sehe ich nur Rezepte, deren Name ihn enthält.

##### REQ-MEAL-061 Rezept aus der REWE-Rezeptwelt importieren
Als Elternteil möchte ich ein Rezept von rewe.de über „Teilen“ oder per Link übernehmen, damit Lieblingsrezepte
mit wenigen Tippern in der App sind.

**Abnahmekriterien**
- Given die Seite `rewe.de/rezepte/spaghetti-lauch-carbonara-mit-ohne-speck/`, When ich sie mit der App teile, Then sehe
  ich einen vorausgefüllten Rezept-Editor mit Name, 4 Portionen, 11 Zutaten (u. a. „500 g Vollkornspaghetti“,
  „2 Stange(n) Lauch“, „Salz“ ohne Menge), den Zubereitungsschritten und der Quell-URL.
- Given ein importiertes Rezept, When ich im Editor Zutaten korrigiere und speichere, Then wird es wie ein manuell
  angelegtes Rezept gespeichert; ohne Speichern wird nichts übernommen.
- Given Zutaten mit Eigenmarken-Präfix wie „REWE Bio Rapsöl“, „REWE Beste Wahl Kidney-Bohnen“ oder „ja! Gouda
  gerieben“, When importiert wird, Then lauten die Namen „Rapsöl“, „Kidney-Bohnen“ und „Gouda gerieben“ (Menge und
  Einheit bleiben erhalten, z. B. „1 Dose(n) Kidney-Bohnen“).
- Given eine Zutatenzeile, die sich nicht in Menge/Einheit/Name zerlegen lässt (z. B. „Saft von 1 Limette“), When
  importiert wird, Then wird die ganze Zeile als Name übernommen.
- Given eine Seite ohne erkennbares Rezept oder ohne Internetverbindung, When ich importiere, Then erhalte ich eine
  verständliche Meldung und kann das Rezept manuell anlegen.

##### REQ-MEAL-062 Gericht aus Rezept auswählen
Als kochendes Kind oder Elternteil möchte ich beim Planen ein Rezept aus einer Liste wählen, damit Name und richtige
Mengen automatisch übernommen werden.

**Abnahmekriterien**
- Given der Gericht-Editor (Kind einreichen, Eltern planen oder stellvertretend eintragen), When ich ein Rezept wähle,
  Then sind Name und Zutaten übernommen und die Portionen mit der Anzahl der an dem Tag anwesenden Mitglieder vorbelegt.
- Given ein Rezept für 4 Portionen mit „500 g Vollkornspaghetti“, When ich 5 Portionen wähle, Then steht im Gericht
  „625 g Vollkornspaghetti“; Zutaten ohne Menge bleiben ohne Menge.
- Given ein aus einem Rezept übernommenes Gericht, When ich Zutaten ändere, Then gelten die Regeln aus REQ-MEAL-020–032
  (Freigabe, erneute Freigabe) unverändert.
- Given ich wähle kein Rezept, When ich das Gericht anlege, Then kann ich Name und Zutaten wie bisher frei eingeben.

##### REQ-MEAL-063 Rezept beim Kochen ansehen
Als kochendes Kind möchte ich zu meinem Gericht das Rezept mit Zubereitungsschritten sehen.

**Abnahmekriterien**
- Given ein Gericht, das aus einem Rezept mit Schritten übernommen wurde, When ich es öffne, Then kann ich die
  Zubereitungsschritte und die Zutaten mit den Mengen des Gerichts ansehen.

#### Einkaufsliste

##### REQ-MEAL-040 Einkaufsliste erzeugen
Als Elternteil möchte ich eine Einkaufsliste mit den Zutaten aller freigegebenen Gerichte der Planwoche.

**Abnahmekriterien**
- Given freigegebene Gerichte, When ich die Einkaufsliste öffne, Then sind Zutaten mit gleichem Namen und gleicher
  Einheit zu einem Eintrag mit addierter Menge zusammengeführt, und je Eintrag ist sichtbar, aus welchen Gerichten er
  stammt.
- Given Zutaten ohne Menge (z. B. „Salz“) oder Freitext-Zeilen, When die Liste erzeugt wird, Then erscheinen sie einmal
  je Name, ohne Menge.
- Given gleiche Zutat mit verschiedenen Einheiten (z. B. „200 g Sahne“ und „1 Becher Sahne“), When die Liste erzeugt
  wird, Then bleiben es getrennte Einträge.
- Given ein Gericht „wartet auf Freigabe“, When ich die Liste öffne, Then sind seine Zutaten nicht enthalten.

##### REQ-MEAL-041 Einkaufsliste bearbeiten
Als Elternteil möchte ich Einträge ergänzen und abhaken (bestellt), damit ich bei Picnic nichts vergesse.

**Abnahmekriterien**
- Given die Einkaufsliste, When ich einen freien Eintrag hinzufüge, Then erscheint er in der Liste.
- Given ein Eintrag, When ich ihn abhake, Then ist er als erledigt markiert.

#### Erinnerungen

##### REQ-MEAL-050 Erinnerung vor der Kind-Frist
Als kochendes Kind möchte ich 2 Tage und 1 Tag vor meiner Frist erinnert werden, wenn ich noch nichts eingereicht habe.

**Abnahmekriterien**
- Given meine Frist ist Donnerstag und ich habe nichts eingereicht, When es Dienstag bzw. Mittwoch ist, Then erhalte
  ich je eine Android-Benachrichtigung der App.
- Given ich habe eingereicht, When der Erinnerungszeitpunkt kommt, Then erhalte ich keine Erinnerung.
- Given mein Handy ist nicht im Heimnetz, When der Erinnerungszeitpunkt kommt, Then erhalte ich die Erinnerung
  trotzdem (lokal eingeplant).

##### REQ-MEAL-051 WhatsApp-Hinweis außer Haus
Als kochendes Kind möchte ich in der Erinnerung sehen, was ich tun soll, wenn ich nicht zu Hause bin.

**Abnahmekriterien**
- Given mein Handy erreicht das Backend nicht, When ich die Erinnerung öffne, Then sehe ich den Hinweis, Gericht und
  Zutaten per WhatsApp an die Eltern zu schicken.

##### REQ-MEAL-052 Statusänderungen melden
Als kochendes Kind möchte ich benachrichtigt werden, wenn mein Gericht freigegeben oder abgelehnt wurde.

**Abnahmekriterien**
- Given mein Gericht wurde abgelehnt, When mein Handy das nächste Mal im Heimnetz synchronisiert, Then erhalte ich
  eine Benachrichtigung mit der Begründung.

#### Voraussetzung aus `AUTH` (minimal, wird in `auth.prd.md` ausgearbeitet)

- **REQ-AUTH-001** Familie mit fünf Mitgliedern und Rollen *Elternteil* / *Kind*; Kinder mit Merkmal „kocht“ und
  Anwesenheitsmuster.
- **REQ-AUTH-002** Jedes Gerät ist einem Mitglied zugeordnet und angemeldet; Eltern-Aktionen sind Eltern vorbehalten.

### Core Capabilities (MoSCoW)

| Priority | Capability | Rationale |
|----------|------------|-----------|
| Must | Wochenplan Mo–So für alle (REQ-MEAL-001/002) | Ersetzt den Zettel; Vorhersehbarkeit fürs mittlere Kind |
| Must | Anwesenheitskalender + Fristen (REQ-MEAL-010–013) | Ohne ihn keine korrekten Kochtage/Fristen im 50 %-Modell |
| Must | Kindergericht einreichen, absagen + Elternfreigabe (REQ-MEAL-020–031) | Kern des Kochtag-Prozesses, Qualitätsregeln |
| Must | Elterngerichte + Wochenstatus (REQ-MEAL-032/033) | „Bereit zur Bestellung“ ist das Erfolgskriterium |
| Must | Rezeptsammlung, REWE-Import, Gericht aus Rezept (REQ-MEAL-060–062) | Kein Abtippen; richtige Mengen; Grundlage der Einkaufsliste (ADR-0012) |
| Must | Einkaufsliste mit Zusammenführung (REQ-MEAL-040/041) | Grundlage für die Picnic-Bestellung |
| Should | Rezept beim Kochen ansehen (REQ-MEAL-063) | Kinder kochen nach Rezept; Daten liegen ohnehin vor |
| Must | Erinnerungen (REQ-MEAL-050–052) | Ersetzt das Hinterherlaufen |
| Later | Vorschläge der Kinder für Elterngerichte | Mitsprache — nach MVP |
| Later | Einheiten-Umrechnung auf der Einkaufsliste (g/kg, ml/l) | Erst wenn Mischungen in der Praxis stören |
| Later | Rezepte offline lesen | Gericht samt Zutaten ist schon offline sichtbar (ADR-0011) |
| Later | Automatische Qualitätsprüfung (z. B. Gemüse) | Erst wenn manuelle Freigabe Muster zeigt |
| Later | Übergabe an Picnic (Home Assistant `picnic.add_product`) | Ziel, braucht strukturierte Zutaten + Produktzuordnung |
| Later | Widget/Startbildschirm „Heute gibt es …“ | Vorhersehbarkeit fürs mittlere Kind weiter erhöhen |
| Won't | Nährwerte, Vorrat, Budget, iOS, externe Nutzer | Siehe „What We're NOT Building“ |

### MVP Scope

Alle *Must*-Anforderungen oben plus minimales `AUTH` (REQ-AUTH-001/002). Damit lässt sich eine vollständige Woche
von der Einreichung bis zur Bestellung ohne WhatsApp-Abstimmung abbilden und die Hypothese über 4 Wochen prüfen.

### User Flow

1. Eltern pflegen einmalig Mitglieder, Kochkinder und Anwesenheitsmuster.
2. Montag–Sonntag: App plant Erinnerungen je Kind 2 Tage und 1 Tag vor dessen Frist.
3. Kind reicht Gericht + Zutaten (frei oder aus einem Rezept) für einen Anwesenheitstag der kommenden Woche ein (oder schickt es außer Haus per
   WhatsApp → Eltern tragen es ein).
4. Eltern geben frei oder lehnen mit Begründung ab; Kind überarbeitet.
5. Eltern belegen die übrigen Tage.
6. Wochenstatus „bereit zur Bestellung“ → Eltern bestellen anhand der Einkaufsliste bei Picnic.
7. Alle sehen den Plan der neuen Woche.

---

## Technical Approach

**Feasibility**: MITTEL — Fachlogik überschaubar; Aufwand liegt im Anwesenheitsmodell und darin, dass `AUTH` vorher
stehen muss.

**Architecture Notes**
- Eigenes Gradle-Modul `backend/meals` + Android-Feature-Modul; kennt andere Feature-Module nicht (ADR-0001).
- API zuerst in `api/openapi.yaml` (ADR-0004).
- Anwesenheit/Fristen als reine, gut testbare Domänenfunktionen (Muster + Referenzwoche + Ausnahmen → Tage/Frist),
  Zeitzone `Europe/Berlin`.
- Benachrichtigungen **lokal** in der App (WorkManager/AlarmManager), eingeplant beim Sync anhand der berechneten
  Fristen; Statusänderungen per Hintergrund-Sync im Heimnetz. Keine zweite App, kein Firebase.
- Zugriff bleibt LAN-only; Fristen sind so gelegt, dass Kinder vor der Abreise einreichen (ADR-0007).
- Zutaten seit Phase 5 strukturiert (Name, optional Menge/Einheit); Freitext bleibt als Zutat nur mit Name (ADR-0012).
- Rezept-Import in der App (Teilen-Menü → unsichtbare WebView → schema.org-`Recipe` aus JSON-LD → Editor zur Prüfung);
  das Backend lädt keine externen Seiten (ADR-0012).

**Technical Risks**

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Anwesenheitsmuster/Ferien falsch berechnet → falsche Fristen | M | Reine Domänenfunktion, Unit-Tests für beide Wochentypen, Ausnahmen, Jahres-/DST-Wechsel |
| Lokale Erinnerungen werden von Android (Doze, Herstelleroptimierung) verzögert | M | Exakte Alarme anfragen, auf Testgeräten der Familie prüfen |
| Kind außer Haus kann nicht einreichen | M | Frist vor Abreise (ADR-0007) + WhatsApp-Fallback mit Eltern-Eintrag |
| `AUTH` verzögert den Start | M | Minimal halten (5 feste Mitglieder, Geräte-Anmeldung) |
| Freitext-Zutaten erschweren spätere Picnic-Übergabe | M | Strukturierte Zutaten mit Phase 5 (ADR-0012) |
| REWE ändert das Seitenformat oder sperrt auch die WebView | M | Allgemeiner schema.org-Leser statt HTML-Scraping; manuelles Anlegen bleibt immer möglich |
| Zutatenzeilen falsch zerlegt (z. B. „Saft von 1 Limette“) | H | Reine Zerlege-Funktion mit Tests an echten REWE-Zeilen; Editor zur Prüfung vor dem Speichern |
| Umstellung der Zutaten bricht ältere App-Versionen | M | API additiv erweitern, `ingredients: string[]` bleibt bis zur Ablösung lesbar |

---

## Implementation Phases

<!--
  STATUS: pending | in-progress | complete
  PARALLEL: phases that can run concurrently (e.g., "with 3" or "-")
  DEPENDS: phases that must complete first (e.g., "1, 2" or "-")
  PRP: link to generated plan file once created
-->

| # | Phase | Description | Status | Parallel | Depends | PRP Plan |
|---|-------|-------------|--------|----------|---------|----------|
| 1 | Fundament & AUTH | Gradle-Gerüst, `core`, Mitglieder/Rollen, Geräte-Anmeldung, CI — aufgeteilt in 1a Backend/CI/Deploy und 1b Android | complete | - | - | 1a: `.claude/PRPs/plans/completed/fundament-auth-backend.plan.md` (umgesetzt, Report: `.claude/PRPs/reports/fundament-auth-backend-report.md`); 1b: `.claude/PRPs/plans/completed/fundament-auth-android.plan.md` (umgesetzt, Report: `.claude/PRPs/reports/fundament-auth-android-report.md`; Geräte-Abnahme 2026-10-17 auf Elternhandy und einem Testhandy; Kopplung der übrigen Familienhandys folgt im Betrieb) |
| 2 | Anwesenheit & Fristen | Muster, Ausnahmen, Fristberechnung (REQ-MEAL-010–013) — aufgeteilt in 2a Backend/API und 2b Android-Eltern-UI | complete | with 3 | 1 | 2a: `.claude/PRPs/plans/completed/anwesenheit-fristen-backend.plan.md` (Backend umgesetzt, Report: `.claude/PRPs/reports/anwesenheit-fristen-backend-report.md`); 2b: `.claude/PRPs/plans/completed/anwesenheit-fristen-android.plan.md` (umgesetzt, Report: `.claude/PRPs/reports/anwesenheit-fristen-android-report.md`; Geräte-Abnahme 2026-10-18 durch den Anwender, alle Punkte der Checkliste außer dem Offline-Test) |
| 3 | Wochenplan & Elterngerichte | Plan ansehen, Elterngerichte, Offline-Cache (REQ-MEAL-001/002/032) — aufgeteilt in 3a Backend/API und 3b App inkl. Offline-Cache | complete | with 2 | 1 | 3a: `.claude/PRPs/plans/completed/wochenplan-elterngerichte-backend.plan.md` (Backend umgesetzt, Report: `.claude/PRPs/reports/wochenplan-elterngerichte-backend-report.md`); 3b: `.claude/PRPs/plans/completed/wochenplan-elterngerichte-android.plan.md` (umgesetzt, Report: `.claude/PRPs/reports/wochenplan-elterngerichte-android-report.md`; Geräte-Abnahme 2026-10-18 durch den Anwender, alle Punkte der Checkliste) |
| 4 | Einreichung & Freigabe | Kindergericht, Absage, Freigabe, Stellvertretung, Wochenstatus (REQ-MEAL-020–031/033) — aufgeteilt in 4a Backend/API und 4b App | complete | - | 2, 3 | 4a: `.claude/PRPs/plans/completed/einreichung-freigabe-backend.plan.md` (Backend umgesetzt, Report: `.claude/PRPs/reports/einreichung-freigabe-backend-report.md`); 4b: `.claude/PRPs/plans/completed/einreichung-freigabe-android.plan.md` (umgesetzt, Report: `.claude/PRPs/reports/einreichung-freigabe-android-report.md`) |
| 5 | Rezeptsammlung | Rezepte mit strukturierten Zutaten, REWE-Import über die App, Gericht aus Rezept (REQ-MEAL-060–063, ADR-0012) — aufgeteilt in 5a Backend/API, 5b App (Rezepte, Auswahl, Ansicht) und 5c REWE-Import | complete | with 7 | 4 | 5a: `.claude/PRPs/plans/completed/rezeptsammlung-backend.plan.md` (Backend umgesetzt, Report: `.claude/PRPs/reports/rezeptsammlung-backend-report.md`); 5b: `.claude/PRPs/plans/completed/rezepte-app.plan.md` (umgesetzt, Report: `.claude/PRPs/reports/rezepte-app-report.md`); 5c: `.claude/PRPs/plans/completed/rezepte-import.plan.md` (umgesetzt und auf dem Gerät abgenommen; Report: `.claude/PRPs/reports/rezepte-import-report.md`) |
| 6 | Einkaufsliste | Zusammengeführte Zutaten, ergänzen, abhaken (REQ-MEAL-040/041) | pending | with 7 | 5 | - |
| 7 | Erinnerungen | Lokale Erinnerungen, WhatsApp-Hinweis, Status-Sync (REQ-MEAL-050–052) | pending | with 5, 6 | 4 | - |
| 8 | Pilot | 4 Wochen Echtbetrieb, Messung der Hypothese | pending | - | 6, 7 | - |

### Phase Details

**Phase 1: Fundament & AUTH**
- **Goal**: Lauffähiges Grundgerüst, in dem Mitglieder sich anmelden.
- **Scope**: Backend-/Android-Skelett, OpenAPI-Grundlage, REQ-AUTH-001/002, Deployment-Pipeline.
- **Success signal**: Fünf Geräte sind angemeldet, Rollen wirken, Image läuft auf dem Pi.

**Phase 2: Anwesenheit & Fristen**
- **Goal**: Korrekte Anwesenheitstage und Fristen je Kind.
- **Scope**: Domänenlogik + API + Eltern-UI für Muster und Ausnahmen.
- **Success signal**: Tests für beide Wochentypen und Ausnahmen grün; Eltern bestätigen die angezeigten Tage.

**Phase 3: Wochenplan & Elterngerichte**
- **Goal**: Der Zettel ist ersetzbar.
- **Scope**: Plananzeige Mo–So, Elterngerichte mit Zutaten, Offline-Lesen.
- **Success signal**: Alle sehen den Plan auf ihrem Handy.

**Phase 4: Einreichung & Freigabe**
- **Goal**: Kochtag-Prozess vollständig in der App.
- **Scope**: Einreichen/Ändern/Absagen, Freigabe/Ablehnung, Stellvertretung, Wochenstatus.
- **Success signal**: Eine Woche lässt sich bis „bereit zur Bestellung“ durchspielen.

**Phase 5: Rezeptsammlung**
- **Goal**: Lieblingsgerichte mit einem Tipper planen, mit richtigen Mengen.
- **Scope**: 5a — Rezepte (CRUD, Suche), strukturierte Zutaten inkl. Migration der Freitext-Zeilen, Gericht mit
  `recipeId`/`servings`, additive API-Erweiterung. 5b — Rezeptliste und -Editor, Rezeptauswahl im Gericht-Editor mit
  Portionen-Umrechnung, Rezeptansicht beim Kochen. 5c — Import über Teilen-Menü/Link per WebView und schema.org-Leser.
- **Success signal**: Die beiden REWE-Lieblingsrezepte (Lauch-Carbonara, Gemüse-Quesadilla) sind importiert, und ein
  Kind reicht eines davon mit umgerechneten Mengen ein.

**Phase 6: Einkaufsliste**
- **Goal**: Bestellung ohne Nachrechnen.
- **Scope**: Zusammengeführte Zutaten der freigegebenen Gerichte mit Herkunft, freie Einträge, Abhaken.
- **Success signal**: Eltern bestellen bei Picnic nur anhand der Liste.

**Phase 7: Erinnerungen**
- **Goal**: Kein Hinterherlaufen mehr.
- **Scope**: Lokale Erinnerungen 2 Tage/1 Tag vor Frist, WhatsApp-Hinweis, Benachrichtigung bei Statusänderung.
- **Success signal**: Kinder erhalten Erinnerungen auch außer Haus pünktlich.

**Phase 8: Pilot**
- **Goal**: Hypothese prüfen.
- **Scope**: 4 Wochen Echtbetrieb, wöchentliche Notiz „Rückfrage ja/nein“.
- **Success signal**: 4 Wochen in Folge ohne Rückfrage bestellt.

### Parallelism Notes

Phase 2 (reine Domänenlogik Anwesenheit/Fristen) und Phase 3 (Plananzeige/Elterngerichte) berühren getrennte Teile
des Moduls und können parallel laufen. Phase 6 (Einkaufsliste) braucht die strukturierten Zutaten und Portionen aus
Phase 5 und folgt ihr deshalb. Phase 7 (Erinnerungen, Android-lastig) hängt nur an Phase 4 und kann parallel zu 5 und 6
laufen — Achtung: 5b und 7 ändern beide die App; nicht gleichzeitig dieselben Dateien bearbeiten.

---

## Decisions Log

| Decision | Choice | Alternatives | Rationale |
|----------|--------|--------------|-----------|
| Bauen oder übernehmen | Selbst bauen | Mealie + eigene Familienlogik | Familienlogik (Rollen, Fristen, Freigabe) ist der Kern; fehlt in allen Tools |
| Erreichbarkeit unterwegs | Frist vor Abreise, LAN-only (ADR-0007) | VPN, Offline-Sync, Status quo | Kein Fernzugriff nötig, wenn Kinder vor Abreise einreichen; WhatsApp-Fallback |
| Benachrichtigungen | Lokal in der App + Sync im WLAN | ntfy (zweite App), Firebase (Google, Internet) | Fristen sind vorab bekannt; keine zusätzliche App, keine Cloud |
| Kochtage | Nur Anwesenheitstage | Frei wählbar | Kind muss zum Kochen da sein |
| Qualitätsregeln | Manuelle Elternfreigabe | Automatische Prüfung | Einfach starten, später automatisieren |
| Planwoche | Montag–Sonntag | Ab Lieferdatum | Entspricht dem Familienalltag |
| Elterngerichte | Ebenfalls bis Bestellfrist geplant | – | Einkaufsliste muss vollständig sein |
| Picnic | Später über Home Assistant | Sofort | MVP validiert erst den Prozess |
| Fristverstoß | Keine Sperre, Markierung „nach Frist“ | Sperre fürs Kind | Lieber spät als gar nicht; Eltern entscheiden |
| Fristuhrzeit | 16:00 Uhr (Bestellung und Kind) | Abend | Vor der Abreise erreichbar, Zeit für Rückfragen |
| Anwesenheit | Muster + frei eintragbare Zeiträume | Nur Einzeltage | Ferien und Sonderregeln flexibel abbildbar |
| Zutaten | Freitext | Menge/Einheit/Name | Einfach für Kinder; Struktur kommt mit Rezepten |
| Reihenfolge Rezepte/Einkaufsliste (2026-10-18) | Rezepte mit strukturierten Zutaten vor der Einkaufsliste (ADR-0012) | Einkaufsliste aus Freitext zuerst | Auswahl statt Abtippen; Liste kann Mengen zusammenführen und auf Esser umrechnen |
| Rezept-Import (2026-10-18) | In der App über WebView und schema.org-JSON-LD, mit Prüfung im Editor | Server lädt die URL; Mealie daneben | Server-Abruf wird von REWE mit 403/Captcha abgewiesen; kein zweites System |
| Rezeptrechte (2026-10-18) | Nur Eltern legen an, importieren, ändern, löschen | Alle dürfen anlegen | Eltern halten die Sammlung sauber; Kinder wählen nur aus |
| Eigenmarken beim Import (2026-10-18) | Bekannte Marken-Präfixe entfernen („REWE Bio“, „REWE Beste Wahl“, „ja!“, „REWE“) | Namen unverändert übernehmen | Allgemeine Namen lesen sich besser, lassen sich zusammenführen und später bei Picnic finden |
| Gericht aus Rezept (2026-10-18) | Kopie, umgerechnet auf Portionen | Live-Verknüpfung | Rezeptänderungen dürfen freigegebene Gerichte nicht verändern |
| Absage | Kind darf mit Freitext-Begründung absagen, ohne Elternbestätigung | Nur Eltern; Bestätigung nötig | Krankheit/Termine kommen vor; Eltern sehen Begründung |

---

## Research Summary

**Market Context**
- Mealie, Tandoor, Grocy (alle self-hosted, Docker): Wochenkalender, Rezepte, Einkaufslisten aus dem Plan; Mealie
  gilt als familientauglichste Oberfläche, Tandoor als mächtig, aber komplex; Grocy fokussiert auf Vorrat und
  Haushaltsaufgaben. Keines bildet Kinderrollen, Kochtage, Wechselmodell-Fristen oder Freigaben ab.
  ([Vergleich](https://sumguy.com/mealie-vs-tandoor-vs-grocy/),
  [Tandoor vs Grocy](https://selfhosting.sh/compare/tandoor-vs-grocy/))
- Picnic: offizielle Home-Assistant-Integration mit Aktion `picnic.add_product` (per Produkt-ID oder Name; Name
  nimmt den ersten Suchtreffer) und Warenkorb als To-do-Liste.
  ([Integration](https://home-assistant.io/integrations/picnic),
  [Aktion](https://www.home-assistant.io/actions/picnic.add_product/))

- REWE-Rezeptwelt (geprüft 2026-10-18): Rezeptseiten enthalten ein schema.org-`Recipe` als JSON-LD
  (`mainEntity` eines `Webpage`-Objekts) mit Zutaten als Zeichenketten wie „500.0 g Vollkornspaghetti“. Abruf ohne
  Browser liefert 403 mit Captcha (ADR-0012).

**Technical Context**
- Repository enthält bisher nur Dokumentation und ADRs, keinen Code; `AUTH` muss zuerst entstehen.
- Pi stellt Home Assistant, ntfy, Pi-hole bereit; App ist LAN-only (ADR-0006).

---

## Später (Backlog aus der PRD-Erarbeitung)

- Vorschläge der Kinder für Elterngerichte (Mitsprache).
- ~~Rezeptsammlung; Gericht und Zutaten aus hinterlegten Rezepten übernehmen; strukturierte Zutaten.~~ → Phase 5.
- Einheiten-Umrechnung auf der Einkaufsliste; Rezepte offline lesen.
- Automatische Qualitätsprüfung (z. B. „enthält Gemüse“).
- Picnic-Übergabe der Einkaufsliste über Home Assistant.
- Widget/Startbildschirm „Heute gibt es …“.
- Weiteres Modul: Aufgabenplaner für Kinder (Putzen, Zimmer aufräumen, Hausaufgaben) → `TASK`.

---

*Generated: 2026-10-11 — Updated: 2026-10-18 (Rezepte vor Einkaufsliste, ADR-0012)*
*Status: DRAFT - needs validation*
