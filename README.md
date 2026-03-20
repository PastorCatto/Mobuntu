
# Ubuntu Desktop for POCO F1 (Beryllium)

This project provides a modular, automated build suite to deploy a clean, bloat-free Ubuntu Linux OS directly onto the POCO F1 (Snapdragon 845). 

Rather than relying on pre-compiled, opaque system images, this toolset empowers you to build the operating system entirely from scratch on your own machine. It utilizes a "Partition Hijack" method to run a mainline Linux kernel natively on the Android bootloader, without requiring risky re-partitioning of the internal UFS storage.

---

### Project Philosophy & AI Transparency
1. **Human-Readable Code:** The bash scripts in this suite were generated with the assistance of Gemini Pro (Feb-Mar 2026) for rapid automation. However, I will **never** use AI to generate compiled binaries, executables, or obfuscated code. Everything in this repo is standard, auditable bash to ensure zero backdoors or hidden exploits. 
2. **The War on Bloat:** Standard Linux desktop metapackages pull in gigabytes of server tools, printer drivers, and background services that will destroy a smartphone's battery and RAM. This script suite specifically targets **minimal session packages** and enforces an aggressive `--no-install-recommends` diet.
3. **Credits & Upstream:** Kernel and boot image generation is powered by the incredible **pmbootstrap** from the postmarketOS team. Firmware blobs are sourced natively from **Mobian/Debian** and **Qualcomm**. All upstream code falls under their respective open-source licenses; this project is licensed under GPL 2.0.

---

### System Requirements

| Requirement | Specification |
| :--- | :--- |
| **Host OS** | Ubuntu 24.04.1 LTS (Native PC, Container, or WSL) |
| **Disk Space** | 50GB minimum (The script generates multiple 8GB+ Ext4 and Sparse images) |
| **Target Device** | POCO F1 (Beryllium) |
| **Firmware Source** | Mobian Weekly SDM845 (For SSH hardware harvesting) or local archive |

---

### Available OS Flavors
When generating your workspace, you can choose from the following highly optimized environments. The script dynamically configures the correct Display Manager (`gdm3`, `sddm`, or `lightdm`) in the background so you never boot to a black screen.

**Mobile Shells (Touch-First, Wayland Native)**
* **Phosh:** Purism's mobile GNOME shell. Includes Squeekboard for virtual typing.
* **Plasma Mobile:** KDE's mobile interface. Includes Maliit for virtual typing.

**Desktop Flavors (Tablet/PC Experience)**
* **GNOME Minimal:** The bare-metal GNOME shell and Nautilus file manager. 
* **KDE Plasma Minimal:** The pure KWin desktop, Dolphin, and Konsole.
* **Ubuntu Unity Minimal:** The classic left-dock interface without the traditional Ubuntu bloatware.
* **XFCE Minimal:** The ultimate lightweight fallback. Fast, CPU-rendered, and rock-solid.

*(Note: All desktop flavors automatically install the `onboard` virtual keyboard so you can log in without requiring a USB-C OTG hub).*

---

### Installation & Build Guide

#### Step 1: Host Preparation & Firmware Harvesting
1. Prepare your Ubuntu 24.04 host environment.
2. **Harvest the Firmware:** Mainline Linux requires proprietary Qualcomm blobs (Audio UCMs, Modem rules) to talk to the SDM845 hardware. 
   * Flash [Mobian Weekly](https://images.mobian.org/qcom/weekly/) to your POCO F1.
   * Boot the phone, connect to Wi-Fi, and enable the SSH server: `sudo apt update && sudo apt install openssh-server`
   * *Note: The default Mobian password is `1234`.*
3. Copy the master `deploy_workspace.sh` script from this repo into an empty folder on your PC and run it.

#### Step 2: Critical pmbootstrap Quirks
When the script triggers `pmbootstrap init`, you must navigate these two upstream quirks:
* **The Display Bug:** Kernels newer than 6.14 currently suffer from a DSI panel initialization failure (resulting in a blank screen). When asked for a channel, you **MUST** select **v25.06**. This forces a stable kernel branch that properly initializes the Tianma and EBBG screens.
* **The Ghost Password:** During the install phase, pmbootstrap will ask for a user password. Enter any value; it is a placeholder that our Ubuntu chroot completely ignores.

#### Step 3: Script Execution Flow
Run the generated scripts in this exact order.

1. **1_preflight.sh**: Installs host dependencies (qemu, debootstrap) and creates your `build.env` configuration.
2. **2_pmos_setup.sh**: Compiles the mainline kernel via pmbootstrap, injects UFS timing fixes (`rootdelay=5`), and clones the hardware UUIDs.
3. **3_firmware_fetcher.sh**: SSHs into your live Mobian phone, bundles the `/usr/share/alsa` and `/lib/firmware` directories, and pulls them to your host PC.
4. **4_the_transplant.sh**: The heavy lifter. Builds a clean Ubuntu ARM64 root filesystem, injects the kernel and firmware, and installs your chosen UI using strict anti-bloat parameters.
5. **5_enter_chroot.sh**: *(Optional)* Safely mounts host pipes (`/proc`, `/run`, `/sys`) and drops you into the OS as root for manual tweaking.
6. **6_seal_rootfs.sh**: Packs the raw folder into flashable Android sparse images.
7. **7_kernel_menuconfig.sh**: *(Optional)* Opens the Linux kernel configuration menu for advanced hardware hacking.

---

### Flashing & Deployment

Because the postmarketOS initramfs scans for specific UUIDs rather than physical disk locations, you have two ways to deploy the generated images.

#### Image Mapping & Targets
| File Name | Type | Target Partition | Deployment Method |
| :--- | :--- | :--- | :--- |
| **pmos_boot.img** | Android Boot | Internal `/boot` | **Mandatory** (Hardware Trigger) |
| **ubuntu_boot_sparse.img** | Sparse Ext4 | Internal `/system` | Internal Storage Hijack |
| **ubuntu_root_sparse.img** | Sparse Ext4 | Internal `/userdata` | Internal Storage Hijack |
| **ubuntu_boot.img** | Raw Ext4 | MicroSD Part 1 | Safe Dual-Boot Testing |
| **ubuntu_root.img** | Raw Ext4 | MicroSD Part 2 | Safe Dual-Boot Testing |

#### Method A: Internal Storage (The Hijack)
This wipes Android completely. Boot the POCO F1 into Fastboot mode (Power + Volume Down) and execute:
```bash
fastboot flash boot pmos_boot.img
fastboot flash system ubuntu_boot_sparse.img
fastboot flash userdata ubuntu_root_sparse.img
fastboot reboot
```
**CRITICAL:** After sending the reboot command, do NOT touch the power button. The initial boot sequence requires several minutes to expand the filesystem and initialize the Display Manager. Just wait.

#### Method B: MicroSD Card
Use `fdisk` or `gparted` to create two partitions on a MicroSD card. Use `dd` to flash the **Raw** (`.img`) files to the SD card. You must still flash the `pmos_boot.img` to the phone's internal `/boot` partition via Fastboot to trigger the kernel.

---

### Advanced Technical Details
For a deeper dive into how the dual-UUID partition spoofing works, why we use the ABL boot wrapper, and how to debug the kernel initramfs via USB serial, please refer to the `Engineering-Report.md` document.
