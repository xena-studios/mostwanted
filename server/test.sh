#!/usr/bin/env bash
# Boots a server set up by install.sh, checks that every plugin enabled and the
# configs loaded, then stops it. CI runs this on every change to server/.
#
#   server/test.sh <server-dir>
#
# Fails if a plugin didn't load or enable, a config couldn't be read, or the
# server didn't start and stop cleanly. Accepts the EULA in <server-dir>: only
# point it at a test copy.
set -euo pipefail

dir=$(cd "${1:?usage: server/test.sh <server-dir>}" && pwd)
here=$(cd "$(dirname "$0")" && pwd)
log="$dir/test-console.log"

echo "eula=true" >"$dir/eula.txt"
rm -f "$log"
mkfifo "$dir/.stdin" 2>/dev/null || true
(cd "$dir" && MEMORY=${MEMORY:-2G} exec ./start.sh <.stdin >"$log" 2>&1) &
pid=$!
exec 3>"$dir/.stdin"

fail() {
  echo "FAIL: $*" >&2
  echo "---- last lines of $log ----" >&2
  tail -60 "$log" >&2 || true
  kill "$pid" 2>/dev/null || true
  exit 1
}

for ((i = 0; i < 600; i++)); do
  grep -q "Done (" "$log" 2>/dev/null && break
  kill -0 "$pid" 2>/dev/null || fail "server exited before it finished starting"
  sleep 0.5
done
grep -q "Done (" "$log" || fail "server didn't start within 5 minutes"

echo "plugins" >&3
for ((i = 0; i < 40; i++)); do grep -q "Server Plugins" "$log" && break; sleep 0.5; done
echo "stop" >&3
for ((i = 0; i < 120; i++)); do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
kill -0 "$pid" 2>/dev/null && fail "server didn't stop within 60s"
wait "$pid" || fail "server exited with status $?"
exec 3>&-
rm -f "$dir/.stdin"

# Every plugin in the manifest that install.sh put in plugins/ must have enabled.
problems=()
while read -r name; do
  grep -qE "\[$name\] Enabling $name|Enabling $name v" "$log" || problems+=("$name never enabled")
done < <(jq -r --slurpfile managed <(jq -R . "$dir/plugins/.managed") \
  '.plugins[] | select(.file as $f | $managed | index($f)) | .name' "$here/manifest.json")

# A plugin that turns itself off during startup (unsupported version, missing
# dependency) logs "Disabling" before the server is told to stop.
startup=$(awk '/Stopping server|Stopping the server/ { exit } { print }' "$log")
while read -r name; do
  [[ -n $name ]] && problems+=("$name disabled itself during startup")
done < <(grep -oE "Disabling [A-Za-z0-9_-]+ v" <<<"$startup" | awk '{print $2}' | sort -u)

if errors=$(grep -E "Error occurred while enabling|Could not load plugin|Could not load '|Failed to load|Error loading|Exception in .*config|highly destabilizing" "$log"); then
  problems+=("$errors")
fi

if ((${#problems[@]})); then
  printf '%s\n' "${problems[@]}" >&2
  fail "plugins or configs didn't load"
fi
grep -E "^\[[0-9:]+ INFO\]: Server Plugins" -A20 "$log" | grep -vE "^\[[0-9:]+ INFO\]: *$" | head -20 || true
echo "PASS: every plugin enabled"
