#!/usr/bin/env bash
# Starts the backend with the settings in .env next to it. Raptor runs this.
# 256 MB with the Serial GC: the lightest setup for a small heap (docs/04-tech.md).
set -euo pipefail
cd "$(dirname "$0")"
if [[ ! -f .env ]]; then
  echo "No .env here: copy backend/.env.example and fill it in." >&2
  exit 1
fi
set -a
# shellcheck disable=SC1091
. ./.env
set +a
exec java -Xmx256m -XX:+UseSerialGC -jar backend.jar
