# ADR-0001: Monorepo & modularer Monolith

- **Status:** Akzeptiert
- **Datum:** 2026-10-11

## Kontext

Das System soll modular erweiterbar sein (Aufgaben, Gamification, Essensplaner, Infoboard), läuft aber auf einem
einzelnen, geteilten Raspberry Pi und wird überwiegend von KI-Agenten entwickelt.

## Entscheidung

- Ein Repository für API-Vertrag, Backend, App, Deployment und Doku.
- Backend als **modularer Monolith**: ein Prozess, ein Container, aber ein Gradle-Modul pro Feature.
- Feature-Module hängen nur von `core` ab, nie voneinander. Querbeziehungen (z. B. „Aufgabe erledigt → Punkte“)
  laufen über Domain-Events in `core`.

## Alternativen

- **Microservices:** mehr Container, mehr RAM, Netzwerk- und Deployment-Aufwand — auf dem Pi unverhältnismäßig.
- **Ein Modul ohne Grenzen:** einfacher Start, aber Agenten verlieren bei wachsendem Code den Überblick.

## Konsequenzen

- Agenten können an einem Feature-Modul arbeiten, ohne den Rest laden zu müssen (spart Kontext).
- Modulgrenzen werden durch Gradle-Abhängigkeiten erzwungen, nicht nur durch Konvention.
- Ein späteres Herauslösen eines Moduls als eigener Dienst bleibt möglich.
