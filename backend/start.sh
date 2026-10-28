#!/usr/bin/env bash
# Starts the backend. Raptor runs this (server/raptor/egg-mostwanted-api.json).
# 256 MB with the Serial GC: the lightest setup for a small heap (docs/04-tech.md).
#
# Settings come from the environment: on Raptor, the egg's settings on the server's
# Startup tab. Elsewhere, a .env next to this (backend/.env.example) works too.
#
# The API uses the game's database. On Raptor the API server has an empty database
# of its own, only so Raptor's database firewall lets it in, and Raptor gives the
# API that one's DB_* : GAME_DB_NAME, GAME_DB_USER and GAME_DB_PASSWORD (the game
# server's, from its Databases tab) take their place. DB_HOST and DB_PORT are the
# same instance's either way.
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
for name in NAME USER PASSWORD; do
  game="GAME_DB_$name"
  if [[ -n ${!game:-} ]]; then
    export "DB_$name=${!game}"
  fi
done
exec java -Xmx256m -XX:+UseSerialGC -jar backend.jar
