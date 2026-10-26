#!/usr/bin/env bash
# Starts the MostWanted game server. install.sh copies this next to server.jar.
#
#   MEMORY=8G ./start.sh
#
# MEMORY is the Java heap (default 8G: Java needs another ~1.5 GB outside it, and
# PostgreSQL, Raptor and the bot share the 12 GB VPS; see docs/04-tech.md).
# The flags are Paper's recommended G1 set, plus Minehut's: players join through
# Minehut's network, so the server checks logins with Minehut's session server
# (https://support.minehut.com/hc/en-us/articles/33252734474899).
set -euo pipefail
cd "$(dirname "$0")"

memory=${MEMORY:-8G}

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
