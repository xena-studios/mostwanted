#!/usr/bin/env bash
# Prints one version's section of a CHANGELOG.md: the lines under
# "## <version>" up to the next "## ". Fails if there's no such section.
#
#   scripts/changelog.sh xutilities/CHANGELOG.md 2.0.0
set -euo pipefail
file=$1
version=$2
section=$(awk -v heading="## $version" '
  $0 == heading || index($0, heading " ") == 1 { found = 1; next }
  found && /^## / { exit }
  found { print }
' "$file")
[[ -n ${section//[[:space:]]/} ]] || { echo "no \"## $version\" section in $file" >&2; exit 1; }
printf '%s\n' "$section" | sed -e '/./,$!d'
