#!/usr/bin/env bash
#
# Nightly backup: Postgres dump + media uploads -> encrypted archive -> Telegram.
#
# Install (on the server):
#   cp scripts/backup-to-telegram.sh /root/backup-to-telegram.sh
#   chmod 700 /root/backup-to-telegram.sh
#   # create /root/.backup_env (chmod 600) with:
#   #   TELEGRAM_BOT_TOKEN=...      from @BotFather
#   #   TELEGRAM_CHAT_ID=...        e.g. -1001234567890, bot must be a channel admin
#   #   BACKUP_PASSPHRASE=...       strong; STORE IT SOMEWHERE SAFE, OFF THIS SERVER
#   crontab -l 2>/dev/null | { cat; echo "0 0 * * * /root/backup-to-telegram.sh >> /var/log/noobatyar-backup.log 2>&1"; } | crontab -
#
# Secrets live only in /root/.backup_env — never in this file, which is committed
# to a public repository.
#
# Why the archive is encrypted: the dump contains customer names and phone
# numbers plus Django password hashes. Anything sent to a Telegram channel stays
# on Telegram's servers indefinitely and is readable by anyone who gains access
# to the channel or the bot token. gpg keeps the channel holding ciphertext only.
#
# Restore:
#   gpg --decrypt noobatyar-YYYYmmdd-HHMMSS.tar.gz.gpg > backup.tar.gz
#   tar xzf backup.tar.gz
#   docker compose exec -T db psql -U postgres -d postgres < db.sql

set -euo pipefail

# Overridable so the pipeline can be rehearsed against throwaway credentials
# without touching the real secrets file.
ENV_FILE="${BACKUP_ENV_FILE:-/root/.backup_env}"
COMPOSE_DIR=/root/app/backend
WORK_DIR=/root/backups
RETENTION_DAYS=7
STAMP="$(date +%Y%m%d-%H%M%S)"
LABEL="noobatyar-${STAMP}"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

[ -r "$ENV_FILE" ] || { log "FATAL: $ENV_FILE missing or unreadable"; exit 78; }
# shellcheck disable=SC1090
set -a; . "$ENV_FILE"; set +a

: "${TELEGRAM_BOT_TOKEN:?TELEGRAM_BOT_TOKEN not set in $ENV_FILE}"
: "${TELEGRAM_CHAT_ID:?TELEGRAM_CHAT_ID not set in $ENV_FILE}"

API="https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}"

notify_failure() {
  # A backup system that fails silently is worse than no backup at all, so a
  # failed run reports itself to the same channel that receives the archives.
  local msg="$1"
  curl -s --max-time 30 -X POST "${API}/sendMessage" \
    -d "chat_id=${TELEGRAM_CHAT_ID}" \
    --data-urlencode "text=❌ بکاپ نوبت‌یار ناموفق بود (${STAMP})

${msg}" >/dev/null || log "WARN: could not deliver the failure notice"
}

mkdir -p "$WORK_DIR"
STAGE="$(mktemp -d "${WORK_DIR}/.stage-XXXXXX")"

# One EXIT trap doing both jobs: a second `trap ... EXIT` would silently replace
# the first, and RETURN traps only fire inside functions, not at script level.
cleanup() {
  local code=$?
  rm -rf "$STAGE"
  if [ "$code" -ne 0 ]; then
    log "FAILED (exit $code)"
    notify_failure "خطا در مرحله‌ی اجرا — لاگ سرور: /var/log/noobatyar-backup.log"
  fi
}
trap cleanup EXIT

cd "$COMPOSE_DIR"

DB_NAME="$(grep -E '^POSTGRES_DB=' .env | cut -d= -f2-)"
DB_USER="$(grep -E '^POSTGRES_USER=' .env | cut -d= -f2-)"
: "${DB_NAME:?POSTGRES_DB missing from ${COMPOSE_DIR}/.env}"
: "${DB_USER:?POSTGRES_USER missing from ${COMPOSE_DIR}/.env}"

log "dumping database ${DB_NAME}"
docker compose exec -T db pg_dump -U "$DB_USER" -d "$DB_NAME" > "${STAGE}/db.sql"
# pg_dump can exit 0 having written nothing useful if the container is wedged;
# a real dump always declares at least one table.
grep -q 'CREATE TABLE' "${STAGE}/db.sql" || {
  log "FATAL: dump contains no CREATE TABLE — refusing to ship a useless backup"
  exit 65
}
log "dump ok ($(wc -c < "${STAGE}/db.sql") bytes)"

log "copying media uploads"
docker compose cp web:/app/media "${STAGE}/media" >/dev/null 2>&1 || log "WARN: media copy skipped"

ARCHIVE="${WORK_DIR}/${LABEL}.tar.gz"
tar czf "$ARCHIVE" -C "$STAGE" .

if [ -n "${BACKUP_PASSPHRASE:-}" ]; then
  log "encrypting"
  printf '%s' "$BACKUP_PASSPHRASE" > "${STAGE}/pass"
  chmod 600 "${STAGE}/pass"
  gpg --batch --yes --quiet \
      --passphrase-file "${STAGE}/pass" \
      --cipher-algo AES256 --symmetric \
      --output "${ARCHIVE}.gpg" "$ARCHIVE"
  shred -u "${STAGE}/pass" 2>/dev/null || rm -f "${STAGE}/pass"
  rm -f "$ARCHIVE"
  UPLOAD="${ARCHIVE}.gpg"
  CAPTION="🔐 بکاپ نوبت‌یار — ${STAMP} (رمزگذاری‌شده)"
else
  log "WARNING: BACKUP_PASSPHRASE is not set — uploading UNENCRYPTED."
  log "         This archive holds customer phone numbers and password hashes."
  UPLOAD="$ARCHIVE"
  CAPTION="⚠️ بکاپ نوبت‌یار — ${STAMP} (بدون رمز — یک passphrase تنظیم کنید)"
fi

SIZE=$(wc -c < "$UPLOAD")
log "uploading $(basename "$UPLOAD") (${SIZE} bytes)"
# Telegram's Bot API caps sendDocument at 50 MB.
if [ "$SIZE" -gt 49000000 ]; then
  log "FATAL: archive exceeds Telegram's 50 MB limit"
  notify_failure "حجم بکاپ از سقف ۵۰ مگابایت تلگرام گذشت (${SIZE} بایت). فایل روی سرور هست: ${UPLOAD}"
  exit 75
fi

HTTP=$(curl -s -o "${STAGE}/tg.json" -w '%{http_code}' --max-time 300 \
  -X POST "${API}/sendDocument" \
  -F "chat_id=${TELEGRAM_CHAT_ID}" \
  -F "caption=${CAPTION}" \
  -F "document=@${UPLOAD}")

if [ "$HTTP" != "200" ]; then
  log "FATAL: Telegram rejected the upload (HTTP ${HTTP}): $(cat "${STAGE}/tg.json" 2>/dev/null)"
  exit 76
fi
log "uploaded ok"

# Keep local copies too: Telegram is the offsite copy, not the only one.
while IFS= read -r old; do
  log "pruned $(basename "$old")"
  rm -f "$old"
done < <(find "$WORK_DIR" -maxdepth 1 -name 'noobatyar-*.tar.gz*' -type f \
              -mtime "+${RETENTION_DAYS}")

log "done"
