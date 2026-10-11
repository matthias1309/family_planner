# Anforderungen

Wird mit `/ecc:prp-prd` erstellt (Datei `prd.md`).

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

1. `AUTH`: Familie & Mitglieder, Login
2. `TASK`: Kanban-Board mit Aufgaben

Danach als eigene Module: `GAME`, `MEAL`, `BOARD`.
