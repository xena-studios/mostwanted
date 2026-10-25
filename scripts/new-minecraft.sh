#!/usr/bin/env bash
# Finds the newest Minecraft version Paper has released as stable, newer than the
# one in gradle/libs.versions.toml, and moves the repo onto it: the paper-api
# version we build against, and the boot test and newest-API compile in
# .github/workflows/build.yml.
# Prints the new version, or nothing if there isn't one. Needs curl and jq.
set -euo pipefail
cd "$(dirname "$0")/.."

catalog=gradle/libs.versions.toml
ci=.github/workflows/build.yml
fill=https://fill.papermc.io/v3/projects/paper

current=$(sed -nE 's/^paper = "([^"]+)\.build\..*/\1/p' "$catalog")

# Releases only (no -pre, -rc), newer than what we run, oldest first.
candidates=$({ echo "$current"; curl -fsS "$fill" | jq -r '.versions[][]' | grep -E '^[0-9]+(\.[0-9]+)+$'; } |
  sort -uV | sed "1,/^${current//./\\.}\$/d")

# A version counts once Paper has a stable build of it. Take the newest.
top=
for v in $candidates; do
  channel=$(curl -fsS "$fill/versions/$v/builds/latest" | jq -r '.channel')
  [[ $channel == STABLE ]] && top=$v
done
[[ -n $top ]] || exit 0

# The newest stable paper-api build for that version.
api=$(curl -fsS https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml |
  grep -oE "<version>${top//./\\.}\.build\.[0-9]+-stable</version>" | sed -E 's/<\/?version>//g' | sort -V | tail -1)
[[ -n $api ]] || { echo "no stable paper-api build of $top yet" >&2; exit 0; }

sed -i.bak -E "s/^paper = \"[^\"]+\"/paper = \"$api\"/" "$catalog"
sed -i.bak -E "s/-Pxena\.newestPaper=[^ ]+/-Pxena.newestPaper=$api/" "$ci"
sed -i.bak -E "s/(- \{ version: \")${current//./\\.}(\", java: [0-9]+ \})/\1$top\2/" "$ci"
rm -f "$catalog.bak" "$ci.bak"

echo "$top"
