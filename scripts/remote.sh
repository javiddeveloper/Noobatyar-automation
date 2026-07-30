#!/usr/bin/env bash
# Run a command on the production server.
#
# Replaces the ~23 ad-hoc *.exp scripts that each embedded the root password in
# plaintext (and got committed to a public repository). One entry point, no
# secrets in the file.
#
# Preferred — key-based auth, no password anywhere:
#   ssh-copy-id root@<host>          # once
#   DEPLOY_HOST=<host> scripts/remote.sh 'docker compose ps'
#
# Fallback — password from the environment, never a literal:
#   read -rs DEPLOY_PASSWORD; export DEPLOY_PASSWORD
#   DEPLOY_HOST=<host> scripts/remote.sh 'docker compose ps'
#
# `read -rs` keeps the password out of your shell history, which `export
# DEPLOY_PASSWORD=...` on the command line would not.

set -euo pipefail

HOST="${DEPLOY_HOST:?DEPLOY_HOST is not set}"
USER_NAME="${DEPLOY_USER:-root}"
PORT="${DEPLOY_PORT:-22}"
APP_DIR="${DEPLOY_APP_DIR:-/root/app/backend}"

if [ "$#" -eq 0 ]; then
  echo "usage: $0 <command...>" >&2
  echo "example: $0 'docker compose logs web --tail 50'" >&2
  exit 64
fi

# Run from the app directory by default, which is what every one of the old
# scripts did by hand.
REMOTE_CMD="cd ${APP_DIR} && $*"

if [ -n "${DEPLOY_PASSWORD:-}" ]; then
  command -v expect >/dev/null 2>&1 || {
    echo "DEPLOY_PASSWORD is set but 'expect' is not installed; use key auth instead." >&2
    exit 69
  }
  # The password reaches expect through the environment, never argv (argv is
  # world-readable via ps).
  expect -c '
    set timeout 300
    log_user 0
    spawn ssh -o StrictHostKeyChecking=accept-new -o LogLevel=ERROR \
      -p $env(PORT) $env(USER_NAME)@$env(HOST) $env(REMOTE_CMD)
    expect {
      -re {[Pp]assword:} { send "$env(DEPLOY_PASSWORD)\r" }
      timeout { puts stderr "timed out waiting for password prompt"; exit 1 }
      eof { puts stderr "connection closed before prompt"; exit 1 }
    }
    log_user 1
    expect eof
    catch wait result
    exit [lindex $result 3]
  ' 2>&1
else
  exec ssh -o StrictHostKeyChecking=accept-new -p "$PORT" \
    "${USER_NAME}@${HOST}" "$REMOTE_CMD"
fi
