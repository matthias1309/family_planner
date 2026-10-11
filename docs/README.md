# Dokumentation

Leichtgewichtiges V-Modell: jede Entwurfsebene links hat eine Prüfebene rechts.
Das V wird **pro Feature** durchlaufen, nicht einmal für das ganze Projekt.

```
01-requirements  ─────────────────────────  E2E-/Abnahmetests
     02-architecture (+ ADRs)  ─────────  Integrationstests
          03-design (pro Modul)  ───────  Unit-Tests (TDD)
                         Implementierung
```

| Ordner | Inhalt |
|---|---|
| [01-requirements](01-requirements/README.md) | PRD, Anforderungen mit IDs und Abnahmekriterien |
| [02-architecture](02-architecture/README.md) | Systemüberblick, Kontext, [ADRs](02-architecture/adr/) |
| [03-design](03-design/README.md) | Moduldesign je Feature |
| [04-verification](04-verification/README.md) | Teststrategie und Traceability-Matrix |
