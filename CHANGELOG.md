# Mobuntu Orange — Changelog

> RC7–RC10.1 assisted by Claude Sonnet 4.6 (Anthropic)
> Substantial progress and direction thanks to **arkadin91** — Ubuntu 26.04 Beta reference image, Kupfer lead, sdm845-mainline firmware discovery, and OTA script logic.

---

## RC10.1 (Latest)
**UI Picker**
- Script 1 now prompts for desktop environment: Phosh, Ubuntu Desktop Minimal, Unity, Plasma Desktop, Plasma Mobile, Lomiri
- Each UI sets the correct display manager automatically — greetd+phrog for Phosh/Lomiri, GDM3 for GNOME, LightDM for Unity, SDDM for Plasma
- Lomiri shows an explicit warning and y/N confirmation before proceeding
- UI selection flows into `build.env` as `UI_NAME`, `UI_PACKAGES`, `UI_DM`, `UI_EXTRA_REPOS`
- Script 3 chroot install section is now UI-aware, branches on `UI_NAME`

**Firmware Archive System**
- Added `firmware/<brand>-<codename>/` directory structure
- Script 3 now checks for a local `firmware.tar.gz` before attempting git clone
- Three-tier priority: local archive → git clone → OnePlus 6 fallback
- Each firmware directory ships with a `README.txt` containing harvest instructions
- Confirmed working: WiFi and GPU on Poco F1 using harvested firmware archive

**Ubuntu 26.04 Support**
- Added `devel` (26.04) to release picker with experimental warning + y/N confirm
- Added `quill` (26.04 stable) as a disabled placeholder — falls back to noble
- Trivial to enable when 26.04 officially releases

**Bug Fixes**
- Removed `/boot/efi` fstab entry — fake UUID `EEFB-EEFB` was causing systemd to drop to emergency mode on every boot
- Fixed `cp: cannot overwrite non-directory` when staging firmware — changed `cp -r dir` to `cp -r dir/.`
- Fixed `curl: command not found` in chroot — curl now installed in a dedicated bootstrap step before Mobian repo is added
- Fixed nested heredoc `cat: '': No such file or directory` errors — replaced inner `cat > file << EOF` blocks with `printf` for greetd config and `/etc/hosts`
- Fixed chroot step ordering — Ubuntu sources written and curl installed before Mobian GPG key fetch
- Renumbered all chroot steps to reflect new ordering

---

## RC10
**Device Config System**
- Introduced `devices/*.conf` device profile system — all scripts source device config via `build.env`
- Added device configs: `xiaomi-beryllium-tianma`, `xiaomi-beryllium-ebbg`, `oneplus-enchilada`, `oneplus-fajita`
- Device configs carry all mkbootimg parameters, firmware method, kernel method, services, quirks, hostname, image label
- Adding a new device requires only a new `.conf` file — no script changes needed

**Firmware**
- Replaced droid-juicer with direct `git clone` of `gitlab.com/sdm845-mainline/firmware-xiaomi-beryllium`
- Full firmware bundle confirmed: all beryllium signed blobs + ath10k WiFi board file + TAS2559 audio amp + ACDB audio calibration + DSP userspace libs + sensor configs
- Added OnePlus 6 fallback: if git clone fails, copies `sdm845/oneplus6/` blobs from host `linux-firmware` into `sdm845/beryllium/` with a clear warning naming the source directory
- OnePlus 6/6T use `apt` firmware method (blobs ship in upstream `linux-firmware`)

**Boot Method Abstraction**
- Script 5 branches on `BOOT_METHOD`: `mkbootimg` (fully implemented), `uboot` (placeholder), `uefi` (placeholder)
- `BOOT_DTB_APPEND`, `BOOT_PANEL_PICKER`, all mkbootimg offsets driven from device config

**Script 1 Auto-Run**
- After saving `build.env`, script 1 optionally chains directly into scripts 2 and 3

**Script Renumbering**
- Finalised 5-script pipeline: old `4,5,6` → new `3,4,5`

---

## RC9
**Phrog / greetd**
- Replaced GDM3 with `greetd` + `phrog` — login screen is native Phosh lockscreen, touch-friendly
- No more broken GNOME UI state on first boot
- Confirmed Phrog is the official greeter for Mobian/beryllium (FOSDEM 2025 demo was a Poco F1)
- `greeter` user created, `/etc/greetd/config.toml` written pointing to phrog

**Kernel Hook (OTA-safe boot.img)** *(logic credit: arkadin91)*
- Installed `/etc/kernel/postinst.d/zz-qcom-bootimg` — rebuilds `boot.img` automatically after every `apt upgrade` that updates the kernel
- Installed `/etc/initramfs/post-update.d/bootimg` — same trigger after `update-initramfs`
- `/etc/kernel/cmdline` written by script 5 with real UUID so hook works correctly
- `/etc/kernel/boot_dtb` written by script 5 so hook picks the correct panel DTB
- Hook filters on `*sdm845*` kernel version to avoid accidentally grabbing the generic Ubuntu kernel

**qbootctl**
- Installed in rootfs — enables OTA-style slot updates without fastboot after first flash

**Firmware Investigation (arkadin91 reference image)**
- Reverse-engineered a working Ubuntu 26.04 ARM64 beryllium image (pre-first-boot)
- Confirmed GPU, WiFi, BT worked before droid-juicer ran
- Discovered `sdm845/beryllium/` blobs were manually staged — not tracked by dpkg
- Confirmed `sdm845/a630_zap.mbn` ships in `linux-firmware` apt; beryllium-specific signed blobs do not
- Confirmed OnePlus 6 firmware file list is structurally identical to beryllium (different binaries, device-signed)
- Traced firmware source to `sdm845-mainline/firmware-xiaomi-beryllium` GitLab repo (pointed out by arkadin91 via Kupfer)

**fstab** *(later removed in RC10.1)*
- Added `/boot/efi vfat` stub entry — matched reference image layout, later found to cause emergency mode without the actual partition present

---

## RC8
**Build System**
- 7-script pipeline inherited; deprecated scripts identified and removed; renumbering planned
- `build.env` used to pass config between scripts
- osm0sis mkbootimg fork confirmed required (Ubuntu package broken — GKI module error)
- `sed -i 's/-Werror//g'` fix applied to libmincrypt Makefile

**Kernel**
- Dynamic fetch from `repo.mobian.org` pool for latest `linux-image-*-sdm845` and headers
- DTB confirmed appended to kernel binary (not `--dtb` flag) — required for SDM845 bootloader
- DTB filenames confirmed: `sdm845-xiaomi-beryllium-tianma.dtb` / `-ebbg.dtb`

**RootFS**
- debootstrap two-stage build with QEMU arm64 binfmt
- WSL2 binfmt injection fallback
- Nested heredoc bug fixed — chroot script written to mktemp file
- `apt-get: command not found` bug fixed — explicit second stage + PATH
- `mke2fs -d` broken — replaced with `fallocate` + `mkfs.ext4` + loop mount + `rsync -aHAXx`

**Firmware (original strategy)**
- `a630_sqe.fw`, `a630_gmu.bin` from `linux-firmware` apt
- `a630_zap.mbn` curled from kernel.org
- adsp/cdsp/mba/modem/venus/wlan via droid-juicer on first boot
- Mobian repo added for droid-juicer, qrtr-tools, rmtfs, tqftpserv, pd-mapper

**Bugs Fixed**
- `return 1` inside fetch functions killed script with `set -e` — fixed with explicit `return 0`
- `basename: missing operand` — fixed with `for f in /boot/vmlinuz-*sdm845*` loop
- binfmt not active on WSL2 — added manual hex registration fallback
- `Exec format error` in chroot — fixed with `qemu-aarch64-static` explicit invocation
