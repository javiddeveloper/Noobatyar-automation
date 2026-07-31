#!/usr/bin/env bash
#
# Incremental update for a server that's already past the TLS cutover — pulls
# the latest code, rebuilds only the images whose source changed, migrates,
# and re-seeds plans (idempotent). Does NOT touch nginx, certificates, the
# firewall, or swap — that's server_hardening.sh / deploy_tls.sh, for the
# one-time cutover only.
#
# Run ON THE SERVER:
#     cd /root/app && bash backend/update_server.sh
#
# Safe to re-run.
set -euo pipefail

APP_DIR=/root/app
BACKEND_DIR="$APP_DIR/backend"

say() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
die() { printf '\n\033[31mFAILED: %s\033[0m\n' "$*" >&2; exit 1; }

cd "$APP_DIR" || die "$APP_DIR not found"
[ -d .git ] || die "$APP_DIR is not a git checkout — run bootstrap_server.sh first"

say "Pulling develop"
BEFORE=$(git rev-parse HEAD)
git fetch origin develop || die "fetch failed"
git checkout -f -B develop origin/develop
AFTER=$(git rev-parse --short HEAD)
if [ "$BEFORE" = "$(git rev-parse HEAD)" ]; then
    echo "already up to date at $AFTER"
else
    echo "$(git rev-parse --short "$BEFORE") -> $AFTER"
    git log --oneline "$BEFORE"..HEAD | sed 's/^/  /'
fi

cd "$BACKEND_DIR"

# ── Rebuild only what changed, so an unrelated commit doesn't trigger a
#    needless multi-minute Next.js rebuild ──────────────────────────────────
say "Checking what needs a rebuild"
CHANGED=$(git diff --name-only "$BEFORE" HEAD 2>/dev/null || echo "")

REBUILD_WEB=false
REBUILD_FRONTEND=false
echo "$CHANGED" | grep -qE '^backend/' && REBUILD_WEB=true
echo "$CHANGED" | grep -qE '^front_client/' && REBUILD_FRONTEND=true

if [ "$REBUILD_WEB" = true ]; then
    say "Rebuilding web"
    docker compose up -d --build web
else
    echo "  backend unchanged — skipping web rebuild"
fi

if [ "$REBUILD_FRONTEND" = true ]; then
    say "Rebuilding frontend (this is the slow one)"
    docker compose up -d --build frontend
else
    echo "  front_client unchanged — skipping frontend rebuild"
fi

# ── Django housekeeping — cheap, always safe to run ──────────────────────────
say "Migrations"
docker compose exec -T web python manage.py migrate --noinput

say "seed_plans (idempotent)"
docker compose exec -T web python manage.py seed_plans

say "Recent web logs"
docker compose logs web --tail 30

say "Done — now at $(git rev-parse --short HEAD)"
