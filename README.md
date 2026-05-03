# Mobuntu

Multi-platform Ubuntu image builder. Each variant targets a different device
family and produces a different output format. All variants share the same
`devkit.py` auto-runner at the repo root.

Built on top of [arkadin91/mobuntu-recipes](https://github.com/arkadin91/mobuntu-recipes)
for the SDM845 target. L4T and PS4 variants are standalone pipelines.

---

## Variants

| Variant | Target | Output | Status |
|---------|--------|--------|--------|
| `Mobuntu/` | SDM845 phones (Poco F1, OnePlus 6/6T) | flashable `.img` | Active |
| `Mobuntu-L4T/` | Nintendo Switch (Tegra X1) | hekate `.7z` | Scaffold |
| `Mobuntu-PS4/` | PlayStation 4 (FW 12.52, CUH-12xx) | dd-able `.img` | Scaffold |
| `Mobuntu-PDK/` | Ubuntu Phone PDK | TBD | Planned |

Releases are color-coded. See CHANGELOG.md for per-release details.

---

## Devkit

Run from the repo root. Auto-detects all variants present:

```bash
python3 devkit.py               # curses TUI
python3 devkit.py --list        # headless variant summary
python3 devkit.py --build Mobuntu-L4T   # headless build
```

---

## Mobuntu — SDM845

Multi-device Ubuntu ARM64 image builder for SDM845 phones.

### Requirements

- Ubuntu 24.04 host (**do not use 26.04** — QEMU arm64 chroot regression)
- `debos` installed
- Network access during build (firmware + kernel fetched at build time)

### Usage

```sh
# Build for Xiaomi Poco F1 (beryllium) — confirmed working baseline
./Mobuntu/build.sh -d beryllium

# Build for OnePlus 6T (fajita)
./Mobuntu/build.sh -d fajita

# Skip rootfs stage, reuse existing tarball
./Mobuntu/build.sh -d beryllium -i

# Override suite
./Mobuntu/build.sh -d fajita -s plucky

# List available devices
./Mobuntu/build.sh -h
```

### Device Support

| Codename | Device | Suite | Status |
|----------|--------|-------|--------|
| beryllium | Xiaomi Poco F1 | plucky | Confirmed working |
| fajita | OnePlus 6T | resolute | Suite warning |
| enchilada | OnePlus 6 | — | Stubbed |

### Suite Notes

- **plucky (25.04)** — recommended for all SDM845 devices
- **resolute (26.04)** — known regressions: WiFi, Bluetooth, audio on SDM845; build.sh requires double confirmation

### Structure

```
Mobuntu/
├── build.sh                    # Entry point — loads device.conf, calls debos
├── rootfs.yaml                 # Stage 1: debootstrap + base packages
├── image.yaml                  # Stage 2: overlays, firmware, final config
├── packages/
├── overlays/
├── scripts/
│   ├── fetch-firmware.sh
│   ├── final.sh
│   ├── setup-user.sh
│   └── update-apt.sh
├── files/                      # Firmware debs + GNOME extensions
└── devices/
    ├── beryllium/
    │   ├── device.conf
    │   └── overlays/
    ├── fajita/
    │   ├── device.conf
    │   └── overlays/
    └── enchilada/
        ├── device.conf
        └── overlays/
```

---

## Mobuntu-L4T — Nintendo Switch

See `Mobuntu-L4T/README.md` for full documentation.

```bash
cd Mobuntu-L4T && sudo ./build.sh
```

Output: `output/mobuntu-l4t-noble-dev.7z` — extract to SD FAT32, then hekate `Flash Linux`.
Requires hekate >= 6.0.6.

---

## Mobuntu-PS4 — PlayStation 4

See `Mobuntu-PS4/README.md` for full documentation.

```bash
cd Mobuntu-PS4 && sudo ./build.sh
```

Output: `output/mobuntu-ps4-noble-dev.img` — dd directly to USB drive.
Targets FW 12.52 (GoldHen), CUH-12xx hardware.

```bash
sudo dd if=output/mobuntu-ps4-noble-dev.img of=/dev/sdX bs=4M status=progress conv=fsync
```
