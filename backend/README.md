# backend

Kotlin/Ktor, modularer Monolith, SQLite (WAL) via Exposed, Migrationen mit Flyway. Siehe ADR-0001 bis 0003 und 0008.

## Module

| Modul | Inhalt |
|---|---|
| `api-contract` | Modelle, generiert aus `../api/openapi.yaml` (OpenAPI Generator, kotlinx.serialization) |
| `core` | AUTH: Mitglieder, Geräte, Kopplungscodes, Persistenz, Bearer-Authentifizierung, Routen |
| `meals` | Anwesenheit & Fristen (Muster, Ausnahmen, Bestell-/Einreichfristen); weitere Essensplaner-Phasen folgen. Design: `docs/03-design/meals.md` |
| `app` | Ktor-Server (Netty, TLS), Konfiguration aus Umgebungsvariablen, Plugins, `/health` |

Weitere Feature-Module kommen mit den jeweiligen Phasen. Konventionen: ADR-0010; Muster zum Spiegeln: `docs/03-design/core.md`, `docs/03-design/meals.md`.

## Befehle

Voraussetzung: JDK 21.

```bash
cd backend
./gradlew ktlintCheck detekt          # statische Analyse
./gradlew :core:test                  # Unit-Tests
./gradlew check koverVerify           # alles inkl. Integrationstests, Coverage >= 80 %
./gradlew :app:buildFatJar            # app/build/libs/app-all.jar
docker build -t family-planner:local .
```

Lokal starten (HTTP, ohne TLS):

```bash
FP_SETUP_TOKEN=local-setup-token-123 ./gradlew :app:run   # http://localhost:8095/health
```

## Konfiguration (Umgebungsvariablen)

| Variable | Default | Bedeutung |
|---|---|---|
| `FP_PORT` | 8095 | öffentlicher Port (HTTPS, wenn TLS an) |
| `FP_HEALTH_PORT` | 8096 | interner HTTP-Port nur auf 127.0.0.1 (Docker-Healthcheck) |
| `FP_DB_PATH` | `./data/family-planner.db` | SQLite-Datei |
| `FP_SETUP_TOKEN` | – | optional, ≥ 16 Zeichen; ohne Token ist die Erst-Einrichtung deaktiviert |
| `FP_TLS_ENABLED` | `false` | im Compose `true` |
| `FP_TLS_KEYSTORE` | `/app/certs/planner.p12` | PKCS12-Keystore (Alias `planner`) |
| `FP_TLS_KEYSTORE_PASSWORD` | – | Pflicht, wenn TLS an |
| `FP_VERSION` | `dev` | Git-SHA, vom Image gesetzt |
