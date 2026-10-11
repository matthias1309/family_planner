#!/usr/bin/env bash
# Zieht das aktuelle `stable`-Image, startet neu und rollt bei ungesundem Container zurück.
# Läuft per systemd-Timer im Compose-Verzeichnis (siehe family-planner-update.service).
set -euo pipefail

cd "$(dirname "$0")"
IMAGE="ghcr.io/matthias1309/family-planner:stable"
CONTAINER="family-planner"
HEALTH_TIMEOUT_SECONDS=120
FAILED_MARKER=".failed-image"

# Nur die ntfy-Variablen lesen; .env wird bewusst nicht per `source` ausgeführt (Passwörter mit $, Leerzeichen …).
env_value() { grep -E "^$1=" .env 2>/dev/null | head -n1 | cut -d= -f2- || true; }
NTFY_URL="$(env_value NTFY_URL)"
NTFY_TOPIC="$(env_value NTFY_TOPIC)"

notify() { # notify <priority> <message>
  [ -n "${NTFY_URL:-}" ] && [ -n "${NTFY_TOPIC:-}" ] || return 0
  curl -fsS -H "Title: Family Planner" -H "Priority: $1" -d "$2" "$NTFY_URL/$NTFY_TOPIC" >/dev/null || true
}

image_id() { docker image inspect --format '{{.Id}}' "$IMAGE" 2>/dev/null || true; }

prev="$(image_id)"
docker compose pull -q
new="$(image_id)"

if [ "$prev" = "$new" ]; then
  exit 0
fi
if [ -f "$FAILED_MARKER" ] && [ "$new" = "$(cat "$FAILED_MARKER")" ]; then
  exit 0 # dieses Image war bereits kaputt – kein Endlos-Rollback
fi

docker compose up -d

status="starting"
for _ in $(seq 1 $((HEALTH_TIMEOUT_SECONDS / 2))); do
  status="$(docker inspect --format '{{.State.Health.Status}}' "$CONTAINER" 2>/dev/null || echo missing)"
  [ "$status" = "healthy" ] && break
  sleep 2
done

if [ "$status" = "healthy" ]; then
  rm -f "$FAILED_MARKER"
  version="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$CONTAINER" | sed -n 's/^FP_VERSION=//p' | cut -c1-7)"
  notify default "Family Planner aktualisiert (${version:-unbekannt})"
  exit 0
fi

echo "$new" > "$FAILED_MARKER"
if [ -n "$prev" ]; then
  docker tag "$prev" "$IMAGE"
  docker compose up -d
fi
notify high "Rollback – neues Image unhealthy"
exit 1
