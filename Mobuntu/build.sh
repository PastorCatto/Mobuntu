#!/bin/bash
set -euo pipefail

# debos requires root for debootstrap
if [ "$(id -u)" -ne 0 ]; then
    echo "Re-running as root (debos requires it)..."
    exec sudo "$0" "$@"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
    echo "Usage: $0 -d <device> [-s <suite>] [-i] [-h]"
    echo ""
    echo "  -d  Device codename (e.g. beryllium, fajita)"
    echo "  -s  Ubuntu suite override (default: from device.conf)"
    echo "  -i  Image only — skip rootfs build, reuse existing tarball"
    echo "  -h  This help text"
    echo ""
    echo "Available devices:"
    for d in "$SCRIPT_DIR/devices"/*/; do
        codename="$(basename "$d")"
        conf="$d/device.conf"
        if [ -f "$conf" ]; then
            # shellcheck disable=SC1090
            source "$conf"
            echo "  $codename — $DEVICE_MODEL ($DEVICE_BRAND)"
        fi
    done
    exit 1
}

DEVICE=""
SUITE_OVERRIDE=""
IMAGE_ONLY=0
UI_OVERRIDE=""

while getopts "d:s:u:ih" opt; do
    case $opt in
        d) DEVICE="$OPTARG" ;;
        s) SUITE_OVERRIDE="$OPTARG" ;;
        u) UI_OVERRIDE="$OPTARG" ;;
        i) IMAGE_ONLY=1 ;;
        h) usage ;;
        *) usage ;;
    esac
done

[ -z "$DEVICE" ] && { echo "ERROR: Device required. Use -d <codename>"; usage; }

DEVICE_CONF="$SCRIPT_DIR/devices/$DEVICE/device.conf"
[ -f "$DEVICE_CONF" ] || {
    echo "ERROR: No device config found at $DEVICE_CONF"
    echo "Available devices: $(ls "$SCRIPT_DIR/devices/")"
    exit 1
}

# Load device config
# shellcheck disable=SC1090
source "$DEVICE_CONF"

SUITE="${SUITE_OVERRIDE:-${DEVICE_SUITE:-plucky}}"

# ── Suite warning gate ──────────────────────────────────────────────────────
if [ "$SUITE" = "resolute" ]; then
    echo ""
    echo "┌─────────────────────────────────────────────────────────────────┐"
    echo "│  WARNING: Ubuntu 26.04 'resolute' has known SDM845 regressions │"
    echo "│  WiFi, Bluetooth, and audio are affected on most devices.       │"
    echo "│  Recommended suite: plucky (25.04)                              │"
    echo "└─────────────────────────────────────────────────────────────────┘"
    echo ""
    read -rp "Type YES to confirm resolute: " confirm1
    [ "$confirm1" = "YES" ] || { echo "Aborted."; exit 1; }
    read -rp "Type RESOLUTE to confirm again: " confirm2
    [ "$confirm2" = "RESOLUTE" ] || { echo "Aborted."; exit 1; }
    echo ""
fi

# ── Validate required device vars ──────────────────────────────────────────
: "${KERNEL_VERSION:?device.conf missing KERNEL_VERSION}"

# ── UI selection ───────────────────────────────────────────────────────────
CURRENT_UI="${DEVICE_UI:-ubuntu-desktop-minimal}"

if [ -n "$UI_OVERRIDE" ]; then
    # Passed via -u flag (e.g. from devkit) — no prompt
    DEVICE_UI="$UI_OVERRIDE"
else
    # Interactive prompt for terminal use — times out after 5s
    echo ""
    echo "Select UI (default: $CURRENT_UI, auto-selecting in 5s):"
    echo "  1) ubuntu-desktop-minimal"
    echo "  2) phosh"
    echo "  3) plasma-mobile"
    echo ""
    if read -r -t 5 -p "Choice [1-3]: " ui_choice; then
        case "$ui_choice" in
            1) DEVICE_UI="ubuntu-desktop-minimal" ;;
            2) DEVICE_UI="phosh" ;;
            3) DEVICE_UI="plasma-mobile" ;;
            *) echo "Invalid — using default"; DEVICE_UI="$CURRENT_UI" ;;
        esac
    else
        echo ""
        echo "No input — defaulting to $CURRENT_UI"
        DEVICE_UI="$CURRENT_UI"
    fi
fi
echo "  UI: $DEVICE_UI"
echo ""

# ── Output filenames ────────────────────────────────────────────────────────
IMG_FILE="mobuntu-${DEVICE}-$(date +%Y%m%d).img"
ROOTFS_FILE="mobuntu-rootfs-${DEVICE}.tar.gz"

echo "=== Mobuntu Build ==="
echo "  Device  : $DEVICE_MODEL ($DEVICE_CODENAME)"
echo "  Suite   : $SUITE"
echo "  Kernel  : $KERNEL_VERSION"
echo "  Output  : $IMG_FILE"
echo ""

# ── Debos args ─────────────────────────────────────────────────────────────
export PATH=/sbin:/usr/sbin:$PATH

DEBOS_ARGS="--disable-fakemachine --scratchsize=10G"

DEBOS_VARS=(
    -t "device:${DEVICE}"
    -t "suite:${SUITE}"
    -t "image:${IMG_FILE}"
    -t "rootfs:${ROOTFS_FILE}"
    -t "fw_archive:${FW_ARCHIVE_URL}"
    -t "kernel_image:${KERNEL_IMAGE_URL}"
    -t "kernel_headers:${KERNEL_HEADERS_URL}"
    -t "kernel_version:${KERNEL_VERSION}"
    -t "device_ui:${DEVICE_UI}"
)

cd "$SCRIPT_DIR"

if [ "$IMAGE_ONLY" -eq 0 ]; then
    echo "── Stage 1: rootfs ──"
    # shellcheck disable=SC2086
    debos $DEBOS_ARGS "${DEBOS_VARS[@]}" rootfs.yaml || exit 1
fi

echo "── Stage 2: image ──"
# shellcheck disable=SC2086
debos $DEBOS_ARGS "${DEBOS_VARS[@]}" image.yaml

echo ""
echo "Build complete: $IMG_FILE"
echo "Flashable rootfs: root-$IMG_FILE"

# ── Extract boot.img from rootfs ────────────────────────────────────────────
BOOT_IMG="boot-${IMG_FILE}"
ROOT_IMG="root-${IMG_FILE}"
MOUNT_TMP="$(mktemp -d)"

echo "── Extracting boot.img ──"
if [ -f "$ROOT_IMG" ]; then
    mount -o loop,ro "$ROOT_IMG" "$MOUNT_TMP" &&     cp "$MOUNT_TMP/boot/boot.img" "$BOOT_IMG" &&     umount "$MOUNT_TMP" &&     echo "Boot image: $BOOT_IMG" ||     { umount "$MOUNT_TMP" 2>/dev/null; echo "WARN: boot.img extraction failed"; }
else
    echo "WARN: $ROOT_IMG not found — skipping boot.img extraction"
fi
rmdir "$MOUNT_TMP" 2>/dev/null || true
