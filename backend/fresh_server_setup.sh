#!/usr/bin/env bash
#
# Full setup for a BRAND NEW server (freshly reinstalled OS, nothing on it).
# No data to restore — this is for the "clean slate, no users yet" case.
#
# Assumes: a bare Ubuntu/Debian box with only SSH access. Installs Docker,
# clones the repo, then runs the existing hardening + TLS scripts.
#
# Fetch and run:
#     curl -fsSLo /root/fresh_server_setup.sh \
#       https://raw.githubusercontent.com/javiddeveloper/Noobatyar-automation/develop/backend/fresh_server_setup.sh
#     less /root/fresh_server_setup.sh      # read it before running it
#     bash /root/fresh_server_setup.sh
#
# After this, the box is at api.noobatyar.ir / app.noobatyar.ir with an empty
# database — you'll need to log into /admin and create the first business from
# scratch, same as a brand new install.
set -euo pipefail

APP_DIR=/root/app
REPO=https://github.com/javiddeveloper/Noobatyar-automation.git
BRANCH=develop

say()  { printf '\n\033[1m== %s\033[0m\n' "$*"; }
warn() { printf '\033[33m  ! %s\033[0m\n' "$*"; }
ok()   { printf '\033[32m  ok %s\033[0m\n' "$*"; }
die()  { printf '\n\033[31mFAILED: %s\033[0m\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "run as root"

# ── 1. Base packages ─────────────────────────────────────────────────────────
say "Base packages"
apt-get update -qq
apt-get install -y -qq git curl ca-certificates gnupg ufw
ok "git, curl, ufw installed"

# ── 2. Docker Engine + Compose plugin, from Docker's own repo ────────────────
# The distro's own docker.io package is usually stale enough to matter — the
# compose plugin (`docker compose`, no hyphen) in particular is often missing.
say "Docker"
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    ok "docker + compose plugin already present ($(docker --version))"
else
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc

    . /etc/os-release
    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
      ${VERSION_CODENAME} stable" > /etc/apt/sources.list.d/docker.list

    apt-get update -qq
    apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    systemctl enable --now docker
    ok "docker installed: $(docker --version)"
fi

# ── 3. Clone the repo ─────────────────────────────────────────────────────────
say "Cloning $REPO"
if [ -d "$APP_DIR/.git" ]; then
    ok "$APP_DIR already a git checkout — pulling instead of cloning"
    git -C "$APP_DIR" fetch origin "$BRANCH"
    git -C "$APP_DIR" checkout -f -B "$BRANCH" "origin/$BRANCH"
else
    [ -d "$APP_DIR" ] && die "$APP_DIR exists but is not a git repo — remove it first if you really mean to overwrite it"
    git clone --branch "$BRANCH" --depth 1 "$REPO" "$APP_DIR"
fi
ok "checked out $BRANCH at $(git -C "$APP_DIR" rev-parse --short HEAD)"

# ── 4. .env ───────────────────────────────────────────────────────────────────
# Not committed (it holds secrets), so a brand new server has none. Write a
# minimal one from scratch — deploy_tls.sh will fill in ALLOWED_HOSTS /
# CORS_ALLOWED_ORIGINS / CLIENT_WEB_URL on top of this.
say "backend/.env"
ENV_FILE="$APP_DIR/backend/.env"
if [ -f "$ENV_FILE" ]; then
    ok "$ENV_FILE already exists — leaving it as is"
else
    # pipefail must be off here: `tr ... | head -c N` makes tr die of SIGPIPE
    # once head closes the pipe after N bytes, and pipefail would otherwise
    # treat that expected SIGPIPE as a real failure and abort the script.
    set +o pipefail
    DJANGO_SECRET=$(python3 -c "import secrets; print(secrets.token_urlsafe(50))" 2>/dev/null \
        || tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 64)
    PG_PASSWORD=$(tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32)
    set -o pipefail

    cat > "$ENV_FILE" <<EOF
DEBUG=False
SECRET_KEY=$DJANGO_SECRET

POSTGRES_DB=noobatyar
POSTGRES_USER=noobatyar
POSTGRES_PASSWORD=$PG_PASSWORD

# Fill these in manually — no safe default exists for either:
MELIPAYAMAK_OTP_TOKEN=
MELIPAYAMAK_FROM=
ZIBAL_MERCHANT_ID=
EOF
    chmod 600 "$ENV_FILE"
    ok "generated $ENV_FILE with a random SECRET_KEY and DB password"
    warn "MELIPAYAMAK_OTP_TOKEN, MELIPAYAMAK_FROM, and ZIBAL_MERCHANT_ID are blank — edit $ENV_FILE before real traffic arrives, or OTP/SMS and platform subscription payments won't work"
fi

# ── 5. Hand off to the existing scripts ───────────────────────────────────────
say "Running host hardening"
bash "$APP_DIR/backend/server_hardening.sh"

say "Running the TLS deploy"
bash "$APP_DIR/backend/deploy_tls.sh"

say "Fresh setup complete"
cat <<NOTE

Database is empty. Next steps:
  1. Edit $ENV_FILE — fill in MELIPAYAMAK_OTP_TOKEN, MELIPAYAMAK_FROM,
     ZIBAL_MERCHANT_ID, then: cd $APP_DIR/backend && docker compose up -d web
  2. docker compose exec web python manage.py createsuperuser
  3. Log into https://api.noobatyar.ir/admin/ and create the first business
  4. passwd   — set a real root password (or better, switch to SSH keys and
     disable password auth entirely: PasswordAuthentication no in
     /etc/ssh/sshd_config, then systemctl reload ssh)
NOTE
