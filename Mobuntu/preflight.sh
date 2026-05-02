#!/bin/bash
# preflight.sh — Mobuntu Orange build environment setup
# Run once before using devkit or build.sh
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
    echo "Re-running as root..."
    exec sudo "$0" "$@"
fi

echo "=== Mobuntu Orange — Preflight ==="
echo ""

apt-get update

echo "── Core build dependencies ──"
apt-get install -y \
    debos \
    debootstrap \
    qemu-user-static \
    binfmt-support \
    abootimg \
    e2fsprogs \
    parted \
    zip \
    unzip \
    wget \
    curl \
    git

echo "── Devkit dependencies ──"
apt-get install -y \
    tmux \
    python3 \
    python3-pip

pip3 install requests --break-system-packages

echo "── Optional: extrepo for Mobian apt source ──"
apt-get install -y extrepo || true
extrepo enable mobian 2>/dev/null || true

echo ""
echo "=== Preflight complete ==="
echo "Run: python3 devkit.py"
