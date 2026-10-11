# deploy

Docker Compose für den Pi (`/opt/containers/apps/family-planner/`, Port 8095). Siehe ADR-0005, 0006 und 0008.
Jede grüne `main` baut in GitHub Actions ein `linux/arm64`-Image (`ghcr.io/matthias1309/family-planner:stable`);
der Pi zieht es per systemd-Timer alle 5 Minuten, wartet auf `healthy`, rollt sonst zurück und meldet per ntfy.

## Einmalige Einrichtung auf dem Pi

1. **GHCR-Paket öffentlich stellen** (einmalig nach dem ersten Push): GitHub → Packages → `family-planner` → Package settings → Visibility *Public*.
2. Verzeichnis anlegen und Dateien aus `deploy/` hineinkopieren:
   ```bash
   sudo mkdir -p /opt/containers/apps/family-planner/{data,certs}
   sudo cp docker-compose.yml update.sh gen-cert.sh .env.example /opt/containers/apps/family-planner/
   cd /opt/containers/apps/family-planner
   sudo cp .env.example .env && sudo chmod 600 .env
   sudo chown 10001 data          # Container läuft als UID 10001
   ```
3. `.env` ausfüllen: `FP_SETUP_TOKEN` (`openssl rand -hex 16`), `FP_TLS_KEYSTORE_PASSWORD`, `NTFY_URL`, `NTFY_TOPIC`.
4. Zertifikat erzeugen (DNS-Name und IP des Pi): `./gen-cert.sh planner.home.arpa <pi-ip>`.
   `certs/planner.crt` wird in Phase 1b in die App übernommen (Pinning). `certs/` ist nicht im Repo.
5. **Pi-hole:** lokalen DNS-Eintrag `planner.home.arpa` → IP des Pi.
6. Starten: `docker compose up -d`.
7. Timer installieren:
   ```bash
   sudo cp family-planner-update.service family-planner-update.timer /etc/systemd/system/
   sudo systemctl daemon-reload && sudo systemctl enable --now family-planner-update.timer
   ```
8. **Uptime Kuma:** HTTP-Monitor auf `https://planner.home.arpa:8095/health`, „Zertifikatfehler ignorieren“ aktivieren.

Nur Port **8095** wird veröffentlicht; der Health-Port 8096 bleibt im Container.

## Erst-Einrichtung der Familie

Normalerweise in der App („Familie einrichten“, siehe [android/README.md](../android/README.md)). Als Fallback per `curl`:

```bash
curl -k -X POST https://planner.home.arpa:8095/api/v1/setup -H 'Content-Type: application/json' \
  -d '{"setupToken":"<FP_SETUP_TOKEN>","parentName":"Mama","deviceName":"Handy Mama"}'
```

Die Antwort enthält das Geräte-Token (nur einmal sichtbar); weitere Aufrufe mit `Authorization: Bearer <token>`.

## Betrieb

- Update/Rollback-Log: `journalctl -u family-planner-update.service`.
- Ein Image, dessen Healthcheck scheitert, wird nach dem Rollback in `.failed-image` vermerkt und nicht erneut versucht.
- Backups: Das Backend schreibt vor jeder ausstehenden Migration eine Kopie nach `data/backups/` (letzte 10).
