# Mobuntu — Developer Documentation
**Last updated: May 2, 2026**

---

## Repository Layout

```
PastorCatto/Mobuntu/
├── devkit.py                  # Multi-variant TUI auto-runner — run from here
├── sync.py                    # Upstream sync engine (SDM845)
├── CHANGELOG.md
├── DOCUMENTATION.md
├── README.md
│
├── Mobuntu/                   # SDM845 build root (arkadin91/mobuntu-recipes wrapper)
│   ├── build.sh
│   ├── image.yaml
│   ├── rootfs.yaml
│   ├── devices/
│   │   ├── beryllium/
│   │   ├── fajita/
│   │   └── enchilada/
│   ├── scripts/
│   ├── files/
│   ├── overlays/
│   └── packages/
│
├── Mobuntu-L4T/               # Nintendo Switch build root
│   ├── build.sh
│   ├── build.env
│   ├── scripts/               # 01-bootstrap through 05-package-hekate-7z
│   ├── overlays/switch/
│   ├── bootloader/ini/
│   └── assets/
│
├── Mobuntu-PS4/               # PlayStation 4 build root
│   ├── build.sh
│   ├── build.env
│   ├── scripts/               # 01-bootstrap through 05-package-output
│   ├── overlays/ps4/
│   └── kernel/
│
└── Mobuntu-PDK/               # Ubuntu PDK target (planned)
```

---

## devkit.py — Multi-Variant Auto-Runner

`devkit.py` lives at the repo root and auto-detects all Mobuntu variants
present in sibling directories. It provides a split-pane curses TUI and a
headless CLI for CI/scripted builds.

### Variant Detection

devkit.py walks up from the current directory to locate the repo root (first
ancestor containing `.git` or a recognized variant folder), then scans for:

| Folder | Variant |
|--------|---------|
| `Mobuntu/` | SDM845 phones |
| `Mobuntu-PDK/` | Ubuntu PDK |
| `Mobuntu-L4T/` | Nintendo Switch |
| `Mobuntu-PS4/` | PlayStation 4 |

Each variant is detected by the presence of both `build.env` and `build.sh`
inside its folder. The `build.env` is parsed to extract display metadata
(`UBUNTU_SUITE`, `FLAVOR`, `L4T_RELEASE`, `RELEASE_TAG`, etc.).

### Usage

```bash
python3 devkit.py               # full curses TUI
python3 devkit.py --list        # headless variant + parsed build.env summary
python3 devkit.py --build <variant_name>      # headless full pipeline
python3 devkit.py --build <variant_name> STAGES='04 05'  # headless staged build
```

### TUI Layout

```
+----------------------+-------------------------------------------+
|  [ Variants ]        |  [ Mobuntu-L4T  --  build output ]        |
|                      |                                            |
|  Mobuntu-L4T         |  [02 14:32:11] Cloning l4t-debs...        |
|    -> /path/...      |  [02 14:33:01] Staged 47 debs             |
|  Mobuntu-PS4         |  [03 14:33:02] Entering chroot            |
|    -> /path/...      |                                  [RUNNING] |
|                      |                                            |
|  [ Actions ]         |                                            |
|   b  Build (full)    |                                            |
|   s  Build stage...  |                                            |
|   c  Clean build/    |                                            |
|   e  Edit build.env  |                                            |
|   v  View build.env  |                                            |
|   k  Cancel build    |                                            |
|   r  Refresh         |                                            |
|   q  Quit            |                                            |
+----------------------+-------------------------------------------+
|  UP/DOWN: select  --  letter keys: action  --  q: quit           |
+------------------------------------------------------------------+
```

ASCII-only — no emoji. Regedit-style split: variants + actions on the left,
live build output streaming on the right.

### Keybindings

| Key | Action |
|-----|--------|
| Up / Down | Select variant |
| `b` | Full build (all stages, runs `sudo ./build.sh`) |
| `s` | Build specific stages (prompts: e.g. `04 05`) |
| `c` | Clean `build/` directory (requires typing `DELETE`) |
| `e` | Edit `build.env` in `$EDITOR` (TUI suspends, resumes after) |
| `v` | View `build.env` contents in right pane |
| `k` | Cancel running build (sends SIGTERM) |
| `r` | Refresh variant list from filesystem |
| `q` | Quit (prompts if a build is running) |

### build.env Parser

devkit.py parses shell-style `build.env` files. Handles:
- `FOO="bar"` — quoted string values
- `FOO="${FOO:-bar}"` — `${VAR:-default}` expansion (returns default)
- `export FOO=bar` — leading export keyword
- Inline comments stripped correctly

---

## Mobuntu — SDM845

### build.sh

Full multi-device build entrypoint. Requires root (re-execs via sudo automatically).

#### Usage

```bash
sudo bash Mobuntu/build.sh -d <device> [options]

sudo bash Mobuntu/build.sh -d beryllium
sudo bash Mobuntu/build.sh -d beryllium -s plucky
sudo bash Mobuntu/build.sh -d fajita -i
sudo bash Mobuntu/build.sh -h
```

#### Flags

| Flag | Description |
|------|-------------|
| `-d <device>` | Device codename — required |
| `-s <suite>` | Ubuntu suite override |
| `-i` | Image only — skip rootfs debootstrap |
| `-h` | Print usage and list available devices |

#### Suite Gate

If the resolved suite is `resolute`, build.sh requires double confirmation:

```
Type YES to confirm resolute: YES
Type RESOLUTE to confirm again: RESOLUTE
```

#### Build Stages

```
Stage 1: rootfs    debos rootfs.yaml  (debootstrap + packages)
Stage 2: image     debos image.yaml   (overlay + firmware + kernel + seal)
```

Output: `mobuntu-<device>-<YYYYMMDD>.img` and `root-mobuntu-<device>-<YYYYMMDD>.img`

---

### sync.py

Pulls latest upstream from arkadin91/mobuntu-recipes, extracts device vars, updates fork.

#### Usage

```bash
python3 sync.py
python3 sync.py --dry-run
python3 sync.py --extract-only
python3 sync.py --fork-dir PATH
```

#### Sync Stages

```
[ 1/4 ] Fetching upstream
[ 2/4 ] Extracting device vars
[ 3/4 ] Diffing upstream
[ 4/4 ] Applying updates
```

#### Pinned Files

Never overwritten by sync:

```
build.sh
image.yaml
rootfs.yaml
devices/
scripts/fetch-firmware.sh
overlays/etc/systemd/system/hexagonrpcd.service.d/
overlays/usr/share/dbus-1/
overlays/usr/share/polkit-1/
```

Add additional paths to `Mobuntu/.devkit-sync-lock` (one per line, `#` for comments).

---

### Device Configuration

#### device.conf Format

```bash
DEVICE_CODENAME="beryllium"
DEVICE_BRAND="xiaomi"
DEVICE_MODEL="Poco F1"
DEVICE_SOC="sdm845"

DEVICE_SUITE="plucky"

KERNEL_APT_NAME="linux-image-6.18-sdm845"
KERNEL_HEADERS_APT_NAME="linux-headers-6.18-sdm845"
KERNEL_VERSION="6.18-sdm845"
KERNEL_IMAGE_URL="https://..."
KERNEL_HEADERS_URL="https://..."

FW_DEB="linux-firmware-xiaomi-beryllium-sdm845.deb"
FW_ARCHIVE_URL="https://..."

ALSA_UCM_URL="https://repo.mobian.org/..."
DEVICE_MASKED_SERVICES="alsa-state alsa-restore"

DEVICE_DISPLAYS="tianma ebbg"
DEVICE_DEFAULT_DISPLAY="tianma"
DEVICE_DTB_TIANMA="sdm845-xiaomi-beryllium-tianma.dtb"
DEVICE_DTB_EBBG="sdm845-xiaomi-beryllium-ebbg.dtb"

DEVICE_PACKAGES="abootimg zstd hexagonrpcd libqrtr-glib0"
DEVICE_SERVICES="hexagonrpcd grow-rootfs"
HEXAGONRPCD_AFTER="multi-user.target"
```

#### Adding a New Device

1. Create `Mobuntu/devices/<codename>/device.conf`
2. Create `Mobuntu/devices/<codename>/overlays/`
3. Place firmware deb in `Mobuntu/files/`
4. Press `r` in devkit to refresh
5. Build with `sudo bash Mobuntu/build.sh -d <codename>`

---

### SDM845 Platform Notes

#### hexagonrpcd

Must use `After=multi-user.target`. **Do not use udev remoteproc gating** — causes a 60-second fastrpc thrash loop on SDM845. Drop-in lives at:

```
overlays/etc/systemd/system/hexagonrpcd.service.d/mobuntu-ordering.conf
```

```ini
[Unit]
After=multi-user.target
```

#### Audio

UCM2 maps from Mobian required. `alsa-state` and `alsa-restore` must be masked. Handled by `final.sh` via `ALSA_UCM_URL` from device.conf.

#### Suite Recommendations

| Suite | Ubuntu | SDM845 |
|-------|--------|--------|
| `plucky` | 25.04 | Recommended |
| `resolute` | 26.04 | Known WiFi/BT/audio regressions |

#### Build Host

Ubuntu 24.04 required. Ubuntu 26.04 host has a QEMU segfault regression with arm64 chroots.

---

### debos Notes

#### Variable Passthrough

Pass variables via `-t key:value` flags; expand in YAML with `{{ $varname }}`. The `environment:` block in debos `run` actions **only works with `command:`, not `script:`**. Use the overlay + command pattern:

```yaml
- action: overlay
  source: scripts
  destination: /usr/local/sbin/

- action: run
  chroot: true
  command: DEVICE="{{ $device }}" bash /usr/local/sbin/fetch-firmware.sh
```

Run debos with `--scratchsize=10G --disable-fakemachine` for WSL2 compatibility.

---

## Mobuntu-L4T — Nintendo Switch

### Overview

Targets Nintendo Switch (Tegra X1, T210/T210B01) via the switchroot L4T stack.
Output is a hekate-installable `.7z`. Requires hekate >= 6.0.6 at runtime.

Kernel is sourced as prebuilt `.deb`s from `theofficialgman/l4t-debs` — no kernel
compilation required. The kernel itself is CTCaer's `switch-l4t-kernel-4.9`
(`linux-5.1.2`), which is NVIDIA BSP 4.9-based (not mainline — required for Tegra X1).

Build host: Ubuntu 24.04 x86-64. arm64 target — uses `qemu-user-static`. Same
host detection logic as SDM845 (24.04: `qemu-user-static`, 26.04: `qemu-user-binfmt-hwe`).

### build.env Key Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `UBUNTU_SUITE` | `noble` | Ubuntu suite (noble = 24.04) |
| `FLAVOR` | `ubuntu-unity-desktop` | Desktop flavor |
| `L4T_RELEASE` | `5.1.2` | switchroot L4T release tracking |
| `IMAGE_SIZE_MIB` | `8192` | Raw ext4 image size |
| `SPLIT_SIZE_MIB` | `4092` | hekate chunk size |
| `DISTRO_LABEL` | `SWR-MOB` | FAT label for hekate `id=` |
| `RELEASE_TAG` | `dev` | Color tag / release identifier |

### Pipeline Stages

| Stage | Script | Purpose |
|------:|--------|---------|
| 01 | `bootstrap-rootfs.sh` | arm64 debootstrap (foreign mode + QEMU) |
| 02 | `fetch-l4t-debs.sh` | Clone `theofficialgman/l4t-debs`, stage into rootfs |
| 03 | `customize-rootfs.sh` | chroot: install flavor + L4T debs, overlays, user/locale |
| 04 | `make-rawimage.sh` | Raw ext4 image via `mke2fs -d` (WSL2-safe, no loop mount) |
| 05 | `package-hekate-7z.sh` | Split to `l4t.NN` chunks, write ini + branding, 7z |

Run subset with `STAGES='04 05' ./build.sh` to repackage without rebuilding rootfs.

### SD Card Layout (inside the 7z)

```
/
├── bootloader/ini/L4T-Mobuntu.ini
└── switchroot/
    ├── install/
    │   ├── l4t.00     (4092 MiB)
    │   ├── l4t.01
    │   └── ...
    └── mobuntu/
        ├── icon.bmp
        ├── bootlogo.bmp
        └── README_CONFIG.txt
```

### Hekate Boot Entry

```ini
[Mobuntu L4T]
l4t=1
boot_prefixes=/switchroot/mobuntu/
id=SWR-MOB
uart_port=0
r2p_action=self
icon=switchroot/mobuntu/icon.bmp
logopath=switchroot/mobuntu/bootlogo.bmp
```

Optional additions:
```ini
emmc=1                 # enable eMMC partition support
usb3_enable=1          # max USB speeds (degrades 2.4GHz BT/WiFi signal)
rootlabel_retries=100  # USB boot: retry rootdev 100 x 200ms = 20s
```

### Install Procedure

1. Format SD with hekate `Tools -> Partition SD Card` (leave >= 8 GiB FAT32)
2. Extract `.7z` to FAT32 root (use 7-Zip on Windows — Win11 built-in is broken)
3. Hekate `Tools -> Partition SD Card -> Flash Linux`
4. Hekate `Nyx Options -> Dump Joy-Con BT` (mandatory, even on Switch Lite)
5. Boot via `More Configs -> Mobuntu L4T`

### Upstream Sources

| Component | Source | Branch |
|-----------|--------|--------|
| Kernel | `CTCaer/switch-l4t-kernel-4.9` | `linux-5.1.2` |
| GPU module | `CTCaer/switch-l4t-kernel-nvidia` | `linux-5.1.2` |
| Platform/DTS | `CTCaer/switch-l4t-platform-t210-nx` | `linux-5.1.2` |
| Distro debs | `theofficialgman/l4t-debs` | `master` |

---

## Mobuntu-PS4 — PlayStation 4

### Overview

Targets PS4 on firmware 12.52 (GoldHen jailbreak). Focus hardware: CUH-12xx
(Belize/Aeolia southbridge) — the model family for which `feeRnt/ps4-linux-12xx`
has active WiFi/BT support (Marvell 8897).

Output is a raw MBR disk image — `dd` it directly to a USB drive. No special
packaging tool required.

**Build host: any Ubuntu 24.04 x86-64. No QEMU — host and target are both amd64.**

### build.env Key Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `UBUNTU_SUITE` | `noble` | Ubuntu suite |
| `ARCH` | `amd64` | Target architecture |
| `KERNEL_MODE` | `prebuilt` | `prebuilt` (GitHub release) or `source` (compile) |
| `KERNEL_REPO` | `feeRnt/ps4-linux-12xx` | GitHub repo for prebuilt fetch |
| `KERNEL_TAG` | `latest` | Release tag (`latest` resolves via GitHub API) |
| `BOOT_PART_SIZE_MIB` | `256` | FAT32 boot partition size |
| `ROOTFS_SIZE_MIB` | `8192` | ext4 rootfs partition size |
| `PS4_CMDLINE` | (see below) | Kernel cmdline passed to kexec payload |

Default kernel cmdline:
```
panic=0 clocksource=tsc consoleblank=0 net.ifnames=0 radeon.dpm=0 amdgpu.dpm=0
drm.debug=0 console=uart8250,mmio32,0xd0340000 console=ttyS0,115200n8
console=tty0 drm.edid_firmware=edid/1920x1080.bin
```

### Pipeline Stages

| Stage | Script | Purpose |
|------:|--------|---------|
| 01 | `bootstrap-rootfs.sh` | Native amd64 debootstrap — no QEMU |
| 02 | `pull-kernel.sh` | Fetch prebuilt bzImage from feeRnt releases, or build from source |
| 03 | `customize-rootfs.sh` | chroot: packages, GPU/WiFi/BT config, overlays, user |
| 04 | `make-rawimage.sh` | Raw ext4 image via `mke2fs -d` |
| 05 | `package-output.sh` | Assemble MBR disk image: FAT32 (boot) + ext4 (rootfs) |

### Kernel Pull Modes

**Prebuilt (default, KERNEL_MODE=prebuilt):**
Stage 02 hits the GitHub API to find the specified release tag (or `latest`),
downloads `bzImage` and `initramfs.cpio.gz` from the release assets. No
compiler toolchain required. Fast.

**Source (KERNEL_MODE=source):**
Clones `feeRnt/ps4-linux-12xx` and builds natively. Requires full kernel
toolchain (`build-essential`, `flex`, `bison`, `libssl-dev`, etc.). Takes
30-60 minutes. Place a custom `.config` at `kernel/ps4_defconfig` to override
the repo's default.

### USB Disk Layout

```
/dev/sdX
├── p1: FAT32 (256 MiB)
│   ├── bzImage
│   ├── initramfs.cpio.gz
│   └── cmdline.txt         (reference only — read by kexec payload config)
└── p2: ext4  (8 GiB, label: MOBU-PS4)
    └── Ubuntu Noble rootfs
```

### Boot Flow

1. Jailbreak PS4 with GoldHen (supports FW 12.52)
2. Launch Linux kexec payload from homebrew launcher
3. Payload reads `bzImage` + `initramfs.cpio.gz` from USB FAT32 (p1)
4. kexec into kernel; rootfs mounts from USB ext4 (p2, found by label `MOBU-PS4`)
5. Login: `mobuntu` / `mobuntu` — change on first boot

### Write to USB

```bash
# Verify device first — this WILL wipe /dev/sdX
sudo dd if=output/mobuntu-ps4-noble-dev.img of=/dev/sdX bs=4M status=progress conv=fsync
```

### Upstream Kernel

| Detail | Value |
|--------|-------|
| Repo | `feeRnt/ps4-linux-12xx` |
| Latest tested | 6.15.4 (March 2026) |
| WiFi/BT | Marvell 8897 (CUH-12xx, Torus 2.0 chipset) |
| HDMI | Fixed for CUH-12xx in 6.15.x |
| GPU | `amdgpu` driver (Liverpool/GCN) |

Kernel lineage: `fail0verflow/ps4-linux` → `codedwrench/ps4-linux` → `feeRnt/ps4-linux-12xx`.

### Known Gaps

- DualShock 4 input — `hid-playstation` likely covers it; verify with kernel config
- Bluetooth firmware blob — should come from `linux-firmware`; confirm `.hcd` file for your model
- HDMI may need `drm.edid_firmware` tuning per display
- PS4 Pro (CUH-7xxx) — different GPU, not targeted by `feeRnt/ps4-linux-12xx`
- Internal storage boot (`/data/linux/boot/`) — some payloads auto-copy on first boot; not wired yet
