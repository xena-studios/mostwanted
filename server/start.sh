#!/usr/bin/env bash
# Starts the MostWanted game server. install.sh copies this next to server.jar;
# Raptor runs it (the MostWanted egg, server/raptor/).
#
#   MEMORY=10G ./start.sh
#
# MEMORY is the Java heap. Unset, it's Raptor's memory setting for the server
# (SERVER_MEMORY) less 1.5 GB, which Java needs outside the heap: 11.5 GB in Raptor
# is a 10 GB heap (docs/04-tech.md). Without either, 10G.
#
# First, every config template (*.tmpl) is filled in from the environment: Raptor
# gives the database's DB_* from the server's Databases tab, and the egg's settings
# (TEBEX_SECRET, ...); a .env file here (server/.env.example) does it elsewhere.
# A value that's missing stops the start: a half-filled config is worse than none.
# The flags are Paper's recommended G1 set, plus Minehut's: players join through
# Minehut's network, so the server checks logins with Minehut's session server
# (https://support.minehut.com/hc/en-us/articles/33252734474899).
set -euo pipefail
cd "$(dirname "$0")"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

while IFS= read -r -d '' template; do
  text=$(<"$template")
  # shellcheck disable=SC2016 # the ${...} is the literal text being searched for
  for name in $(grep -o '\${[A-Z0-9_]*}' "$template" | tr -d '${}' | sort -u); do
    if [[ -z ${!name:-} ]]; then
      echo "${template#./} needs $name: on Raptor, the server's Databases tab gives DB_*, and its Startup tab the rest" >&2
      exit 1
    fi
    text=${text//"\${$name}"/"${!name}"}
  done
  printf '%s\n' "$text" >"${template%.tmpl}"
done < <(find . -name '*.tmpl' -not -path './.cache/*' -print0)

if [[ -n ${MEMORY:-} ]]; then
  memory=$MEMORY
elif [[ -n ${SERVER_MEMORY:-} ]]; then
  memory=$((SERVER_MEMORY > 2560 ? SERVER_MEMORY - 1536 : 1024))M
else
  memory=10G
fi

exec java \
  -Xms"$memory" -Xmx"$memory" \
  -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled -XX:+PerfDisableSharedMem \
  -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=8M -XX:G1HeapWastePercent=5 \
  -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1NewSizePercent=30 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:G1ReservePercent=20 \
  -XX:InitiatingHeapOccupancyPercent=15 -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 \
  -XX:SurvivorRatio=32 \
  -Dminecraft.api.services.host=https://api.minecraftservices.com/ \
  -Dminecraft.api.auth.host=https://authserver.mojang.com/ \
  -Dminecraft.api.account.host=https://api.mojang.com/ \
  -Dminecraft.api.profiles.host=https://api.mojang.com/ \
  -Dminecraft.api.session.host=https://api.minehut.com/mitm/proxy \
  -jar server.jar --nogui
