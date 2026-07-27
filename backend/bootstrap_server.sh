#!/usr/bin/env bash
#
# Turns /root/app into a real git checkout, then runs the full deploy.
#
# Needed because the server was provisioned by zip/SCP, so `git pull` fails with
# "not a git repository" and none of the deploy scripts are present at all.
#
# Fetch and run:
#     curl -fsSLo /root/bootstrap_server.sh \
#       https://raw.githubusercontent.com/javiddeveloper/Noobatyar-automation/develop/backend/bootstrap_server.sh
#     less /root/bootstrap_server.sh        # read it before running it
#     bash /root/bootstrap_server.sh
#
# What it protects:
#   * /root/app is tarred up first, so the pre-existing state is recoverable.
#   * .env is untracked, and `git checkout` never touches untracked files, so
#     the server's secrets survive. It is checksummed before and after to prove it.
#   * Docker volumes hold the database and are not inside /root/app at all, so
#     nothing here can reach the data.
#
# Safe to re-run.
set -euo pipefail

APP_DIR=/root/app
REPO=https://github.com/javiddeveloper/Noobatyar-automation.git
BRANCH=develop
STAMP=$(date +%Y%m%d-%H%M%S)

say()  { printf '\n\033[1m== %s\033[0m\n' "$*"; }
warn() { printf '\033[33m  ! %s\033[0m\n' "$*"; }
ok()   { printf '\033[32m  ok %s\033[0m\n' "$*"; }
die()  { printf '\n\033[31mFAILED: %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "run as root"
command -v git >/dev/null 2>&1 || { apt-get update -qq && apt-get install -y -qq git; }

[ -d "$APP_DIR" ] || die "$APP_DIR does not exist — is the app somewhere else?"
cd "$APP_DIR"

ENV_FILE="$APP_DIR/backend/.env"
env_sum() { [ -f "$ENV_FILE" ] && md5sum "$ENV_FILE" | cut -d' ' -f1 || echo "absent"; }
ENV_BEFORE=$(env_sum)

# ── 1. Backup ────────────────────────────────────────────────────────────────
say "Backing up $APP_DIR"
BACKUP="/root/app-backup-$STAMP.tar.gz"
tar czf "$BACKUP" -C /root app 2>/dev/null || warn "tar reported warnings (usually files changing during read)"
[ -f "$BACKUP" ] || die "backup was not created — refusing to continue"
ok "$BACKUP ($(du -h "$BACKUP" | cut -f1))"
echo "  restore with: rm -rf /root/app && tar xzf $BACKUP -C /root"

# ── 2. Make it a git checkout ────────────────────────────────────────────────
say "Converting $APP_DIR to a git checkout"
if [ -d "$APP_DIR/.git" ]; then
    ok "already a git repository"
    git remote set-url origin "$REPO" 2>/dev/null || git remote add origin "$REPO"
else
    warn "not a git repository — initialising"
    git init -q
    git remote add origin "$REPO"
fi

git fetch --depth 1 origin "$BRANCH" || die "could not fetch $BRANCH — check the server's internet access"

# -f overwrites tracked files with the repo's version, which is the point.
# Untracked files (.env above all) are left alone; only `git clean` removes
# those, and it is deliberately not used here.
git checkout -f -B "$BRANCH" FETCH_HEAD || die "checkout failed"
ok "checked out $BRANCH at $(git rev-parse --short HEAD)"

# ── 3. Prove .env survived ───────────────────────────────────────────────────
say "Verifying the environment file"
ENV_AFTER=$(env_sum)
if [ "$ENV_BEFORE" = "absent" ] && [ "$ENV_AFTER" = "absent" ]; then
    warn "no backend/.env on this server — deploy_tls.sh will create one"
elif [ "$ENV_BEFORE" = "$ENV_AFTER" ]; then
    ok "backend/.env untouched (md5 $ENV_AFTER)"
else
    die ".env changed during checkout — restore from $BACKUP and investigate"
fi

# ── 4. Confirm the new config actually arrived ───────────────────────────────
say "Checking the deploy files are present"
grep -q '443:443'         backend/docker-compose.yml || die "docker-compose.yml still has no 443 mapping"
grep -q 'ssl_certificate' backend/nginx/nginx.conf   || die "nginx.conf still has no TLS config"
[ -f backend/server_hardening.sh ] || die "server_hardening.sh missing"
[ -f backend/deploy_tls.sh ]       || die "deploy_tls.sh missing"
# The frontend image builds from ../front_client, so that directory has to exist.
[ -d front_client ] || die "front_client/ missing — the frontend build would fail"
ok "compose, nginx.conf, both scripts, and front_client are all in place"

# ── 5. Hand off ──────────────────────────────────────────────────────────────
say "Running host hardening"
bash backend/server_hardening.sh

say "Running the TLS deploy"
bash backend/deploy_tls.sh

say "Bootstrap complete"
echo "From now on this server updates with:  cd /root/app && git pull origin develop"
echo "Backup of the previous state: $BACKUP"
