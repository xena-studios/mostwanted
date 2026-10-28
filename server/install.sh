#!/usr/bin/env bash
# Sets up (or updates) a MostWanted game server in a directory: downloads Leaf
# and every plugin pinned in server/manifest.json, checking each file's checksum,
# then copies in the configs from server/files/ and the server icon.
#
#   server/install.sh <server-dir> [--with plan,vaultunlocked,...]
#
# Safe to run again: it replaces plugin jars it installed before, removes ones
# no longer in the manifest, and overwrites the config files this repo tracks.
# Anything else in <server-dir> (the world, plugin data, other configs) is left alone.
#
# Secrets never live in this repo. Files ending in .tmpl are copied as they are:
# start.sh fills them in at every start, from the server's environment (Raptor's
# database and the egg's settings) or <server-dir>/.env (see server/.env.example).
# Raptor runs this from the MostWanted egg's install (server/raptor/egg-mostwanted.json).
#
# Needs bash, curl, jq and shasum. Downloads are cached in <server-dir>/.cache/.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/.." && pwd)
manifest="$here/manifest.json"

dir=${1:-}
[[ -n $dir ]] || { echo "usage: server/install.sh <server-dir> [--with plugin,plugin]" >&2; exit 2; }
shift
with=""
while (($#)); do
  case "$1" in
    --with) with=$2; shift 2 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done

for tool in curl jq shasum; do
  command -v "$tool" >/dev/null || { echo "install.sh needs $tool" >&2; exit 2; }
done

mkdir -p "$dir/plugins" "$dir/.cache"
dir=$(cd "$dir" && pwd)
cache="$dir/.cache"

# Downloads $url to the cache once, checks it against $algo:$hash, and copies it to $dest.
fetch() {
  local url=$1 algo=$2 hash=$3 dest=$4
  local cached="$cache/$hash"
  if [[ ! -f $cached ]]; then
    echo "  downloading $(basename "$dest")"
    curl -fsSL --retry 3 -o "$cached.part" "$url"
    mv "$cached.part" "$cached"
  fi
  echo "$hash  $cached" | shasum -a "$algo" -c - >/dev/null \
    || { rm -f "$cached"; echo "checksum mismatch for $(basename "$dest"): refusing to install it" >&2; exit 1; }
  cp "$cached" "$dest"
}

echo "Server"
server_file=$(jq -r '.server.file' "$manifest")
fetch "$(jq -r '.server.url' "$manifest")" 256 "$(jq -r '.server.sha256' "$manifest")" "$dir/$server_file"
ln -sf "$server_file" "$dir/server.jar"

echo "Plugins"
managed="$dir/plugins/.managed"
[[ -f $managed ]] && while read -r old; do rm -f "$dir/plugins/$old"; done <"$managed"
: >"$managed"
while read -r plugin; do
  slug=$(jq -r '.slug' <<<"$plugin")
  if [[ $(jq -r '.optional // false' <<<"$plugin") == true && ",$with," != *",$slug,"* ]]; then
    continue
  fi
  file=$(jq -r '.file' <<<"$plugin")
  fetch "$(jq -r '.url' <<<"$plugin")" 512 "$(jq -r '.sha512' <<<"$plugin")" "$dir/plugins/$file"
  echo "$file" >>"$managed"
done < <(jq -c '.plugins[]' "$manifest")

echo "Configs"
# Templates (.tmpl) go in as they are: start.sh fills them in at every start.
(cd "$here/files" && find . -type f) | while read -r rel; do
  rel=${rel#./}
  mkdir -p "$(dirname "$dir/$rel")"
  cp "$here/files/$rel" "$dir/$rel"
  echo "  $rel"
done
cp "$root/brand/out/server-icon.png" "$dir/server-icon.png"
cp "$here/start.sh" "$dir/start.sh"
chmod +x "$dir/start.sh"

echo
echo "Installed $(wc -l <"$managed" | tr -d ' ') plugins and the configs in $dir."
[[ -f $dir/eula.txt ]] || echo "Before the first start, accept Minecraft's EULA (https://aka.ms/MinecraftEULA): echo eula=true > $dir/eula.txt (Raptor does it for you)"
echo "Start it with: (cd $dir && ./start.sh)"
