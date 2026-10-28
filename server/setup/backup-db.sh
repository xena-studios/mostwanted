#!/usr/bin/env bash
# Dumps the MostWanted database (pg_dump, custom format) and keeps the last KEEP_DAYS
# days of dumps. A systemd timer runs it nightly at 03:30, before Raptor's daily
# backups (4-5 AM), so a COPY_TO folder inside a Raptor server's files rides along
# in Raptor's backups, off the VPS.
#
#   sudo server/setup/backup-db.sh --install   install it and the nightly timer
#   sudo mostwanted-backup-db                  take a dump now
#   sudo mostwanted-backup-db --restore FILE   put a dump back (asks first)
#   server/setup/backup-db.sh --dry-run        print what it would do
#
# Settings: the environment, else /etc/mostwanted/backup.env (written by --install),
# else these defaults:
#
#   DB_NAME=mostwanted
#   BACKUP_DIR=/var/backups/mostwanted   root only (700)
#   KEEP_DAYS=14
#   COPY_TO=                             also copy the newest dump here as
#                                        mostwanted-latest.dump, e.g. a folder in the
#                                        backend's Raptor server so Raptor's backups
#                                        take it off the VPS. Empty: no copy.
#
# Restoring: stop the game and the backend in Raptor first, then --restore with a
# dump from BACKUP_DIR (or one copied back from off the VPS). It takes a dump of
# what's there now first, so a wrong pick can be undone, then replaces the database's
# contents in one transaction: if anything fails, nothing changed.
#
# Each dump is checked with pg_restore --list before it counts, and old ones are
# only deleted after a new one succeeded, so a failing night never eats the history.
set -euo pipefail

CONFIG=/etc/mostwanted/backup.env
INSTALLED=/usr/local/sbin/mostwanted-backup-db

mode=dump
dry=false
yes=false
restore_file=""
while (($#)); do
  case "$1" in
    --install) mode=install ;;
    --restore) mode=restore; restore_file=${2:?--restore needs a dump file}; shift ;;
    --yes) yes=true ;;
    --dry-run) dry=true ;;
    -h | --help) sed -n '2,/^set -euo/p' "$0" | sed '$d; s/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option $1 (try --help)" >&2; exit 2 ;;
  esac
  shift
done

say() { printf '%s\n' "$*"; }
die() { printf 'backup-db.sh: %s\n' "$*" >&2; exit 1; }

if ! $dry && ((EUID != 0)); then
  die "run it as root (sudo), or with --dry-run"
fi

saved() {
  [[ -r $CONFIG ]] || return 0
  sed -n "s/^$1=\"\{0,1\}\([^\"]*\)\"\{0,1\}\$/\1/p" "$CONFIG" | tail -1
}
setting() {
  local name=$1 default=$2 value
  if [[ -n ${!name+set} ]]; then value=${!name}; else value=$(saved "$name"); [[ -n $value ]] || value=$default; fi
  printf '%s' "$value"
}
DB_NAME=$(setting DB_NAME mostwanted)
BACKUP_DIR=$(setting BACKUP_DIR /var/backups/mostwanted)
KEEP_DAYS=$(setting KEEP_DAYS 14)
COPY_TO=$(setting COPY_TO "")
[[ $DB_NAME =~ ^[a-z_][a-z0-9_]*$ ]] || die "DB_NAME: not a database name: $DB_NAME"
if ! [[ $KEEP_DAYS =~ ^[0-9]+$ ]] || ((KEEP_DAYS < 1)); then die "KEEP_DAYS must be a whole number of days, 1 or more"; fi

if [[ $mode == install ]]; then
  if $dry; then
    say "would install $0 as $INSTALLED"
    say "would write $CONFIG (if missing): DB_NAME=$DB_NAME BACKUP_DIR=$BACKUP_DIR KEEP_DAYS=$KEEP_DAYS COPY_TO=$COPY_TO"
    say "would enable mostwanted-backup-db.timer (nightly 03:30)"
    exit 0
  fi
  [[ $0 -ef $INSTALLED ]] || install -m 755 "$0" "$INSTALLED"
  install -d -m 755 /etc/mostwanted
  if [[ ! -f $CONFIG ]]; then
    cat >"$CONFIG" <<EOF
# Nightly database dumps (server/setup/backup-db.sh). Edit; takes effect next run.
DB_NAME="$DB_NAME"
BACKUP_DIR="$BACKUP_DIR"
KEEP_DAYS="$KEEP_DAYS"
# A folder to also copy the newest dump to (as mostwanted-latest.dump), e.g. in the
# backend's Raptor server, so Raptor's backups carry it off the VPS. Empty: none.
COPY_TO="$COPY_TO"
EOF
    say "wrote $CONFIG"
  fi
  if [[ -d /run/systemd/system ]]; then
    cat >/etc/systemd/system/mostwanted-backup-db.service <<EOF
[Unit]
Description=MostWanted: dump the database
After=postgresql.service

[Service]
Type=oneshot
ExecStart=$INSTALLED
Nice=10
IOSchedulingClass=idle
EOF
    cat >/etc/systemd/system/mostwanted-backup-db.timer <<EOF
[Unit]
Description=MostWanted: dump the database nightly

[Timer]
OnCalendar=*-*-* 03:30:00
Persistent=true

[Install]
WantedBy=timers.target
EOF
    systemctl daemon-reload
    systemctl enable --now --quiet mostwanted-backup-db.timer
    say "mostwanted-backup-db.timer enabled: nightly at 03:30 ($(timedatectl show -p Timezone --value 2>/dev/null || echo 'system time'))"
  else
    say "no systemd here: run $INSTALLED from cron instead"
  fi
  exit 0
fi

# ---- Dumps ----
# Dumps the database to BACKUP_DIR, checks it, and prints its path last.
dump() {
  local stamp file tmp
  stamp=$(date -u +%Y-%m-%dT%H%M%SZ)
  file=$BACKUP_DIR/$DB_NAME-$stamp.dump
  install -d -m 700 "$BACKUP_DIR"
  tmp=$file.part
  # shellcheck disable=SC2064
  trap "rm -f '$tmp'" EXIT
  (cd / && runuser -u postgres -- pg_dump --format=custom --compress=6 "$DB_NAME") >"$tmp"
  chmod 600 "$tmp"
  # A dump that pg_restore can't read isn't a backup.
  pg_restore --list "$tmp" >/dev/null || die "the dump didn't check out; kept the old ones"
  mv "$tmp" "$file"
  trap - EXIT
  say "dumped $DB_NAME to $file ($(du -h "$file" | cut -f1))" >&2
  printf '%s\n' "$file"
}

if [[ $mode == restore ]]; then
  [[ -r $restore_file ]] || die "can't read $restore_file"
  pg_restore --list "$restore_file" >/dev/null 2>&1 || die "$restore_file isn't a pg_dump custom-format dump"
  if $dry; then
    say "would dump $DB_NAME as it is now, then replace its contents with $restore_file"
    exit 0
  fi
  if ! $yes; then
    say "This replaces everything in the database $DB_NAME with $restore_file."
    say "Stop the game server and the backend in Raptor first."
    read -r -p "Type the database's name to go ahead: " answer
    [[ $answer == "$DB_NAME" ]] || die "not restored"
  fi
  say "First, a dump of what's there now:"
  safety=$(dump)
  # As root, so the file can stay root-only; pg_restore reads it from stdin.
  (cd / && runuser -u postgres -- pg_restore --clean --if-exists --single-transaction --exit-on-error \
    -d "$DB_NAME") <"$restore_file"
  say "Restored $DB_NAME from $restore_file. What was there before is in $safety."
  exit 0
fi

if $dry; then
  say "would dump $DB_NAME to $BACKUP_DIR/$DB_NAME-<time>.dump, check it, delete dumps older than $KEEP_DAYS days${COPY_TO:+, and copy it to $COPY_TO/$DB_NAME-latest.dump}"
  exit 0
fi

file=$(dump)

# Only now that tonight's worked: forget the old ones (never the newest).
find "$BACKUP_DIR" -maxdepth 1 -name "$DB_NAME-*.dump" -type f -mtime "+$((KEEP_DAYS - 1))" ! -path "$file" -print -delete \
  | sed 's/^/deleted /'

if [[ -n $COPY_TO ]]; then
  [[ -d $COPY_TO ]] || die "COPY_TO $COPY_TO doesn't exist"
  cp "$file" "$COPY_TO/.$DB_NAME-latest.dump.part"
  # The files' owner there (Raptor's container user), so the server can read and back it up.
  chown --reference="$COPY_TO" "$COPY_TO/.$DB_NAME-latest.dump.part"
  chmod 600 "$COPY_TO/.$DB_NAME-latest.dump.part"
  mv "$COPY_TO/.$DB_NAME-latest.dump.part" "$COPY_TO/$DB_NAME-latest.dump"
  say "copied to $COPY_TO/$DB_NAME-latest.dump"
fi
