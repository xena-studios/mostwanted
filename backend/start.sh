#!/usr/bin/env bash
# Starts the backend. Raptor runs this (server/raptor/egg-mostwanted-api.json).
# 256 MB with the Serial GC: the lightest setup for a small heap (docs/04-tech.md).
#
# Settings come from the environment: on Raptor, the egg's settings on the server's
# Startup tab. Elsewhere, a .env next to this (backend/.env.example) works too.
#
# The API uses the game's database: on Raptor, GAME_DB_HOST, GAME_DB_PORT,
# GAME_DB_NAME, GAME_DB_USER and GAME_DB_PASSWORD on its Startup tab, the game
# server's (its Databases tab). They take the place of DB_*.
set -euo pipefail
cd "$(dirname "$0")"
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi
# The port Raptor gave the server, unless API_PORT says otherwise.
export API_PORT=${API_PORT:-${SERVER_PORT:-8080}}
for name in HOST PORT NAME USER PASSWORD; do
  game="GAME_DB_$name"
  if [[ -n ${!game:-} ]]; then
    export "DB_$name=${!game}"
  fi
done
exec java -Xmx256m -XX:+UseSerialGC -jar backend.jar
