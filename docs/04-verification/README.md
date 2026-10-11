# Verifikation

| Ebene | Prüft | Werkzeug | Ort |
|---|---|---|---|
| Unit | Moduldesign (`03-design`) | JUnit 5 / Kotest, MockK | `*/src/test` |
| Integration | Architektur (`02-architecture`), API-Vertrag | Ktor `testApplication`, SQLite temp | `backend/*/src/test` |
| E2E / Abnahme | Anforderungen (`01-requirements`) | Compose UI Tests gegen laufendes Backend | `android/*/src/androidTest` |

- Mindestabdeckung 80 % (Kover), in der CI erzwungen.
- Testnamen beginnen mit der REQ-ID, wenn sie eine Anforderung abdecken.
- [traceability.md](traceability.md) ordnet REQ-IDs den Tests zu.
