#!/usr/bin/env bash
#
# Run BEFORE reinstalling the OS. Two things, in order of what actually matters:
#
#   1. BACKUP  — the Postgres database, .env secrets, uploaded media, and the
#      Let's Encrypt certificate. This is the part that cannot be recreated
#      from the git repo. Losing it means losing every business, every
#      appointment, every uploaded payment receipt — permanently.
#
#   2. INVENTORY — what is installed, so the new OS matches. This part is
#      reconstructable (it's on GitHub / apt), so it matters far less than #1,
#      but it saves re-discovering "oh right, we also had X" a week later.
#
# Usage:
#     bash backend/pre_reinstall_inventory.sh
#
# Writes everything under /root/pre-reinstall-<timestamp>/. COPY THAT DIRECTORY
# OFF THE SERVER before wiping it — scp it, or upload it somewhere. A backup
# that lives only on the disk being erased is not a backup.
set -euo pipefail

STAMP=$(date +%Y%m%d-%H%M%S)
OUT="/root/pre-reinstall-$STAMP"
APP_DIR=/root/app

say() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
ok()  { printf '\033[32m  ok %s\033[0m\n' "$*"; }
warn(){ printf '\033[33m  ! %s\033[0m\n' "$*"; }

mkdir -p "$OUT"/{backup,inventory}
say "Writing to $OUT"

# ═════════════════════════════════════════════════════════════════════════════
# PART 1 — BACKUP (the part you cannot get back)
# ═════════════════════════════════════════════════════════════════════════════

# ── Database ─────────────────────────────────────────────────────────────────
say "Database"
if [ -f "$APP_DIR/backend/.env" ]; then
    # shellcheck disable=SC1090
    set -a; source "$APP_DIR/backend/.env"; set +a
fi
if docker compose -f "$APP_DIR/backend/docker-compose.yml" ps db >/dev/null 2>&1; then
    docker compose -f "$APP_DIR/backend/docker-compose.yml" exec -T db \
        pg_dump -U "${POSTGRES_USER:-postgres}" "${POSTGRES_DB:-postgres}" \
        > "$OUT/backup/postgres_dump.sql" \
        && ok "postgres_dump.sql ($(du -h "$OUT/backup/postgres_dump.sql" | cut -f1))" \
        || warn "pg_dump failed — check POSTGRES_USER/POSTGRES_DB in .env"
else
    warn "db container not running — could not dump. Is docker compose up?"
fi

# ── Secrets ──────────────────────────────────────────────────────────────────
say "Environment file"
if [ -f "$APP_DIR/backend/.env" ]; then
    cp "$APP_DIR/backend/.env" "$OUT/backup/backend.env"
    ok "backend/.env copied (contains DB password, SMS token, Zibal ids — keep this file private)"
else
    warn "no backend/.env found at $APP_DIR/backend/.env"
fi

# ── Uploaded media (business logos, payment receipts) ──────────────────────────
say "Media volume"
if docker volume inspect backend_media_volume >/dev/null 2>&1; then
    docker run --rm -v backend_media_volume:/data -v "$OUT/backup":/backup alpine \
        tar czf /backup/media_volume.tar.gz -C /data . \
        && ok "media_volume.tar.gz ($(du -h "$OUT/backup/media_volume.tar.gz" | cut -f1))"
else
    # Volume name is prefixed with the compose project name, which may differ.
    VOL=$(docker volume ls -q | grep -i media_volume | head -1 || true)
    if [ -n "$VOL" ]; then
        docker run --rm -v "$VOL":/data -v "$OUT/backup":/backup alpine \
            tar czf /backup/media_volume.tar.gz -C /data . \
            && ok "media_volume.tar.gz via $VOL"
    else
        warn "no media volume found — list with: docker volume ls"
    fi
fi

# ── TLS certificate ──────────────────────────────────────────────────────────
say "Let's Encrypt certificate"
if [ -d /etc/letsencrypt ]; then
    tar czf "$OUT/backup/letsencrypt.tar.gz" -C /etc letsencrypt 2>/dev/null \
        && ok "letsencrypt.tar.gz — restoring this skips re-issuing and avoids the rate limit"
else
    warn "no /etc/letsencrypt — nothing to back up"
fi

# ── Anything else living directly under /root/app but not in git ───────────────
say "Untracked files in /root/app"
if [ -d "$APP_DIR/.git" ]; then
    (cd "$APP_DIR" && git status --porcelain --ignored 2>/dev/null | grep '^??' | cut -c4-) \
        > "$OUT/inventory/untracked_files.txt" || true
    ok "list saved to inventory/untracked_files.txt — review it for anything else worth keeping"
else
    warn "$APP_DIR is not a git checkout — cannot diff against the repo"
fi

# ═════════════════════════════════════════════════════════════════════════════
# PART 2 — INVENTORY (reconstructable, but convenient to have)
# ═════════════════════════════════════════════════════════════════════════════

say "OS + kernel"
{
    cat /etc/os-release 2>/dev/null
    echo
    uname -a
} > "$OUT/inventory/os.txt"
ok "inventory/os.txt"

say "APT packages"
if command -v dpkg >/dev/null 2>&1; then
    dpkg --get-selections | grep -v deinstall > "$OUT/inventory/apt_packages_all.txt"
    # The short, useful list: packages the admin explicitly asked for, not
    # every transitive dependency apt pulled in with them.
    apt-mark showmanual > "$OUT/inventory/apt_packages_manual.txt" 2>/dev/null || true
    ok "inventory/apt_packages_manual.txt ($(wc -l < "$OUT/inventory/apt_packages_manual.txt") packages) — this is the one worth reading"
fi

say "Docker"
{
    echo "--- docker version ---"; docker --version 2>/dev/null
    echo; echo "--- compose version ---"; docker compose version 2>/dev/null
    echo; echo "--- images ---"; docker images
    echo; echo "--- running containers ---"; docker ps
    echo; echo "--- all containers ---"; docker ps -a
    echo; echo "--- volumes ---"; docker volume ls
    echo; echo "--- networks ---"; docker network ls
} > "$OUT/inventory/docker.txt" 2>&1
ok "inventory/docker.txt"

say "systemd services (enabled, non-default)"
systemctl list-unit-files --state=enabled --no-pager 2>/dev/null \
    > "$OUT/inventory/systemd_enabled.txt" || true
ok "inventory/systemd_enabled.txt"

say "Cron jobs"
{
    echo "--- root crontab ---"; crontab -l 2>/dev/null || echo "(none)"
    echo; echo "--- /etc/cron.d ---"; ls -la /etc/cron.d 2>/dev/null
    echo; echo "--- systemd timers ---"; systemctl list-timers --all --no-pager 2>/dev/null
} > "$OUT/inventory/cron.txt"
ok "inventory/cron.txt — check this for the reminder-SMS cron from DEPLOY_UPDATE_BACKEND.md"

say "Certbot"
{
    certbot certificates 2>/dev/null || echo "(certbot not installed or no certs)"
} > "$OUT/inventory/certbot.txt"
ok "inventory/certbot.txt"

say "Firewall"
{
    echo "--- ufw ---"; ufw status verbose 2>/dev/null || echo "(ufw not active)"
    echo; echo "--- DOCKER-USER chain ---"; iptables -L DOCKER-USER -n --line-numbers 2>/dev/null || echo "(none)"
} > "$OUT/inventory/firewall.txt"
ok "inventory/firewall.txt — remember: 9000 (Portainer) should stay DOCKER-USER-blocked"

say "Listening ports"
ss -lntp 2>/dev/null > "$OUT/inventory/listening_ports.txt" || true
ok "inventory/listening_ports.txt"

say "SSH"
{
    echo "--- sshd_config (non-comment, non-blank lines) ---"
    grep -vE '^\s*#|^\s*$' /etc/ssh/sshd_config 2>/dev/null
    echo; echo "--- authorized_keys (root) ---"
    cat /root/.ssh/authorized_keys 2>/dev/null || echo "(none)"
} > "$OUT/inventory/ssh.txt"
ok "inventory/ssh.txt"

say "Swap"
{ free -h; echo; swapon --show; cat /etc/fstab; } > "$OUT/inventory/swap_fstab.txt"
ok "inventory/swap_fstab.txt"

# ═════════════════════════════════════════════════════════════════════════════
say "Done"
du -sh "$OUT" 2>/dev/null
echo
echo "Everything is under: $OUT"
echo
echo "COPY THIS DIRECTORY OFF THE SERVER NOW, before wiping the OS. For example,"
echo "from your own machine:"
echo
echo "  scp -r root@$(hostname -I 2>/dev/null | awk '{print $1}'):$OUT ./"
echo
echo "A backup that only exists on the disk you are about to erase is not a backup."
