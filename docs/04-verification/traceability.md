# Traceability-Matrix

| REQ-ID | Anforderung | Design | Unit | Integration | E2E | Status |
|---|---|---|---|---|---|---|
| REQ-AUTH-001 | Mitglieder & Rollen | [core](../03-design/core.md) | `MemberServiceTest` (`REQ-AUTH-001 …`); App: `UseCasesTest`, `ViewModelsTest` (`REQ-AUTH-001 …`) | `MemberDeviceApiTest` (`REQ-AUTH-001 …`) | manuell: Abnahme Phase 1b, 2026-10-17 auf Elternhandy + Testhandy ([android/README.md](../../android/README.md)) | abgenommen (Backend + App) |
| REQ-AUTH-002 | Geräte-Anmeldung | [core](../03-design/core.md), [ADR-0008](../02-architecture/adr/0008-device-pairing.md) | `SetupServiceTest`, `PairingServiceTest`, `DeviceServiceTest` (`REQ-AUTH-002 …`); App: `UseCasesTest`, `ViewModelsTest`, `ScreensTest`, `ApiCallTest` (`REQ-AUTH-002 …`) | `AuthApiTest`, `MemberDeviceApiTest` (`REQ-AUTH-002 …`) | manuell: Abnahme Phase 1b, 2026-10-17 auf Elternhandy + Testhandy ([android/README.md](../../android/README.md)) | abgenommen (Backend + App) |
| REQ-AUTH-003 | Gerät entfernen | [core](../03-design/core.md) | `DeviceServiceTest` (`REQ-AUTH-003 …`); App: `UseCasesTest`, `ViewModelsTest`, `ScreensTest` (`REQ-AUTH-003 …`) | `MemberDeviceApiTest` (`REQ-AUTH-003 …`) | manuell: Abnahme Phase 1b, 2026-10-17 auf Elternhandy + Testhandy ([android/README.md](../../android/README.md)) | abgenommen (Backend + App) |
| REQ-OPS-001 | Betrieb (Health, Backup, Deploy) | [core](../03-design/core.md), [ADR-0005](../02-architecture/adr/0005-deployment-raspberry-pi.md) | `DatabaseFactoryTest` (Backup) | `AuthApiTest` (`REQ-OPS-001 health reports ok`) | | umgesetzt (Deploy-Probe auf dem Pi offen) |
| REQ-MEAL-001 | Wochenplan ansehen | | | | | offen |
| REQ-MEAL-002 | Plan ohne Heimnetz lesen | | | | | offen |
| REQ-MEAL-010 | Anwesenheitsmuster pflegen | [meals](../03-design/meals.md) | `AttendanceCalculatorTest`, `AttendanceServiceTest`, `ExposedAttendanceRepositoryTest` (`REQ-MEAL-010 …`) | `MealsAttendanceApiTest` (`REQ-MEAL-010 …`) | | Backend umgesetzt (App folgt in 2b) |
| REQ-MEAL-011 | Anwesenheit frei eintragen/überschreiben | [meals](../03-design/meals.md) | `AttendanceCalculatorTest`, `AttendanceServiceTest`, `ExposedAttendanceRepositoryTest` (`REQ-MEAL-011 …`) | `MealsAttendanceApiTest` (`REQ-MEAL-011 …`) | | Backend umgesetzt (App folgt in 2b) |
| REQ-MEAL-012 | Kind-Einreichfrist berechnen | [meals](../03-design/meals.md), [ADR-0007](../02-architecture/adr/0007-meal-deadlines-without-remote-access.md) | `DeadlinesTest`, `AttendanceServiceTest` (`REQ-MEAL-012 …`) | `MealsAttendanceApiTest` (`REQ-MEAL-012 …`) | | Backend umgesetzt (App folgt in 2b) |
| REQ-MEAL-013 | Bestellfrist konfigurieren | [meals](../03-design/meals.md) | `DeadlinesTest`, `DeadlineSettingsServiceTest`, `ExposedAttendanceRepositoryTest` (`REQ-MEAL-013 …`) | `MealsAttendanceApiTest` (`REQ-MEAL-013 …`) | | Backend umgesetzt (App folgt in 2b) |
| REQ-MEAL-020 | Kochgericht einreichen | | | | | offen |
| REQ-MEAL-021 | Nur Anwesenheitstage wählbar | | | | | offen |
| REQ-MEAL-022 | Eingereichtes Gericht ändern | | | | | offen |
| REQ-MEAL-023 | Kochen absagen | | | | | offen |
| REQ-MEAL-030 | Freigeben / ablehnen | | | | | offen |
| REQ-MEAL-031 | Stellvertretend eintragen | | | | | offen |
| REQ-MEAL-032 | Elterngerichte planen | | | | | offen |
| REQ-MEAL-033 | Wochenstatus | | | | | offen |
| REQ-MEAL-040 | Einkaufsliste erzeugen | | | | | offen |
| REQ-MEAL-041 | Einkaufsliste bearbeiten | | | | | offen |
| REQ-MEAL-050 | Erinnerung vor Frist | | | | | offen |
| REQ-MEAL-051 | WhatsApp-Hinweis außer Haus | | | | | offen |
| REQ-MEAL-052 | Statusänderungen melden | | | | | offen |
