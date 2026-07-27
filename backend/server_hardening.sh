#!/usr/bin/env bash
#
# Host-level setup for the Noobatyar server: firewall, swap, and Docker sanity.
# Run BEFORE backend/deploy_tls.sh — that one handles the application stack.
#
#     cd /root/app && bash backend/server_hardening.sh
#
# Safe to re-run. Nothing here touches the application containers.
#
# Intended end state — only these reachable from the internet:
#     22   SSH
#     80   HTTP  (ACME challenge + redirect to HTTPS)
#     443  HTTPS
# Everything else is refused, including Portainer on 9000, which is currently
# served over plain HTTP to the whole internet.
set -euo pipefail

say()  { printf '\n\033[1m== %s\033[0m\n' "$*"; }
warn() { printf '\033[33m  ! %s\033[0m\n' "$*"; }
ok()   { printf '\033[32m  ok %s\033[0m\n' "$*"; }

[ "$(id -u)" -eq 0 ] || { echo "run as root"; exit 1; }

# ── 1. Swap ──────────────────────────────────────────────────────────────────
# The Next.js production build is the heaviest thing this box does and gets
# OOM-killed on a small VPS without swap.
say "Swap"
if [ "$(swapon --show --noheadings | wc -l)" -gt 0 ]; then
    ok "swap already present: $(swapon --show=SIZE --noheadings | tr '\n' ' ')"
else
    warn "no swap — creating 2G at /swapfile"
    fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
    ok "2G swap active and persisted in /etc/fstab"
fi

# ── 2. Firewall ──────────────────────────────────────────────────────────────
# Order matters: allow SSH BEFORE enabling, or enabling ufw ends this session
# and locks everyone out of the box.
say "Firewall (ufw)"
command -v ufw >/dev/null 2>&1 || { apt-get update -qq && apt-get install -y -qq ufw; }

ufw allow 22/tcp  >/dev/null
ufw allow 80/tcp  >/dev/null
ufw allow 443/tcp >/dev/null
ok "allowed 22, 80, 443"

ufw default deny incoming  >/dev/null
ufw default allow outgoing >/dev/null

if ufw status | grep -q '^Status: active'; then
    ok "ufw already active"
else
    warn "enabling ufw (SSH is allowed above, so this session survives)"
    ufw --force enable >/dev/null
    ok "ufw enabled"
fi

# ── 3. Docker-published ports ────────────────────────────────────────────────
# The trap: Docker writes its own iptables rules ahead of ufw's, so a published
# container port stays reachable no matter what ufw says. `ufw deny 9000` would
# look like it worked and change nothing. Ports published by containers have to
# be filtered in the DOCKER-USER chain, which Docker leaves for exactly this.
say "Docker-published ports"
if command -v docker >/dev/null 2>&1; then
    echo "  currently published to the host:"
    docker ps --format '    {{.Names}}: {{.Ports}}' 2>/dev/null | grep -v '^\s*$' || true

    block_docker_port() {
        local port="$1" name="$2"
        if iptables -C DOCKER-USER -p tcp --dport "$port" ! -s 127.0.0.1 -j DROP 2>/dev/null; then
            ok "$name ($port) already restricted to localhost"
        else
            iptables -I DOCKER-USER -p tcp --dport "$port" ! -s 127.0.0.1 -j DROP
            ok "$name ($port) is now refused from outside"
        fi
    }

    # Portainer: a full Docker control plane, currently open on plain HTTP.
    # Not deleted — just made local-only. Reach it with an SSH tunnel:
    #     ssh -L 9000:localhost:9000 root@93.127.223.93
    # then open http://localhost:9000 in your own browser.
    if ss -lntp 2>/dev/null | grep -q ':9000 '; then
        block_docker_port 9000 "Portainer"
    else
        ok "nothing listening on 9000"
    fi

    # Persist across reboots — iptables rules are otherwise lost.
    if command -v netfilter-persistent >/dev/null 2>&1; then
        netfilter-persistent save >/dev/null && ok "iptables rules persisted"
    else
        warn "installing iptables-persistent so these rules survive a reboot"
        DEBIAN_FRONTEND=noninteractive apt-get install -y -qq iptables-persistent
        netfilter-persistent save >/dev/null && ok "iptables rules persisted"
    fi
else
    warn "docker not found — skipping (deploy_tls.sh will need it)"
fi

# ── 4. Report ────────────────────────────────────────────────────────────────
say "Listening sockets after hardening"
ss -lntp 2>/dev/null | /usr/bin/awk 'NR==1 || /LISTEN/' | head -20

say "ufw status"
ufw status verbose | head -20

cat <<'NOTE'

Next:
  1. bash backend/deploy_tls.sh      # certificate + stack + frontend rebuild
  2. passwd                          # the current root password is public on GitHub
  3. consider disabling password SSH once a key is installed:
       PasswordAuthentication no     in /etc/ssh/sshd_config, then: systemctl reload ssh

Verify from OUTSIDE the server afterwards — 22, 80, 443 open; 9000 refused.
NOTE
