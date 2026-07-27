#!/usr/bin/env bash
#
# One-shot TLS cutover for api.noobatyar.ir / app.noobatyar.ir.
#
# Run ON THE SERVER, from anywhere:
#     cd /root/app && git pull origin develop && bash backend/deploy_tls.sh
#
# Safe to re-run: every step checks its own end state first, so a failure
# halfway through can be fixed and the script run again without redoing work
# (in particular it will not re-issue a certificate that already exists, which
# would burn Let's Encrypt's 5-per-week limit for this domain set).
#
# Order is deliberate: certificate, then nginx, then Django, then the frontend
# last. The frontend bakes NEXT_PUBLIC_API_URL=https://api.noobatyar.ir at build
# time, so rebuilding it before TLS works would leave the client app calling a
# dead origin.
set -euo pipefail

APP_DIR=/root/app
BACKEND_DIR="$APP_DIR/backend"
CERT_EMAIL="${CERT_EMAIL:-j.s.mobilecoder@gmail.com}"
CERT_DIR=/etc/letsencrypt/live/api.noobatyar.ir
ENV_FILE="$BACKEND_DIR/.env"

say() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
die() { printf '\n\033[31mFAILED: %s\033[0m\n' "$*" >&2; exit 1; }

cd "$BACKEND_DIR" || die "$BACKEND_DIR not found"

# ── 0. Confirm the new config actually arrived ───────────────────────────────
# The runbook warns /root/app may not be a git checkout. If the code was
# deployed by zip, `git pull` fails silently and everything below would be
# configuring files that do not exist yet.
say "Checking the new config is present"
grep -q '443:443'        docker-compose.yml || die "docker-compose.yml has no 443 mapping — the new code did not reach this server"
grep -q 'ssl_certificate' nginx/nginx.conf  || die "nginx.conf has no TLS config — the new code did not reach this server"
echo "ok: compose publishes 443 and nginx.conf carries TLS"

cp -n docker-compose.yml docker-compose.yml.bak 2>/dev/null || true
cp -n nginx/nginx.conf   nginx/nginx.conf.bak   2>/dev/null || true
mkdir -p "$BACKEND_DIR/certbot/www"

# ── 1. certbot ───────────────────────────────────────────────────────────────
# No python3-certbot-nginx: nginx runs in a container, so the plugin cannot see
# its config. Standalone binds :80 on the host instead.
if ! command -v certbot >/dev/null 2>&1; then
    say "Installing certbot"
    apt-get update -qq
    apt-get install -y -qq certbot
else
    echo "certbot already installed"
fi

# ── 2. Certificate ───────────────────────────────────────────────────────────
if [ -f "$CERT_DIR/fullchain.pem" ]; then
    say "Certificate already exists — skipping issuance"
    certbot certificates 2>/dev/null | grep -A 3 'api.noobatyar.ir' || true
else
    say "Issuing certificate (nginx stops for ~30s)"
    ss -lntp 2>/dev/null | grep -q ':80 ' && docker compose stop nginx || true

    # Hooks are stored with the certificate, so unattended renewals every 60
    # days stop and start nginx by themselves.
    certbot certonly --standalone \
        -d api.noobatyar.ir \
        -d app.noobatyar.ir \
        --non-interactive --agree-tos \
        -m "$CERT_EMAIL" \
        --pre-hook  "docker compose -f $BACKEND_DIR/docker-compose.yml stop nginx" \
        --post-hook "docker compose -f $BACKEND_DIR/docker-compose.yml start nginx" \
        || die "certbot failed — check that port 80 is free and DNS still points here"

    [ -f "$CERT_DIR/fullchain.pem" ] || die "certbot reported success but $CERT_DIR is empty"
fi

# ── 3. nginx ─────────────────────────────────────────────────────────────────
# The port change forces docker to recreate the container rather than reuse the
# old one that only published :80.
say "Starting nginx with the TLS config"
docker compose up -d nginx
sleep 3
docker compose exec -T nginx nginx -t || {
    echo "nginx rejected the config — rolling back"
    cp nginx/nginx.conf.bak nginx/nginx.conf
    docker compose restart nginx
    die "nginx config invalid, previous config restored"
}

# ── 4. Django environment ────────────────────────────────────────────────────
# 93.127.223.93 stays in ALLOWED_HOSTS: the nginx default server still answers
# the bare IP for owner-app builds that predate the domain switch.
say "Updating $ENV_FILE"
touch "$ENV_FILE"
set_env() {
    local key="$1" value="$2"
    if grep -q "^${key}=" "$ENV_FILE"; then
        sed -i "s|^${key}=.*|${key}=${value}|" "$ENV_FILE"
        echo "  updated $key"
    else
        printf '%s=%s\n' "$key" "$value" >> "$ENV_FILE"
        echo "  added   $key"
    fi
}
set_env ALLOWED_HOSTS       "api.noobatyar.ir,app.noobatyar.ir,93.127.223.93,localhost,127.0.0.1"
set_env CORS_ALLOWED_ORIGINS "https://app.noobatyar.ir,https://api.noobatyar.ir"
set_env CLIENT_WEB_URL       "https://app.noobatyar.ir"

say "Restarting Django"
docker compose up -d web
sleep 5
docker compose exec -T web python manage.py migrate --noinput
docker compose exec -T web python manage.py seed_plans

# ── 5. Frontend — last, for the reason in the header ─────────────────────────
say "Rebuilding the frontend against https://api.noobatyar.ir"
echo "(this is the heavy step; if it is OOM-killed, check 'free -h' and add swap)"
docker compose up -d --build frontend

# ── 6. Verify from the server itself ─────────────────────────────────────────
say "Verifying"
echo -n "  https://api.noobatyar.ir  -> "; curl -s -o /dev/null -w '%{http_code}\n' --max-time 20 https://api.noobatyar.ir/api/accounting/plans/ || echo FAILED
echo -n "  https://app.noobatyar.ir  -> "; curl -s -o /dev/null -w '%{http_code}\n' --max-time 20 https://app.noobatyar.ir/ || echo FAILED
echo -n "  http -> https redirect    -> "; curl -s -o /dev/null -w '%{http_code}\n' --max-time 20 http://api.noobatyar.ir/ || echo FAILED
echo -n "  bare IP still served      -> "; curl -s -o /dev/null -w '%{http_code}\n' --max-time 20 http://93.127.223.93/ || echo FAILED
echo -n "  port 443 listening        -> "; (ss -lntp 2>/dev/null | grep -q ':443 ' && echo yes) || echo "NO — check the firewall (ufw status)"

say "Done"
echo "If 443 is listening here but unreachable from outside, the host firewall is the cause."
