#!/usr/bin/env bash
# Starts the backend. The panel runs this (server/eggs/egg-mostwanted-api.json).
# 256 MB with the Serial GC: the lightest setup for a small heap (docs/04-tech.md).
#
# Settings come from the environment: the egg's settings on the server's Startup
# tab, the game's database (DB_*) included. Elsewhere, a .env next to this
# (backend/.env.example) works too.
set -euo pipefail
cd "$(dirname "$0")"
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi
for name in DB_HOST DB_NAME DB_USER DB_PASSWORD; do
  if [[ -z ${!name:-} ]]; then
    echo "needs $name: set it on the server's Startup tab (or in .env)" >&2
    exit 1
  fi
done
# The port the panel gave the server, unless API_PORT says otherwise.
export API_PORT=${API_PORT:-${SERVER_PORT:-8080}}
exec java -Xmx256m -XX:+UseSerialGC -jar backend.jar
