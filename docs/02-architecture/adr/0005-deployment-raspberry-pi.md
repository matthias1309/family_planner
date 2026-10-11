# ADR-0005: Deployment auf dem Raspberry Pi (Pull-basiert)

- **Status:** Akzeptiert
- **Datum:** 2026-10-11

## Kontext

Ist-Zustand `dockerhome` (geprüft 2026-10-11):

- Raspberry Pi 5, 4 Kerne, 8 GB RAM (~5 GB verfügbar), NVMe mit 144 GB frei, Debian 13 arm64
- Docker 29, Compose v5; ~15 Container (Home Assistant, Paperless + Postgres/Redis, Gitea, Pi-hole, ntfy, Uptime Kuma, Diun, …)
- Apps liegen unter `/opt/containers/apps/<name>/` mit Bind-Mount `./data`
- **Kernel bootet mit `cgroup_disable=memory`** → Docker-Speicherlimits greifen nicht
- Bestehende App (FinanzFlow) wird direkt auf dem Pi gebaut

Der Pi soll von außen nicht erreichbar sein (siehe ADR-0006) — auch nicht für die CI.

## Entscheidung

**Build in GitHub Actions, Pull durch den Pi.** Es gibt keine eingehende Verbindung zum Pi.

### CI (GitHub Actions)

1. Build, Lint (ktlint/Detekt), Tests, Coverage ≥ 80 % (Kover) — bei Fehler kein Image.
2. Image für `linux/arm64` auf einem arm64-Runner bauen, nach **GHCR** pushen:
   - unveränderlicher Tag pro Build: `ghcr.io/matthias1309/family-planner:<git-sha>`
   - beweglicher Kanal-Tag **`stable`**, wird nur auf `main` nach grüner Pipeline gesetzt
3. Android-APK als Build-Artefakt bzw. GitHub Release.

### Pi

- Verzeichnis `/opt/containers/apps/family-planner/` mit `docker-compose.yml`, `.env`, `data/`, `certs/`.
- Compose referenziert `ghcr.io/matthias1309/family-planner:stable`.
- **systemd-Timer** (z. B. alle 5 Minuten) startet `deploy/update.sh`:
  1. `docker compose pull`; hat sich der Image-Digest nicht geändert → Ende
  2. vorherigen Digest merken, `docker compose up -d`
  3. auf Healthcheck `/health` warten; bei Fehlschlag **Rollback** auf den vorherigen Digest
  4. Ergebnis per **ntfy** melden (Update erfolgreich / Rollback)
- Vor jedem Update konsistentes SQLite-Backup (siehe ADR-0003); Flyway-Migrationen laufen beim Start.
- Laufzeit-Image: schlanke JRE (z. B. Eclipse Temurin JRE), Start mit `-Xmx256m -XX:+UseSerialGC`
  (Heap-Grenze, weil cgroup-Limits nicht wirken).
- Port **8095**, `restart: unless-stopped`, `init: true`, Healthcheck auf `/health`.
- Monitoring über das vorhandene **Uptime Kuma**.

## Alternativen

- **Push-Deploy (CI verbindet sich per SSH/Tailscale zum Pi):** sofortiges Deploy und Status in der CI, aber
  braucht einen Zugang von außen auf den Pi und SSH-Schlüssel in GitHub — verworfen.
- **Watchtower:** übernimmt das Pullen, aktualisiert ohne Einschränkung aber alle Container (Home Assistant, Paperless, …),
  kein Rollback, Projekt nicht mehr aktiv gepflegt — verworfen.
- **Build auf dem Pi (wie FinanzFlow):** kein Registry nötig, aber Gradle-Builds belasten den geteilten Pi spürbar.
- **Gitea Actions Runner auf dem Pi:** möglich, aber Repo liegt auf GitHub; doppelte CI-Infrastruktur.

## Konsequenzen

- Keine eingehenden Verbindungen, keine Pi-Zugangsdaten bei GitHub.
- Der Pi bleibt reine Laufzeitumgebung; Builds kosten dort keine Ressourcen.
- Deploy erfolgt mit bis zu ~5 Minuten Verzögerung nach dem Merge; Erfolg/Rollback kommt per ntfy, nicht in der CI.
- Ältere Versionen bleiben über den SHA-Tag jederzeit wieder einspielbar.
- Das Repo ist öffentlich → das GHCR-Paket kann öffentlich sein, der Pi braucht dann keine Registry-Zugangsdaten.
- Bei Speicherproblemen hilft nur die JVM-Konfiguration — optional `cgroup_enable=memory` in `cmdline.txt` aktivieren (Reboot nötig, eigene Entscheidung).
