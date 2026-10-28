#!/usr/bin/env bash
# The VPS firewall (docs/04-tech.md, docs/08-website.md#the-api), for Ubuntu 24.04:
#
#   - ufw: nothing comes in unless allowed. Allowed: SSH (every port sshd listens on,
#     so this can't lock you out), the game port, the API port, PostgreSQL from
#     Raptor's servers (bridge raptor0) only, and Raptor's SFTP port if you set one.
#   - nftables table `inet mostwanted`: the API port only from Cloudflare's ranges
#     (https://www.cloudflare.com/ips/), and the game port only from Minehut's once
#     GAME_ALLOW lists them. It filters before Docker's port forwarding, so it also
#     covers ports Raptor publishes from containers, which ufw never sees.
#   - systemd: loads the saved Cloudflare ranges at boot (before Docker), and
#     refreshes them from Cloudflare daily.
#
#   sudo server/setup/firewall.sh              set it all up (safe to run again)
#   server/setup/firewall.sh --dry-run         print what it would do, change nothing
#   sudo mostwanted-firewall --refresh         what the daily timer runs
#   sudo mostwanted-firewall --load            what boot runs: saved ranges, no network
#
# Settings come from the environment, else /etc/mostwanted/firewall.env (written on
# the first run, edit it and run again), else these defaults:
#
#   SSH_PORT=22          also allowed: any other port sshd -T reports
#   GAME_PORT=25565
#   GAME_ALLOW=          Minehut's addresses (CIDRs, space-separated). Empty: anyone.
#                        TODO(owner): fill in once Minehut tells us their ranges.
#   API_PORT=8443        the backend's HTTPS port (Cloudflare Origin Rule points here)
#   SFTP_PORT=           Raptor's SFTP (2022) if you use it; empty keeps it closed
#   DB_PORT=5432         PostgreSQL, reachable from Raptor's servers on raptor0 only
#
# A failed download keeps the last good Cloudflare list: it never opens the port up.
set -euo pipefail

CONFIG=/etc/mostwanted/firewall.env
STATE=/var/lib/mostwanted-firewall
INSTALLED=/usr/local/sbin/mostwanted-firewall
CF_V4_URL=${CF_V4_URL:-https://www.cloudflare.com/ips-v4}
CF_V6_URL=${CF_V6_URL:-https://www.cloudflare.com/ips-v6}

mode=apply
dry=false
while (($#)); do
  case "$1" in
    --dry-run) dry=true ;;
    --refresh) mode=refresh ;;
    --load) mode=load ;;
    -h | --help) sed -n '2,/^set -euo/p' "$0" | sed '$d; s/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option $1 (try --help)" >&2; exit 2 ;;
  esac
  shift
done

say() { printf '%s\n' "$*"; }
warn() { printf 'WARNING: %s\n' "$*" >&2; }
die() { printf 'firewall.sh: %s\n' "$*" >&2; exit 1; }

# Runs a command, or prints it in a dry run.
run() {
  if $dry; then
    printf '  would run: %s\n' "$*"
  else
    "$@"
  fi
}

if ! $dry && ((EUID != 0)); then
  die "run it as root (sudo), or with --dry-run to see what it would do"
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# Settings: environment first, then the saved file, then defaults.
saved() {
  [[ -r $CONFIG ]] || return 0
  sed -n "s/^$1=\"\{0,1\}\([^\"]*\)\"\{0,1\}\$/\1/p" "$CONFIG" | tail -1
}
setting() {
  local name=$1 default=$2 value
  if [[ -n ${!name+set} ]]; then value=${!name}; else value=$(saved "$name"); [[ -n $value ]] || value=$default; fi
  printf '%s' "$value"
}
SSH_PORT=$(setting SSH_PORT 22)
GAME_PORT=$(setting GAME_PORT 25565)
GAME_ALLOW=$(setting GAME_ALLOW "")
API_PORT=$(setting API_PORT 8443)
SFTP_PORT=$(setting SFTP_PORT "")
DB_PORT=$(setting DB_PORT 5432)

for port in "$SSH_PORT" "$GAME_PORT" "$API_PORT" "$DB_PORT" ${SFTP_PORT:+"$SFTP_PORT"}; do
  if ! [[ $port =~ ^[0-9]+$ ]] || ((port < 1 || port > 65535)); then die "not a port: $port"; fi
done

v4_re='^([0-9]{1,3}\.){3}[0-9]{1,3}(/[0-9]{1,2})?$'
v6_re='^[0-9a-fA-F:]+:[0-9a-fA-F:]*(/[0-9]{1,3})?$'
game4=() game6=()
for cidr in $GAME_ALLOW; do
  if [[ $cidr =~ $v4_re ]]; then game4+=("$cidr")
  elif [[ $cidr =~ $v6_re ]]; then game6+=("$cidr")
  else die "GAME_ALLOW: not an address or range: $cidr"
  fi
done

# Cloudflare's ranges: downloaded, checked line by line, then saved. Returns 1 (and
# keeps the saved copy) if the download or the check fails.
fetch_cloudflare() {
  local tmp=$work/download bad=0
  mkdir -p "$tmp"
  if ! curl -fsS --max-time 30 --retry 3 -o "$tmp/v4" "$CF_V4_URL" \
    || ! curl -fsS --max-time 30 --retry 3 -o "$tmp/v6" "$CF_V6_URL"; then
    warn "couldn't download Cloudflare's ranges"
    return 1
  fi
  # Each line must be a range; anything else (an HTML error page) rejects the lot.
  grep -Ev '^\s*$' "$tmp/v4" | grep -Evq "$v4_re" && bad=1
  grep -Ev '^\s*$' "$tmp/v6" | grep -Evq "$v6_re" && bad=1
  if ((bad)) || (($(grep -Ec "$v4_re" "$tmp/v4") < 5)) || (($(grep -Ec "$v6_re" "$tmp/v6") < 3)); then
    warn "Cloudflare's lists don't look right; keeping the saved ones"
    return 1
  fi
  if $dry; then
    say "  Cloudflare: $(grep -Ec "$v4_re" "$tmp/v4") IPv4 and $(grep -Ec "$v6_re" "$tmp/v6") IPv6 ranges"
    mkdir -p "$work/state"
    cp "$tmp/v4" "$work/state/cloudflare-v4"
    cp "$tmp/v6" "$work/state/cloudflare-v6"
  else
    install -d -m 755 "$STATE"
    install -m 644 "$tmp/v4" "$STATE/cloudflare-v4"
    install -m 644 "$tmp/v6" "$STATE/cloudflare-v6"
  fi
}

# "a, b, c" from a file of ranges, one per line.
elements() { grep -E "$2" "$1" | paste -sd, - | sed 's/,/, /g'; }

# The nftables table, replaced in one transaction (nft -f), so there's never a moment
# with the port open.
ruleset() {
  local dir=$1
  cat <<EOF
# Written by mostwanted firewall.sh: edit /etc/mostwanted/firewall.env and run it again.
table inet mostwanted
delete table inet mostwanted
table inet mostwanted {
  set cloudflare4 { type ipv4_addr; flags interval; elements = { $(elements "$dir/cloudflare-v4" "$v4_re") } }
  set cloudflare6 { type ipv6_addr; flags interval; elements = { $(elements "$dir/cloudflare-v6" "$v6_re") } }
EOF
  if ((${#game4[@]})); then
    printf '  set game4 { type ipv4_addr; flags interval; elements = { %s } }\n' "$(printf '%s\n' "${game4[@]}" | paste -sd, - | sed 's/,/, /g')"
  fi
  if ((${#game6[@]})); then
    printf '  set game6 { type ipv6_addr; flags interval; elements = { %s } }\n' "$(printf '%s\n' "${game6[@]}" | paste -sd, - | sed 's/,/, /g')"
  fi
  cat <<EOF

  # Before Docker's port forwarding (dstnat), so published container ports are covered.
  chain prerouting {
    type filter hook prerouting priority -300; policy accept;
    iifname "lo" accept

    # The API: Cloudflare only.
    meta l4proto { tcp, udp } th dport $API_PORT ip saddr @cloudflare4 accept
    meta l4proto { tcp, udp } th dport $API_PORT ip6 saddr @cloudflare6 accept
    meta l4proto { tcp, udp } th dport $API_PORT counter drop
EOF
  if ((${#game4[@]} + ${#game6[@]})); then
    say ""
    say "    # The game: Minehut only."
    ((${#game4[@]})) && say "    meta l4proto { tcp, udp } th dport $GAME_PORT ip saddr @game4 accept"
    ((${#game6[@]})) && say "    meta l4proto { tcp, udp } th dport $GAME_PORT ip6 saddr @game6 accept"
    say "    meta l4proto { tcp, udp } th dport $GAME_PORT counter drop"
  else
    say ""
    say "    # The game: open to anyone until GAME_ALLOW lists Minehut's ranges."
  fi
  say "  }"
  say "}"
}

# Writes and loads the table from the saved (or, in a dry run, just downloaded) ranges.
load_table() {
  local dir=$STATE
  $dry && [[ -d $work/state ]] && dir=$work/state
  [[ -s $dir/cloudflare-v4 && -s $dir/cloudflare-v6 ]] \
    || die "no saved Cloudflare ranges in $dir yet: run with network access first"
  if $dry; then
    say "  would load this nftables table:"
    ruleset "$dir" | sed 's/^/    | /'
    return
  fi
  ruleset "$dir" >"$STATE/mostwanted.nft.new"
  nft -c -f "$STATE/mostwanted.nft.new" || die "nft rejected the new table; the old one is still loaded"
  nft -f "$STATE/mostwanted.nft.new"
  mv "$STATE/mostwanted.nft.new" "$STATE/mostwanted.nft"
  say "Loaded nftables table inet mostwanted: API port $API_PORT from Cloudflare only$( ((${#game4[@]} + ${#game6[@]})) && printf ', game port %s from GAME_ALLOW only' "$GAME_PORT")."
}

case $mode in
  load)
    load_table
    exit 0
    ;;
  refresh)
    fetch_cloudflare || say "Using the saved Cloudflare ranges."
    load_table
    exit 0
    ;;
esac

# ---- Full setup ----
command -v nft >/dev/null || $dry || die "needs nftables: apt-get install nftables (provision.sh installs it)"
command -v ufw >/dev/null || $dry || die "needs ufw: apt-get install ufw (provision.sh installs it)"

say "Settings ($CONFIG)"
say "  SSH_PORT=$SSH_PORT GAME_PORT=$GAME_PORT API_PORT=$API_PORT DB_PORT=$DB_PORT SFTP_PORT=${SFTP_PORT:-closed}"
say "  GAME_ALLOW=${GAME_ALLOW:-(anyone)}"
if $dry; then
  say "  would save them to $CONFIG"
else
  install -d -m 755 /etc/mostwanted
  cat >"$CONFIG.new" <<EOF
# mostwanted firewall settings: edit, then run server/setup/firewall.sh again.
SSH_PORT="$SSH_PORT"
GAME_PORT="$GAME_PORT"
# TODO: Minehut's ranges (space-separated CIDRs) once they publish them. Empty = anyone.
GAME_ALLOW="$GAME_ALLOW"
API_PORT="$API_PORT"
SFTP_PORT="$SFTP_PORT"
DB_PORT="$DB_PORT"
EOF
  mv "$CONFIG.new" "$CONFIG"
fi
[[ -n $GAME_ALLOW ]] || warn "GAME_ALLOW is empty: port $GAME_PORT is open to anyone, not just Minehut. Fill it in $CONFIG once Minehut gives their ranges."

# Every port sshd really listens on, so a wrong SSH_PORT can't cut you off.
ssh_ports=("$SSH_PORT")
if command -v sshd >/dev/null; then
  while read -r port; do
    [[ $port =~ ^[0-9]+$ && " ${ssh_ports[*]} " != *" $port "* ]] && ssh_ports+=("$port")
  done < <(sshd -T 2>/dev/null | awk '$1 == "port" { print $2 }')
fi
((${#ssh_ports[@]} > 1)) && warn "sshd listens on ${ssh_ports[*]:1} as well as SSH_PORT=$SSH_PORT: allowing all of them"

say "Cloudflare ranges"
fetch_cloudflare || { [[ -s $STATE/cloudflare-v4 ]] && ! $dry && say "  using the saved ones"; } || die "no Cloudflare ranges to use"

say "nftables"
load_table

say "ufw"
# SSH first, so it's allowed before anything is denied.
for port in "${ssh_ports[@]}"; do
  run ufw limit "$port/tcp" comment "ssh"
done
run ufw default deny incoming
run ufw default allow outgoing
run ufw allow "$GAME_PORT/tcp" comment "minecraft (sources: nft table inet mostwanted)"
run ufw allow "$API_PORT/tcp" comment "api (Cloudflare only: nft table inet mostwanted)"
[[ -n $SFTP_PORT ]] && run ufw allow "$SFTP_PORT/tcp" comment "raptor sftp"
# Raptor's servers reach PostgreSQL on the host through their bridge's gateway.
if ip link show raptor0 >/dev/null 2>&1; then
  run ufw allow in on raptor0 to any port "$DB_PORT" proto tcp comment "postgresql from raptor servers"
else
  warn "no raptor0 bridge (Raptor not installed yet?): run this again after installing Raptor so its servers can reach PostgreSQL"
fi
run ufw --force enable

say "systemd"
if $dry; then
  say "  would install this script as $INSTALLED, and:"
  say "    mostwanted-firewall.service        --load at boot, before Docker"
  say "    mostwanted-firewall-refresh.timer  --refresh daily (and 5 minutes after boot)"
elif [[ -d /run/systemd/system ]]; then
  [[ $0 -ef $INSTALLED ]] || install -m 755 "$0" "$INSTALLED"
  cat >/etc/systemd/system/mostwanted-firewall.service <<EOF
[Unit]
Description=MostWanted firewall: API port from Cloudflare only (saved ranges)
DefaultDependencies=no
After=nftables.service local-fs.target
Before=network-pre.target docker.service
Wants=network-pre.target

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=$INSTALLED --load

[Install]
WantedBy=multi-user.target
EOF
  cat >/etc/systemd/system/mostwanted-firewall-refresh.service <<EOF
[Unit]
Description=MostWanted firewall: refresh Cloudflare's IP ranges
Wants=network-online.target
After=network-online.target

[Service]
Type=oneshot
ExecStart=$INSTALLED --refresh
EOF
  cat >/etc/systemd/system/mostwanted-firewall-refresh.timer <<EOF
[Unit]
Description=Refresh Cloudflare's IP ranges daily

[Timer]
OnBootSec=5min
OnCalendar=daily
RandomizedDelaySec=1h
Persistent=true

[Install]
WantedBy=timers.target
EOF
  systemctl daemon-reload
  systemctl enable --quiet mostwanted-firewall.service
  systemctl enable --now --quiet mostwanted-firewall-refresh.timer
  say "  mostwanted-firewall.service (boot) and mostwanted-firewall-refresh.timer (daily) enabled"
else
  warn "no systemd here: the table won't come back after a reboot or refresh itself"
fi

say
if $dry; then
  say "Dry run: nothing was changed."
else
  ufw status verbose
  say
  say "Done. Check from another terminal that SSH still works before closing this one."
fi
