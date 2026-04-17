# Mobuntu — Changelog

> RC7–RC13 assisted by Claude Sonnet 4.6 (Anthropic)
> Previous builds were assisted by Gemini 3.1 Pro (up to RC6)
> Substantial progress and direction thanks to **arkadin91** — Ubuntu 26.04 Beta reference image, Kupfer lead, sdm845-mainline firmware discovery, OTA script logic, WirePlumber tuning config, and pmaports device file discovery.

---

## RC13 (Current)

**Branding**
- Project renamed from Mobuntu Orange to Mobuntu
- All scripts updated to reflect new name

**Build Color System**
- Script 1 prompts for a build color after Ubuntu release selection
- 10 colors available with channel recommendations in brackets: orange (24.04 stable), pink (26.04 stable), yellow (edge/beta)
- Custom color option saves to build.env without conflict checking
- Hostname auto-generated as `mobuntu-{color}` from selected color
- `BUILD_COLOR` written to build.env alongside hostname

**Panel Selection**
- Panel (Tianma/EBBG) now selected in script 1 and saved to build.env as `BOOT_PANEL` and `BOOT_DTB_SELECTED`
- Script 5 reads from build.env instead of re-prompting

**QEMU Path Fix (Ubuntu 26.04 host)**
- `qemu-user-static` package renamed in 26.04 — replaced with `qemu-user-binfmt-hwe`
- Static binary path changed from `/usr/bin/qemu-aarch64-static` to `/usr/bin/qemu-aarch64`
- Scripts 3 and 4 updated accordingly

**Kernel Version Picker**
- Script 2 now lists all available kernel series from Mobian pool when no pin is set
- Displays available series with suggested `KERNEL_VERSION_PIN` value for each
- Auto-selects latest if no input given
- All index fetches switched from curl to wget for WSL2 host compatibility

**Audio Stack (Critical Fix)**
- `hexagonrpcd` removed, then re-added with correct systemd ordering drop-ins
- Root cause of ADSP 60s watchdog crash identified as service startup race
- `alsa-state` and `alsa-restore` masked — conflict with SDM845 audio subsystem
- `51-qcom.conf` WirePlumber ALSA tuning config sourced from `firmware/{brand}-{codename}/` folder
- pmaports beryllium device files added: `hexagonrpcd.confd`, `q6voiced.conf`, `81-libssc.rules`
- Script 3 fetches pmaports files from upstream if not present locally, saves source URL to log
- `hexagonrpcd` service ordering: `qrtr-ns` -> `rmtfs` -> `pd-mapper` -> `hexagonrpcd`

**qcom-firmware Initramfs Hook**
- `qcom-firmware` initramfs hook sourced from `firmware/{brand}-{codename}/qcom-firmware`
- Falls back to project root if device-specific file not found
- Bakes ADSP/CDSP/GPU firmware directly into initramfs for early boot availability
- Hook install inside chroot is conditional — skips cleanly if not staged

**Service Ordering**
- systemd drop-in configs generated for `pd-mapper`, `rmtfs`, `hexagonrpcd`
- All drop-ins use `printf` instead of heredocs to avoid CHROOT_EOF conflicts

**Ubuntu Desktop Minimal Easter Egg**
- When Ubuntu Desktop Minimal is selected, GNOME accent color is set to match `BUILD_COLOR`
- Unmounted volumes hidden in Nautilus via dconf override
- Written to `/etc/dconf/db/local.d/01-mobuntu-theme`

**Squeekboard**
- `phosh-osk-stub` and `lomiri-osk-stub` replaced with `squeekboard` (available in Ubuntu repos)
- Phosh no longer uses `-t staging` flag — pulled directly from Ubuntu repos

**Display Manager Fix**
- Stale `display-manager.service` symlink removed before DM enable to prevent conflicts

**Auto-resize on First Boot**
- Script 5 prompts to enable first-boot auto-resize alongside verbosity selection
- Installs `mobuntu-resize.service` — one-shot, runs `resize2fs` using `DEVICE_IMAGE_LABEL`
- Creates `/etc/mobuntu-resize-pending` flag, deleted after successful resize
- Device reboots automatically after resize completes

**Watchdog / Auto Build (Partially Implemented)**
- `watchdog.sh` added — runs scripts 2 -> 3 -> verify -> 5 unattended
- Hidden ZWJ (U+200D) signal character appended to success messages in scripts 2, 3, and verifier
- Watchdog detects signal to confirm clean exit at each stage
- Auto-sudo toggle with explicit risk warning — recommended for WSL2/VM use only
- Auto-sudo is currently broken and may remain so by design
- Output images tagged with `_autobuild` suffix on success
- Timestamped log written per run

**Build Verification**
- `verify_build.sh` added — standalone cross-check of build.env vs rootfs
- Checks: build.env completeness, device config, hostname, packages, services, ordering drop-ins, WirePlumber config, kernel, firmware, ALSA masking, initramfs hook, autoresize service, build color
- Package checks use direct dpkg status file reads — no chroot required, works from x86 host
- Hidden ZWJ signal on pass for watchdog integration

**Developer Masterkit**
- `mobuntu-developer-masterkit.py` added — Python curses TUI
- Regedit-style split layout: left pane file tree, right pane content/menu
- Sections: Device Config, APT, Kernel, Services, Audio, Verifier Generator, Staged Changes
- Staged changes reviewed and applied in one shot
- Generates device-specific verifier scripts
- Generates `51-qcom.conf` WirePlumber configs with custom ALSA parameters
- Generates systemd drop-in ordering configs
- ESC returns to main menu, ESC x2 prompts quit
- Requires `dialog` and Python 3 (installed by script 1 preflight)

**Firmware Folder Structure**
- Device-specific files now live in `firmware/{brand}-{codename}/`
- Includes: `firmware.tar.gz`, `qcom-firmware`, `51-qcom.conf`, `hexagonrpcd.confd`, `q6voiced.conf`, `81-libssc.rules`
- Script 3 looks in firmware folder first, falls back to curl from upstream sources

**Noble (24.04) Support — Reverted**
- `hexagonrpcd` removal briefly made 24.04 viable again
- Subsequently confirmed that `hexagonrpcd` is required for reliable audio
- Noble support reverted — will be formally sunset in RC14 as `hexagonrpcd` 0.4.0 is not available in noble repos

**Version Markers**
- RC version added to header comments across all scripts

---

## RC12

**Audio Investigation**
- Ran diagnostic script against arkadin91's reference image (Ubuntu 26.04, April 9 build)
- Confirmed identical kernel (6.18.20-1), same firmware — difference is userspace only
- Key finding: reference image has no `hexagonrpcd` — audio works via WirePlumber ALSA tuning alone
- `51-qcom.conf` sourced from arkadin91's image: S16LE, 48000Hz, period-size 4096, period-num 6, headroom 512
- `hexagonrpcd` confirmed as cause of ADSP 60s watchdog crash on warm boot — removed
- `alsa-state` and `alsa-restore` identified as conflicting services
- ADSP fastrpc missing `memory-region` in DTB confirmed via `/sys/firmware/fdt` inspection — cosmetic warning only, not root cause

**Kernel Unpinning**
- `KERNEL_VERSION_PIN` can now be commented out in device config
- Script 2 falls back to latest available series when pin is absent

**Ubuntu 26.04 Host Support**
- QEMU package and binary path updated for 26.04 host environment
- `whiptail` and `dialog` added to preflight host dependencies

---

## RC11

**ARM64 Host Support**
- Script 1 detects host architecture via `uname -m` at startup
- ARM64 hosts skip QEMU entirely — `qemu-user-static` and `binfmt-support` not installed
- x86-64 hosts retain full QEMU + binfmt path
- Script 3 debootstrap branches by host arch: ARM64 uses single-stage, x86-64 uses foreign + QEMU second stage
- Script 3 chroot execution branches by host arch: ARM64 uses direct chroot, x86-64 uses QEMU static binary
- Script 4 updated with same arch-aware logic — WSL2 block x86-only, chroot entry direct on ARM64
- `HOST_ARCH` and `HOST_IS_ARM64` written to build.env

**Kernel Version Pinning**
- Added `KERNEL_VERSION_PIN` field to device configs
- Script 2 checks for pin before scraping repo — if set, fetches exact version directly
- If pinned version not found, build fails with list of available versions
- Empty pin falls back to latest
- Beryllium configs pinned to `6.18.20`

**Ubuntu 26.04 Codename Fix**
- Corrected `devel` to `resolute` — the actual Ubuntu 26.04 codename served by mirrors
- Debootstrap symlink auto-created at preflight: `resolute -> gutsy` if missing
- `resolute` set as default release selection
- 25.04 (plucky) removed from picker — EOL

**UI Package Pinning**
- All UI installs use `-t` to pin to correct apt source
- Phosh/phrog/greetd pinned to `-t staging`
- Ubuntu UI packages pinned to `-t "$UBUNTU_RELEASE"`

**Chroot Script Rewrite**
- Split two-stage heredoc: `INJECT_EOF` writes host variables into chroot script header, single-quoted `CHROOT_EOF` appends all build logic
- Eliminates `cat: '': No such file or directory` errors from unescaped variable expansion
- Fixes script loop regression where script 3 would restart from the top after completing

**Bug Fixes**
- binfmt hex magic bytes corrupted by Python string replacement — fixed using binary file patching and `printf | sudo tee` pattern
- Script 4 rewritten directly with `cat << 'ENDOFFILE'` after corruption
- Single-quote parse error in binfmt registration string fixed

---

## RC10.2

**Audio — PipeWire Restored**
- Reverted from PulseAudio back to PipeWire — confirmed working with proper UCM2 maps
- RC10.1 used PulseAudio following postmarketOS recommendation, but PipeWire works correctly once UCM2 maps are present
- `pipewire pipewire-pulse wireplumber` restored to base system install in script 3

**UCM2 Maps**
- `alsa-ucm-conf` installed pinned to Ubuntu release (`apt-get install -t ${UBUNTU_RELEASE} alsa-ucm-conf`)
- UCM2 maps harvested from arkadin91's reference image and bundled into `firmware.tar.gz`
- Firmware archive re-applied post-apt so UCM maps always win over package manager

**Hardware Status (Confirmed)**
- Touch, Sound (speaker + headphones), WiFi, Bluetooth all working
- Modem disabled — causes WiFi and BT to crash when active, under investigation

---

## RC10.1

**UI Picker**
- Script 1 prompts for desktop environment: Phosh, Ubuntu Desktop Minimal, Unity, Plasma Desktop, Plasma Mobile, Lomiri
- Each UI sets correct display manager automatically
- Lomiri shows explicit warning and y/N confirmation before proceeding
- UI selection flows into build.env

**Firmware Archive System**
- Added `firmware/<brand>-<codename>/` directory structure
- Script 3 checks for local `firmware.tar.gz` before attempting git clone
- Three-tier priority: local archive -> git clone -> OnePlus 6 fallback

**Ubuntu 26.04 Support**
- `devel` (26.04) added to release picker with experimental warning
- `quill` (26.04 stable) added as disabled placeholder

**Bug Fixes**
- `/boot/efi` fstab entry removed — fake UUID was causing systemd to drop to emergency mode on every boot
- `cp: cannot overwrite non-directory` fixed when staging firmware
- `curl: command not found` in chroot fixed — curl now installed before Mobian repo is added
- Nested heredoc `cat: '': No such file or directory` errors fixed with printf
- Chroot step ordering fixed — Ubuntu sources and curl installed before Mobian GPG key fetch

---

## RC10

**Device Config System**
- Introduced `devices/*.conf` device profile system — all scripts source device config via `build.env`
- Added device configs: `xiaomi-beryllium-tianma`, `xiaomi-beryllium-ebbg`, `oneplus-enchilada`, `oneplus-fajita`
- Device configs carry all mkbootimg parameters, firmware method, kernel method, services, quirks, hostname, image label
- Adding a new device requires only a new `.conf` file — no script changes needed

**Firmware**
- Replaced droid-juicer with direct `git clone` of `gitlab.com/sdm845-mainline/firmware-xiaomi-beryllium`
- Full firmware bundle: all beryllium signed blobs + ath10k WiFi board file + TAS2559 audio amp + ACDB calibration + DSP userspace libs + sensor configs
- Added OnePlus 6 fallback with clear warning naming the source directory
- OnePlus 6/6T use `apt` firmware method

**Boot Method Abstraction**
- Script 5 branches on `BOOT_METHOD`: `mkbootimg` implemented, `uboot` and `uefi` as placeholders
- `BOOT_DTB_APPEND`, `BOOT_PANEL_PICKER`, all mkbootimg offsets driven from device config

**Script 1 Auto-Run**
- After saving `build.env`, script 1 optionally chains directly into scripts 2 and 3

**Script Renumbering**
- Finalised 5-script pipeline: old `4,5,6` -> new `3,4,5`

---

## RC9

**Phrog / greetd**
- Replaced GDM3 with `greetd` + `phrog` — login screen is native Phosh lockscreen, touch-friendly
- `greeter` user created, `/etc/greetd/config.toml` written pointing to phrog

**Kernel Hook (OTA-safe boot.img)** *(logic credit: arkadin91)*
- Installed `/etc/kernel/postinst.d/zz-qcom-bootimg` — rebuilds boot.img automatically after every kernel update
- Installed `/etc/initramfs/post-update.d/bootimg` — fires after `update-initramfs`
- `/etc/kernel/cmdline` written by script 5 with real UUID
- `/etc/kernel/boot_dtb` written by script 5 so hook picks correct panel DTB
- Hook filters on `*sdm845*` kernel version to avoid generic Ubuntu kernel

**qbootctl**
- Installed in rootfs — enables OTA-style slot updates without fastboot

---

## RC8

**Build System**
- `build.env` used to pass config between scripts
- osm0sis mkbootimg fork confirmed required — Ubuntu package broken (GKI module error)
- `sed -i 's/-Werror//g'` fix applied to libmincrypt Makefile

**Kernel**
- Dynamic fetch from `repo.mobian.org` pool for latest `linux-image-*-sdm845` and headers
- DTB confirmed appended to kernel binary (not `--dtb` flag) — required for SDM845 bootloader
- DTB filenames confirmed: `sdm845-xiaomi-beryllium-tianma.dtb` / `-ebbg.dtb`

**RootFS**
- debootstrap two-stage build with QEMU arm64 binfmt
- WSL2 binfmt injection fallback
- `fallocate` + `mkfs.ext4` + loop mount + `rsync -aHAXx` image build

**Bug Fixes**
- `return 1` inside fetch functions killed script with `set -e` — fixed with explicit `return 0`
- `basename: missing operand` — fixed with `for f in /boot/vmlinuz-*sdm845*` loop
- binfmt not active on WSL2 — added manual hex registration fallback
- `Exec format error` in chroot — fixed with `qemu-aarch64-static` explicit invocation
