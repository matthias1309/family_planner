# Anforderungen

Wird mit `/ecc:prp-prd` erstellt — ein PRD pro Modul als `<modul>.prd.md` (z. B. `meals.prd.md`), immer hier und nicht unter `.claude/PRPs/prds/`.

## Konventionen

- ID-Schema: `REQ-<MODUL>-<NR>`, z. B. `REQ-TASK-001`, `REQ-AUTH-002`
- Module: `AUTH`, `TASK`, `GAME`, `MEAL`, `BOARD`, `OPS` (Betrieb/nicht-funktional)
- Jede Anforderung hat **Abnahmekriterien** im Given/When/Then-Format — daraus entstehen die E2E-Tests.

```markdown
### REQ-TASK-001 Aufgabe erledigen
Als Familienmitglied möchte ich eine Aufgabe als erledigt markieren, damit alle den Fortschritt sehen.

**Abnahmekriterien**
- Given eine Aufgabe in "In Arbeit", When ich sie als erledigt markiere, Then steht sie in "Erledigt".
```

## MVP-Schnitt

1. `AUTH` (minimal): Familie & Mitglieder, Rollen, Geräte-Anmeldung
2. `MEAL`: Essensplaner — siehe [meals.prd.md](meals.prd.md)

Danach als eigene Module: `TASK` (inkl. Aufgaben für Kinder: Putzen, Zimmer, Hausaufgaben), `GAME`, `BOARD`.
