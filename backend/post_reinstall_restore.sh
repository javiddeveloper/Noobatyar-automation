#!/usr/bin/env bash
#
# Run AFTER the fresh OS install, once Docker is installed and the repo is
# cloned to /root/app (see bootstrap_server.sh for that part).
#
# Restores what pre_reinstall_inventory.sh backed up: the database, .env, media
# uploads, and the TLS certificate — then brings the stack up.
#
# Usage:
#     bash backend/post_reinstall_restore.sh /root/pre-reinstall-<timestamp>
#
# The backup directory must be back on the new server (scp it there first) —
# this script does not fetch it from anywhere.
set -euo pipefail

BACKUP_DIR="${1:?Usage: $0 /path/to/pre-reinstall-<timestamp>}"
APP_DIR=/root/app

say() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
ok()  { printf '\033[32m  ok %s\033[0m\n' "$*"; }
warn(){ printf '\033[33m  ! %s\033[0m\n' "$*"; }
die() { printf '\n\033[31mFAILED: %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "run as root"
[ -d "$BACKUP_DIR/backup" ] || die "$BACKUP_DIR/backup not found — did you pass the right directory?"
[ -d "$APP_DIR" ] || die "$APP_DIR does not exist — clone the repo first (see bootstrap_server.sh)"

# ── 1. .env first — everything else needs it ─────────────────────────────────
say ".env"
if [ -f "$BACKUP_DIR/backup/backend.env" ]; then
    if [ -f "$APP_DIR/backend/.env" ]; then
        warn "$APP_DIR/backend/.env already exists — leaving it. Diff manually if unsure:"
        echo "    diff $BACKUP_DIR/backup/backend.env $APP_DIR/backend/.env"
    else
        cp "$BACKUP_DIR/backup/backend.env" "$APP_DIR/backend/.env"
        ok "restored backend/.env"
    fi
else
    die "no backend.env in backup — cannot proceed without database credentials"
fi
# shellcheck disable=SC1090
set -a; source "$APP_DIR/backend/.env"; set +a

# ── 2. TLS certificate (skip re-issuing, avoid the rate limit) ────────────────
say "TLS certificate"
if [ -f "$BACKUP_DIR/backup/letsencrypt.tar.gz" ]; then
    tar xzf "$BACKUP_DIR/backup/letsencrypt.tar.gz" -C /etc
    ok "restored /etc/letsencrypt — deploy_tls.sh will see the cert already exists and skip issuance"
else
    warn "no letsencrypt backup — deploy_tls.sh will issue a fresh certificate"
fi

# ── 3. Bring up just the database, so it exists to restore into ──────────────
say "Starting the database"
cd "$APP_DIR/backend"
docker compose up -d db
say "Waiting for postgres to accept connections"
for _ in $(seq 1 30); do
    docker compose exec -T db pg_isready -U "${POSTGRES_USER:-postgres}" >/dev/null 2>&1 && break
    sleep 2
done
docker compose exec -T db pg_isready -U "${POSTGRES_USER:-postgres}" >/dev/null 2>&1 \
    || die "database did not become ready"
ok "database is up"

# ── 4. Restore the dump ───────────────────────────────────────────────────────
say "Restoring the database"
if [ -f "$BACKUP_DIR/backup/postgres_dump.sql" ]; then
    docker compose exec -T db \
        psql -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-postgres}" \
        < "$BACKUP_DIR/backup/postgres_dump.sql" \
        || die "psql restore failed — check the dump and POSTGRES_DB/POSTGRES_USER match"
    ok "database restored from $BACKUP_DIR/backup/postgres_dump.sql"
else
    die "no postgres_dump.sql in backup — nothing to restore"
fi

# ── 5. Restore media uploads ──────────────────────────────────────────────────
say "Restoring media volume"
if [ -f "$BACKUP_DIR/backup/media_volume.tar.gz" ]; then
    docker volume create backend_media_volume >/dev/null 2>&1 || true
    docker run --rm \
        -v backend_media_volume:/data \
        -v "$BACKUP_DIR/backup":/backup \
        alpine sh -c "cd /data && tar xzf /backup/media_volume.tar.gz"
    ok "media restored into backend_media_volume"
else
    warn "no media_volume.tar.gz in backup — logos/receipts will be missing until re-uploaded"
fi

say "Done — data restored."
echo "Next: bash backend/server_hardening.sh && bash backend/deploy_tls.sh"
echo "(deploy_tls.sh will detect the restored certificate and skip re-issuing it)"
