#!/usr/bin/env bash
# Sets up a fresh OVH VPS (Ubuntu 24.04, the release Raptor supports) for MostWanted,
# after Raptor is installed (docs/12-launch.md). Safe to run again: every step checks
# first and only changes what isn't right yet.
#
#   sudo server/setup/provision.sh [--env /etc/mostwanted/server.env] [--no-postgres]
#   server/setup/provision.sh --dry-run        print what it would do, change nothing
#
# What it does:
#   1. Packages: curl, jq, git, ufw, nftables, unattended-upgrades, fail2ban, ...
#   2. A `mostwanted` user (no password, no sudo) owning /srv/mostwanted, for the repo
#      checkout and builds.
#   3. Java 25 (Eclipse Temurin, Adoptium's apt repo) to build and test on the box.
#   4. PostgreSQL 18 (the PostgreSQL project's apt repo): the database and its user
#      from the .env (DB_NAME, DB_USER, DB_PASSWORD; a missing password is generated
#      and saved there), small memory settings for the 12 GB box, listening on
#      localhost and on Raptor's bridge so its servers can connect.
#   5. Hardening: automatic security updates, fail2ban for SSH, and SSH keys only, but
#      only once a key is set up for the account you're using; otherwise it warns and
#      leaves passwords on, so it can't lock you out.
#   6. The nightly database dump (server/setup/backup-db.sh --install).
#
# It never stores a secret anywhere but the .env (mode 600), and never prints one.
# The firewall is a separate step: server/setup/firewall.sh.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
env_file=/etc/mostwanted/server.env
dry=false
postgres=true
PG_VERSION=18
while (($#)); do
  case "$1" in
    --dry-run) dry=true ;;
    --env) env_file=$2; shift ;;
    --no-postgres) postgres=false ;;
    -h | --help) sed -n '2,/^set -euo/p' "$0" | sed '$d; s/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option $1 (try --help)" >&2; exit 2 ;;
  esac
  shift
done

warnings=()
say() { printf '%s\n' "$*"; }
step() { printf '\n== %s\n' "$*"; }
warn() { printf 'WARNING: %s\n' "$*" >&2; warnings+=("$*"); }
die() { printf 'provision.sh: %s\n' "$*" >&2; exit 1; }

run() {
  if $dry; then
    printf '  would run: %s\n' "$*"
  else
    "$@"
  fi
}

# Writes $1 (mode $2) from stdin if it differs. Returns 0 if it changed (or would).
put() {
  local dest=$1 mode=$2 content
  content=$(cat)
  if [[ -f $dest ]] && [[ $(cat "$dest") == "$content" ]]; then
    return 1
  fi
  if $dry; then
    say "  would write $dest:"
    printf '%s\n' "$content" | sed 's/^/    | /'
  else
    install -d -m 755 "$(dirname "$dest")"
    printf '%s\n' "$content" >"$dest.new"
    chmod "$mode" "$dest.new"
    mv "$dest.new" "$dest"
    say "  wrote $dest"
  fi
}

has_systemd() { [[ -d /run/systemd/system ]]; }

if ! $dry && ((EUID != 0)); then
  die "run it as root (sudo), or with --dry-run to see what it would do"
fi

# ---- The system ----
[[ -r /etc/os-release ]] || die "no /etc/os-release: this is for Ubuntu"
# shellcheck disable=SC1091
. /etc/os-release
codename=${VERSION_CODENAME:-}
if [[ ${ID:-} != ubuntu ]]; then
  if $dry; then
    warn "this isn't Ubuntu (${ID:-unknown}): the real run only works on Ubuntu 24.04"
  else
    die "this is for Ubuntu 24.04 (found ${PRETTY_NAME:-unknown})"
  fi
elif [[ ${VERSION_ID:-} != 24.04 ]]; then
  warn "Raptor supports Ubuntu 24.04; this is ${VERSION_ID:-unknown}. Carrying on."
fi
arch=$(dpkg --print-architecture 2>/dev/null || uname -m)

# ---- The .env ----
step "Settings from $env_file"
env_value() {
  [[ -r $env_file ]] || return 0
  sed -n "s/^$1=//p" "$env_file" | tail -1 | sed "s/^[\"']//; s/[\"']\$//"
}
if [[ ! -e $env_file ]] && ! $dry; then
  die "no $env_file. Make it first: sudo install -D -m 600 server/.env.example $env_file"
fi
DB_NAME=$(env_value DB_NAME); DB_NAME=${DB_NAME:-mostwanted}
DB_USER=$(env_value DB_USER); DB_USER=${DB_USER:-mostwanted}
DB_PASSWORD=$(env_value DB_PASSWORD)
for name in "$DB_NAME" "$DB_USER"; do
  [[ $name =~ ^[a-z_][a-z0-9_]*$ ]] || die "DB_NAME and DB_USER must be lowercase letters, digits and _ (got $name)"
done
if [[ -n $DB_PASSWORD && ( $DB_PASSWORD == *"'"* || $DB_PASSWORD == *\\* ) ]]; then
  die "DB_PASSWORD in $env_file has a ' or \\ in it: the configs quote it, so pick another"
fi
say "  database $DB_NAME, user $DB_USER, password $([[ -n $DB_PASSWORD ]] && echo set || echo 'not set yet')"
if [[ -e $env_file ]] && [[ $(stat -c %a "$env_file" 2>/dev/null || echo 600) != 600 ]]; then
  run chmod 600 "$env_file"
fi

# ---- 1. Packages ----
step "Packages"
export DEBIAN_FRONTEND=noninteractive
base=(ca-certificates curl gnupg jq git perl ufw nftables unattended-upgrades fail2ban python3-systemd openssh-server iproute2)
missing=()
for package in "${base[@]}"; do
  dpkg -s "$package" >/dev/null 2>&1 || missing+=("$package")
done
if ((${#missing[@]})); then
  run apt-get update -q
  run apt-get install -y -q "${missing[@]}"
else
  say "  all there"
fi

# ---- 2. The mostwanted user ----
step "User mostwanted"
if id mostwanted >/dev/null 2>&1; then
  say "  exists"
else
  # No password (can't log in with one) and no sudo: it only owns files and runs builds.
  run useradd --create-home --shell /bin/bash --comment "MostWanted" mostwanted
fi
if [[ -d /srv/mostwanted ]]; then
  say "  /srv/mostwanted exists"
else
  run install -d -o mostwanted -g mostwanted -m 750 /srv/mostwanted
fi

# Adds an apt repository with its key, once.
apt_repo() {
  local name=$1 key_url=$2 line=$3 key=/etc/apt/keyrings/$1.asc changed=false
  if [[ ! -s $key ]]; then
    run install -d -m 755 /etc/apt/keyrings
    if $dry; then
      say "  would download $key_url to $key"
    else
      curl -fsSL "$key_url" -o "$key.new"
      grep -q "BEGIN PGP PUBLIC KEY BLOCK" "$key.new" || die "$key_url isn't a PGP key"
      mv "$key.new" "$key"
      chmod 644 "$key"
    fi
    changed=true
  fi
  put "/etc/apt/sources.list.d/$name.list" 644 <<<"$line" && changed=true
  if $changed; then
    run apt-get update -q
  fi
  return 0
}

# ---- 3. Java 25 ----
step "Java 25 (Temurin)"
if dpkg -s temurin-25-jdk >/dev/null 2>&1; then
  say "  installed: $(java -version 2>&1 | head -1)"
else
  apt_repo adoptium https://packages.adoptium.net/artifactory/api/gpg/key/public \
    "deb [arch=$arch signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $codename main"
  run apt-get install -y -q temurin-25-jdk
fi

# ---- 4. PostgreSQL ----
if $postgres; then
  step "PostgreSQL $PG_VERSION"
  if dpkg -s "postgresql-$PG_VERSION" >/dev/null 2>&1; then
    say "  installed"
  else
    apt_repo pgdg https://www.postgresql.org/media/keys/ACCC4CF8.asc \
      "deb [arch=$arch signed-by=/etc/apt/keyrings/pgdg.asc] https://apt.postgresql.org/pub/repos/apt $codename-pgdg main"
    run apt-get install -y -q "postgresql-$PG_VERSION"
  fi

  pg_conf=/etc/postgresql/$PG_VERSION/main
  # Raptor runs servers in Docker on the bridge raptor0; they reach the host at its
  # gateway address. Without Raptor yet, localhost only (run this again after).
  gateway="" subnet=""
  if command -v docker >/dev/null && docker network inspect raptor_nw >/dev/null 2>&1; then
    gateway=$(docker network inspect raptor_nw -f '{{range .IPAM.Config}}{{.Gateway}} {{end}}' | awk '{print $1}')
    subnet=$(docker network inspect raptor_nw -f '{{range .IPAM.Config}}{{.Subnet}} {{end}}' | awk '{print $1}')
  fi
  if [[ -z $gateway || -z $subnet ]]; then
    warn "Raptor's network (raptor_nw) isn't there yet: PostgreSQL listens on localhost only. Install Raptor, then run this again."
  fi

  restart=false
  if [[ -n $gateway ]]; then
    # At boot PostgreSQL can start before Docker brings raptor0 up; this lets it listen
    # on the bridge's address anyway instead of quietly skipping it.
    if put /etc/sysctl.d/60-mostwanted.conf 644 <<<"net.ipv4.ip_nonlocal_bind = 1"; then
      run sysctl -q -w net.ipv4.ip_nonlocal_bind=1
    fi
  fi
  # Memory: the game has 8 GB of the 12, so PostgreSQL stays small (docs/04-tech.md).
  put "$pg_conf/conf.d/10-mostwanted.conf" 644 <<EOF && restart=true
# Written by server/setup/provision.sh: run it again rather than editing this.
listen_addresses = 'localhost${gateway:+,$gateway}'
max_connections = 50
shared_buffers = 256MB
effective_cache_size = 1GB
work_mem = 8MB
maintenance_work_mem = 64MB
password_encryption = scram-sha-256
log_min_duration_statement = 500ms
EOF

  # Raptor's servers may log in to this database as this user, with the password.
  hba=$pg_conf/pg_hba.conf
  block=""
  [[ -n $subnet ]] && block="host    $DB_NAME    $DB_USER    $subnet    scram-sha-256"
  if [[ -f $hba ]] || $dry; then
    current=$( [[ -f $hba ]] && sed -n '/^# BEGIN mostwanted/,/^# END mostwanted/p' "$hba" | sed '1d;$d' || true)
    if [[ $current != "$block" ]]; then
      if $dry; then
        say "  would set pg_hba.conf's mostwanted block to: ${block:-(nothing)}"
      else
        sed -i '/^# BEGIN mostwanted/,/^# END mostwanted/d' "$hba"
        if [[ -n $block ]]; then
          printf '# BEGIN mostwanted (server/setup/provision.sh): Raptor servers on raptor0\n%s\n# END mostwanted\n' "$block" >>"$hba"
        fi
        say "  updated $hba"
      fi
      restart=true
    fi
  fi

  pg_running() { pg_lsclusters -h 2>/dev/null | awk -v v="$PG_VERSION" '$1 == v && $2 == "main" { print $4 }' | grep -q online; }
  if ! $dry; then
    if $restart || ! pg_running; then
      if has_systemd; then
        systemctl enable --quiet "postgresql@$PG_VERSION-main"
        systemctl restart "postgresql@$PG_VERSION-main"
      else
        pg_ctlcluster "$PG_VERSION" main restart || pg_ctlcluster "$PG_VERSION" main start
      fi
      say "  (re)started PostgreSQL"
    fi
  elif $restart; then
    say "  would restart PostgreSQL"
  fi

  # The password: from the .env, or a new one saved there. Never on a command line.
  if [[ -z $DB_PASSWORD ]]; then
    if $dry; then
      say "  would generate DB_PASSWORD and save it in $env_file"
    else
      DB_PASSWORD=$(openssl rand -hex 24 2>/dev/null || head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')
      if grep -q '^DB_PASSWORD=' "$env_file"; then
        sed -i "s/^DB_PASSWORD=.*/DB_PASSWORD=$DB_PASSWORD/" "$env_file"
      else
        printf 'DB_PASSWORD=%s\n' "$DB_PASSWORD" >>"$env_file"
      fi
      chmod 600 "$env_file"
      say "  generated DB_PASSWORD and saved it in $env_file (copy it into the game's and the backend's .env)"
    fi
  fi
  if $dry; then
    say "  would create role $DB_USER and database $DB_NAME if missing, and set the role's password"
  else
    # psql reads the password from stdin (\set), so it never shows in `ps`.
    cd /
    runuser -u postgres -- psql -X -q -v ON_ERROR_STOP=1 -d postgres <<EOF
\set pw '$DB_PASSWORD'
SELECT format('CREATE ROLE %I LOGIN', '$DB_USER') WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$DB_USER') \gexec
ALTER ROLE "$DB_USER" WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'pw';
SELECT format('CREATE DATABASE %I OWNER %I', '$DB_NAME', '$DB_USER') WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$DB_NAME') \gexec
EOF
    say "  role $DB_USER and database $DB_NAME ready"
    if [[ -n $gateway ]]; then
      say "  Raptor servers connect with DB_HOST=$gateway (localhost inside a container is the container itself)"
    fi
  fi
else
  step "PostgreSQL: skipped (--no-postgres)"
fi

# ---- 5. Hardening ----
step "Automatic security updates"
put /etc/apt/apt.conf.d/20auto-upgrades 644 <<'EOF' || say "  on"
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
APT::Periodic::AutocleanInterval "7";
EOF
# No automatic reboots: a restart kicks every player. Reboot by hand when
# /var/run/reboot-required appears (the launch doc says how to check).

step "fail2ban for SSH"
ssh_ports=$(sshd -T 2>/dev/null | awk '$1 == "port" { print $2 }' | paste -sd, - || true)
ssh_ports=${ssh_ports:-22}
if put /etc/fail2ban/jail.d/mostwanted.local 644 <<EOF; then
# Written by server/setup/provision.sh.
[sshd]
enabled = true
port = $ssh_ports
backend = systemd
maxretry = 5
findtime = 10m
bantime = 1h
EOF
  if ! $dry && has_systemd; then
    systemctl enable --quiet fail2ban
    systemctl restart fail2ban
  fi
else
  say "  on"
fi

step "SSH: keys only?"
# Only switch passwords off when the account you're logged in as can get in with a key.
login_user=${SUDO_USER:-root}
login_home=$(getent passwd "$login_user" | cut -d: -f6)
keys_file=${login_home:-/nonexistent}/.ssh/authorized_keys
key_count=0
if [[ -r $keys_file ]]; then
  key_count=$(grep -cE '^(ssh-(ed25519|rsa)|ecdsa-sha2-nistp[0-9]+|sk-(ssh-ed25519|ecdsa-sha2-nistp256)@openssh.com) ' "$keys_file" || true)
fi
sshd_dropin=/etc/ssh/sshd_config.d/10-mostwanted.conf
if ((key_count > 0)); then
  root_login=prohibit-password
  if [[ $login_user != root ]] && id -nG "$login_user" | grep -qw sudo; then
    root_login=no
  else
    warn "you're logged in as root: root keeps key logins (PermitRootLogin prohibit-password). Make a sudo user with a key, log in as them, and run this again to turn root logins off."
  fi
  # 10- sorts before Ubuntu's 50-cloud-init.conf, and sshd keeps the first value it reads.
  if put "$sshd_dropin" 644 <<EOF; then
# Written by server/setup/provision.sh: $login_user has a key in $keys_file.
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin $root_login
EOF
    if ! $dry; then
      if sshd -t; then
        if has_systemd; then systemctl reload ssh || systemctl reload sshd || true; fi
        say "  passwords off for SSH ($login_user has $key_count key(s)). Keep this session open and check a new login works."
      else
        rm -f "$sshd_dropin"
        warn "sshd didn't accept the new settings, so they were removed and nothing changed"
      fi
    fi
  else
    say "  already keys only"
  fi
else
  warn "no SSH key in $keys_file for $login_user: password logins stay on. Add your public key there (ssh-copy-id from your PC), check you can log in with it, then run this again."
fi

# ---- 6. Database backups ----
if $postgres; then
  step "Nightly database dump"
  if $dry; then
    run "$here/backup-db.sh" --install
  else
    "$here/backup-db.sh" --install
  fi
fi

# ---- Done ----
step "Done"
if [[ -f /var/run/reboot-required ]]; then
  warn "the system wants a reboot for updates (sudo reboot when no one's playing)"
fi
if ((${#warnings[@]})); then
  say "Warnings:"
  printf '  - %s\n' "${warnings[@]}"
fi
say "Next: server/setup/firewall.sh, then the rest of docs/12-launch.md."
$dry && say "Dry run: nothing was changed."
exit 0
