# Moduldesign: `meals` – Anwesenheit & Fristen (Phase 2a)

## Abgedeckte Anforderungen

REQ-MEAL-010, REQ-MEAL-011, REQ-MEAL-012, REQ-MEAL-013 — siehe [meals.prd.md](../01-requirements/meals.prd.md).
Konventionen für Feature-Module: [ADR-0010](../02-architecture/adr/0010-backend-feature-modules.md).
Fristen ohne Fernzugriff: [ADR-0007](../02-architecture/adr/0007-meal-deadlines-without-remote-access.md).
Die Eltern-UI in der App folgt als Phase 2b.

## Domänenmodell & Regeln

| Begriff | Definition |
|---|---|
| `ZONE` | `Europe/Berlin` — alle Kalender-/Uhrzeitberechnungen |
| Planwoche (`PlanWeek`) | Montag einer Woche Mo–So; kein Montag → `Validation` |
| Laufende Woche zu Planwoche P | `P − 7 Tage` … `P − 1 Tag` (Fristen liegen hier) |
| Aktuelle Planwoche (zum Zeitpunkt *now*) | `monday(heute in ZONE) + 7 Tage` |
| `AttendancePattern.Always` | an allen Tagen anwesend |
| `AttendancePattern.Alternating` | Wochentyp A/B mit je einer Tagesmenge und einer Referenzwoche (Montag einer A-Woche). Typ von Datum d: `floorMod(WEEKS.between(referenceWeek, monday(d)), 2) == 0` → A, sonst B (gilt auch vor der Referenzwoche). Mindestens ein Tag in A ∪ B. |
| `AttendanceProfile` | `memberId`, `cooks`, `pattern`; nur für Rolle CHILD. Ohne gespeichertes Profil: `cooks=false`, `Always`. |
| `AttendanceException` | Zeitraum `from ≤ to` (inklusive, max. 366 Tage), `PRESENT`/`ABSENT`, Notiz 0–60 Zeichen. Überlappung mit bestehender Ausnahme desselben Kindes → 409. |
| Anwesenheit an Tag d | Ausnahme, die d enthält (`source=EXCEPTION`), sonst Muster (`source=PATTERN`) |
| `DeadlineSettings` | Bestellfrist-Wochentag (Default So), Bestellfrist-Uhrzeit (16:00), Kind-Uhrzeit (16:00), `effectiveFrom` |
| Gültige Einstellungen für P | Eintrag mit größtem `effectiveFrom ≤ P`, sonst Defaults |
| Ändern der Einstellungen (REQ-MEAL-013) | `effectiveFrom = aktuelle Planwoche + 7 Tage`; die gerade geplante Woche behält ihre Frist. Erneutes Ändern überschreibt. |
| Bestellfrist für P | Wochentag der Einstellung in der laufenden Woche, zur eingestellten Uhrzeit, in `ZONE` |
| Kind-Frist für P (REQ-MEAL-012) | nur wenn `cooks`; in P an keinem Tag anwesend → keine Frist; sonst `min(letzter Anwesenheitstag der laufenden Woche zur Kind-Uhrzeit, Bestellfrist)`; in der laufenden Woche nie anwesend → Bestellfrist |

Das Anwesenheitsmodell verallgemeinert den PRD-Wortlaut „Ankunft Di, Abreise Do/So": Wechselmodell mit Woche A = Di–Do und
Woche B = Di–So. Beispiel: Laufende Woche Typ A → letzter Tag Do → Frist **Do 16:00**; Typ B oder `Always` → So → **Bestellfrist**.

„Ab der nächsten Planwoche" (REQ-MEAL-013) heißt: die gerade geplante Woche (Montag nach heute) behält ihre Frist,
neue Werte gelten ab der Woche danach.

## Schnittstellen

Endpunkte siehe `api/openapi.yaml` (Tag `meals`), Lesen für alle Mitglieder, Schreiben nur Eltern:

- `GET /api/v1/meals/profiles`, `PUT /api/v1/meals/profiles/{memberId}`
- `GET|POST /api/v1/meals/profiles/{memberId}/exceptions`, `DELETE /api/v1/meals/exceptions/{exceptionId}`
- `GET|PUT /api/v1/meals/settings`
- `GET /api/v1/meals/weeks/{weekStart}/attendance` (Schnittstelle für Phase 3/4 und die Erinnerungen in Phase 6)

## DB-Tabellen

Migration `V2__meals_attendance.sql`: `meal_attendance_profiles` (Tagesmasken: Bit `dayOfWeek − 1`, Mo = Bit 0),
`meal_attendance_exceptions`, `meal_deadline_settings`. Daten als ISO-Text `yyyy-MM-dd`, Zeitstempel als Epoch-Millis.

## Unit-Testfälle

Siehe `backend/meals/src/test` (Namen beginnen mit der REQ-ID): Always/Alternating inkl. Parität vor der Referenzwoche und
über den Jahreswechsel, Ausnahmen (Überschreiben, Randtage, Überlappung, Länge, Notiz), Fristen (Do/So, Maximum Bestellfrist,
ganz abwesend, nicht kochend, Sommer-/Winterzeit 25.10. und 29.03.), Einstellungen (Defaults, Wirksamkeit ab nächster
Planwoche, Überschreiben, Berliner Zeit), Persistenz gegen echte SQLite.
