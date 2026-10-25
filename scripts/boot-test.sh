#!/usr/bin/env bash
# Boots a real server with the given plugin jars, runs console commands, stops
# it, and fails if a plugin logged an error or warning, a command's expected
# output never appeared, or the server didn't start and stop cleanly.
#
#   scripts/boot-test.sh [--project paper|folia] [--version 1.21.11] \
#     [--checks file]... [--run "console command"]... [--expect "log text"]... plugin.jar...
#
# Steps run in order. Each --expect waits for text logged after the --run before
# it. A checks file holds the same steps, one per line: "run: <command>",
# "expect: <text>", or "logged: <text>" for text logged at any point, such as
# during startup; blank lines and lines starting with # are skipped. A step
# that starts with "paper " or "folia " runs only on that server.
#
# Server jars, Mojang's jar and libraries are cached in .cache/servers/.
set -euo pipefail

project=paper
version=1.21.11
steps=()
jars=()
while (($#)); do
  case "$1" in
    --project) project=$2; shift 2 ;;
    --version) version=$2; shift 2 ;;
    --run) steps+=("run: $2"); shift 2 ;;
    --expect) steps+=("expect: $2"); shift 2 ;;
    --checks)
      while IFS= read -r line || [[ -n $line ]]; do
        [[ -z ${line//[[:space:]]/} || $line == \#* ]] || steps+=("$line")
      done <"$2"
      shift 2 ;;
    -*) echo "unknown option $1" >&2; exit 2 ;;
    *) jars+=("$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"); shift ;;
  esac
done
((${#jars[@]})) || { echo "no plugin jars given" >&2; exit 2; }

root=$(cd "$(dirname "$0")/.." && pwd)
cache="$root/.cache/servers"
dir="$root/build/boot/$project-$version"
log="$dir/console.log"

# ---- the server jar, verified against Paper's checksum ----
mkdir -p "$cache"
build=$(curl -fsS "https://fill.papermc.io/v3/projects/$project/versions/$version/builds/latest")
url=$(grep -o '"url":"[^"]*"' <<<"$build" | head -1 | cut -d'"' -f4)
sha=$(grep -o '"sha256":"[^"]*"' <<<"$build" | head -1 | cut -d'"' -f4)
server_jar="$cache/${url##*/}"
if [[ ! -f $server_jar ]]; then
  echo "Downloading ${url##*/}"
  curl -fsSL -o "$server_jar.part" "$url"
  mv "$server_jar.part" "$server_jar"
fi
echo "$sha  $server_jar" | shasum -a 256 -c - >/dev/null

# ---- a fresh server, sharing the slow-to-fetch files between runs ----
rm -rf "$dir"
mkdir -p "$dir/plugins"
for shared in cache libraries versions; do
  mkdir -p "$cache/$project-$version/$shared"
  ln -s "$cache/$project-$version/$shared" "$dir/$shared"
done
# Our plugins' names, to spot their warnings in the log. Folia gets only the
# plugins that declare they run on it.
names=()
for jar in "${jars[@]}"; do
  # unzip complains about whichever descriptor the jar doesn't have.
  descriptor=$({ unzip -p "$jar" paper-plugin.yml plugin.yml 2>/dev/null || true; })
  [[ -n $descriptor ]] || { echo "$(basename "$jar") isn't a plugin: it has no paper-plugin.yml or plugin.yml" >&2; exit 2; }
  if [[ $project == folia ]] && ! grep -q '^folia-supported: true' <<<"$descriptor"; then
    echo "Skipping $(basename "$jar"): not for Folia"
    continue
  fi
  names+=("$(sed -n 's/^name: *//p' <<<"$descriptor" | head -1)")
  cp "$jar" "$dir/plugins/"
done
((${#names[@]})) || { echo "no plugins to test on $project"; exit 0; }
ours=$(IFS='|'; echo "${names[*]}")
echo "eula=true" >"$dir/eula.txt"
cat >"$dir/server.properties" <<EOF
online-mode=false
server-port=$((30000 + RANDOM % 20000))
level-type=minecraft\:flat
generator-settings={"layers"\:[{"block"\:"minecraft\:stone","height"\:1}],"biome"\:"minecraft\:plains"}
generate-structures=false
view-distance=2
simulation-distance=2
spawn-protection=0
EOF


# ---- run it ----
mkfifo "$dir/stdin"
(cd "$dir" && exec java -Xms512M -Xmx1G -jar "$server_jar" --nogui <stdin >"$log" 2>&1) &
pid=$!
exec 3>"$dir/stdin"

fail() {
  echo "FAIL: $*" >&2
  echo "---- last lines of $log ----" >&2
  tail -60 "$log" >&2 || true
  kill "$pid" 2>/dev/null || true
  exit 1
}

# Waits up to $2 seconds for the fixed string $1 to appear in the log after line $3.
wait_for() {
  for ((i = 0; i < $2 * 2; i++)); do
    tail -n "+$((${3:-0} + 1))" "$log" 2>/dev/null | grep -qF -- "$1" && return 0
    kill -0 "$pid" 2>/dev/null || fail "server exited while waiting for \"$1\""
    sleep 0.5
  done
  fail "\"$1\" didn't appear within $2s"
}

wait_for "Done (" 300
echo "Started $project $version with ${names[*]}"
since=0
for step in "${steps[@]+"${steps[@]}"}"; do
  case "$step" in
    paper\ * | folia\ *)
      [[ ${step%% *} == "$project" ]] || continue
      step=${step#* } ;;
  esac
  case "$step" in
    "run: "*)
      since=$(wc -l <"$log")
      echo "${step#run: }" >&3 ;;
    "expect: "*) wait_for "${step#expect: }" 60 "$since" ;;
    "logged: "*) wait_for "${step#logged: }" 60 0 ;;
    *) fail "a step must start with \"run: \", \"expect: \" or \"logged: \": $step" ;;
  esac
done

echo "stop" >&3
for ((i = 0; i < 120; i++)); do
  kill -0 "$pid" 2>/dev/null || break
  sleep 0.5
done
kill -0 "$pid" 2>/dev/null && fail "server didn't stop within 60s"
wait "$pid" || fail "server exited with status $?"
exec 3>&-

# ---- judge the log ----
if errors=$(grep -E '^\[[0-9:]+ ERROR\]' "$log"); then
  fail "the log has errors:"$'\n'"$errors"
fi
if warnings=$(grep -E "^\[[0-9:]+ WARN\]: \[($ours)\]" "$log"); then
  fail "our plugins logged warnings:"$'\n'"$warnings"
fi
grep -E "^\[[0-9:]+ INFO\]: \[($ours)\]" "$log" || true
echo "PASS: $project $version"
