#!/usr/bin/env bash
# Erzeugt ein selbstsigniertes Zertifikat (5 Jahre) und den PKCS12-Keystore für das Backend.
# Aufruf: ./gen-cert.sh <dns-name> <ip>      z. B. ./gen-cert.sh planner.home.arpa 192.168.1.50
# Das Keystore-Passwort wird aus FP_TLS_KEYSTORE_PASSWORD in .env gelesen.
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <dns-name> <ip>" >&2
  exit 2
fi
dns_name="$1"
ip="$2"
cd "$(dirname "$0")"

password="$(grep -E '^FP_TLS_KEYSTORE_PASSWORD=' .env | cut -d= -f2-)"
if [ -z "$password" ]; then
  echo "FP_TLS_KEYSTORE_PASSWORD in .env fehlt oder ist leer" >&2
  exit 1
fi

export KEYSTORE_PASSWORD="$password" # nicht per Kommandozeile übergeben (ps)
mkdir -p certs
umask 077
openssl req -x509 -newkey rsa:3072 -sha256 -days 1825 -nodes \
  -subj "/CN=${dns_name}" \
  -addext "subjectAltName=DNS:${dns_name},IP:${ip}" \
  -keyout certs/planner.key -out certs/planner.crt
openssl pkcs12 -export -name planner \
  -inkey certs/planner.key -in certs/planner.crt \
  -out certs/planner.p12 -passout env:KEYSTORE_PASSWORD
chmod 644 certs/planner.p12
echo "Fertig: certs/planner.p12 (für das Backend) und certs/planner.crt (für die App, Phase 1b)"
