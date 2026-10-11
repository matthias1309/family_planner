# Mitglieder & Geräte-Anmeldung (Modul `AUTH`)

Minimales `AUTH` als Voraussetzung für den Essensplaner (siehe [meals.prd.md](meals.prd.md), Abschnitt „Voraussetzung aus AUTH“).
Umsetzung: Phase 1a (Backend/CI/Deploy); die Android-Seite folgt in Phase 1b.

## Abgrenzung

- **Nicht Teil von AUTH:** das Merkmal „kocht“ und die Anwesenheitsmuster der Kinder aus dem ursprünglichen
  REQ-AUTH-001-Wortlaut. Sie sind Feature-Begriffe und gehören ins Modul `meals` (ADR-0001: `core` kennt keine
  Feature-Begriffe) — Umsetzung in Phase 2 des Essensplaners.
- Mitglieder umbenennen/löschen und Rollen ändern: erst bei Bedarf (YAGNI). Bei einem Tippfehler im Pilotvorlauf wird
  die Datenbank zurückgesetzt.
- Passwörter, Token-Ablauf/Refresh, Mandantenfähigkeit.

## Anforderungen

### REQ-AUTH-001 Mitglieder & Rollen
Als Elternteil möchte ich Familienmitglieder mit Namen und Rolle (Elternteil / Kind) anlegen, damit Funktionen wissen,
wer etwas tut und ob es eine Eltern-Aktion ist.

**Abnahmekriterien**
- Given ein Elternteil, When es ein Mitglied mit Name und Rolle anlegt, Then erscheint es in der Mitgliederliste.
- Given ein Kind-Gerät, When es ein Mitglied anlegen will, Then wird der Aufruf mit 403 abgewiesen.
- Given ein Mitglied „Lea“ existiert, When ein Mitglied „lea“ angelegt werden soll, Then antwortet das System mit 409.
- Given ein leerer oder mehr als 40 Zeichen langer Name, When ein Mitglied angelegt werden soll, Then antwortet das System mit 400.

### REQ-AUTH-002 Geräte-Anmeldung
Als Familie möchte ich jedes Handy einem Mitglied zuordnen, damit die App ohne Passwort weiß, wer sie benutzt.

**Abnahmekriterien**
- Given keine Mitglieder und ein gültiges Setup-Token, When die Erst-Einrichtung läuft, Then entstehen das erste
  Elternteil, sein Gerät und ein Geräte-Token.
- Given bereits existierende Mitglieder, When die Erst-Einrichtung erneut läuft, Then antwortet das System mit 409.
- Given ein falsches Setup-Token, When die Erst-Einrichtung läuft, Then antwortet das System mit 401.
- Given ein Kopplungscode (6 Ziffern, 15 Minuten gültig, einmalig), When ein Gerät damit koppelt, Then erhält es ein
  Token für das zugehörige Mitglied.
- Given ein abgelaufener, benutzter oder unbekannter Code, When ein Gerät koppeln will, Then antwortet das System mit 401.
- Given ein neuer Kopplungscode für ein Mitglied, When der alte Code eingelöst wird, Then ist er ungültig.
- Given ein Request ohne oder mit ungültigem Token, When er einen geschützten Endpunkt aufruft, Then antwortet das System mit 401.
- Given ein Kind-Gerät, When es einen Eltern-Endpunkt aufruft, Then antwortet das System mit 403.

### REQ-AUTH-003 Gerät entfernen
Als Elternteil möchte ich ein Gerät entfernen können (z. B. Handy verloren), damit es keinen Zugriff mehr hat.

**Abnahmekriterien**
- Given ein Elternteil, When es ein Gerät entfernt, Then wird dessen Token danach mit 401 abgewiesen.

### REQ-OPS-001 Betrieb
Als Betreiber möchte ich, dass sich das Backend selbst aktualisiert und Fehler meldet, damit der Pi wartungsarm bleibt.

**Abnahmekriterien**
- Given eine laufende Instanz, When `/health` aufgerufen wird, Then meldet es die Erreichbarkeit der Datenbank.
- Given ein neues `stable`-Image in GHCR, When der Update-Timer läuft, Then ist es binnen 5 Minuten deployt.
- Given ein neues Image mit fehlschlagendem Healthcheck, When das Update läuft, Then erfolgt ein Rollback auf das
  vorherige Image und eine ntfy-Meldung.
- Given ausstehende Datenbank-Migrationen, When das Backend startet, Then wird zuvor ein Backup der Datenbank geschrieben.
