<p align="center">
  <img src="packaging/icon/chairplay-1024.png" alt="ChairPlay app icon" width="128" height="128">
</p>

<h1 align="center">ChairPlay Linux</h1>

<p align="center"><strong>Wireless Apple CarPlay on your Linux PC.</strong><br>
An unofficial port of <a href="https://github.com/NumberOneBot/ChairPlay">ChairPlay</a> to Ubuntu, Pop!_OS, Linux Mint, Debian and their relatives.</p>

<p align="center">
  <img src="https://img.shields.io/badge/Linux-Ubuntu%20%2F%20Debian%20family%20%C2%B7%20amd64%20%2F%20arm64-E95420?style=flat" alt="Linux: Ubuntu / Debian family · amd64 / arm64">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=flat" alt="License: GPL-3.0"></a>
  <a href="https://github.com/NumberOneBot/ChairPlay"><img src="https://img.shields.io/badge/Original-NumberOneBot%2FChairPlay-555?style=flat" alt="Original project: NumberOneBot/ChairPlay"></a>
</p>

<p align="center">
  <a href="https://github.com/crash101g/ChairPlayLinux/releases/latest"><img src="https://img.shields.io/badge/Download%20.deb-E95420?style=for-the-badge" alt="Download .deb"></a>
</p>

> [!IMPORTANT]
> **This is not the original ChairPlay.** ChairPlay is written and maintained by Oleksiy Strelets
> ([NumberOneBot](https://github.com/NumberOneBot)) as a Windows app: **https://github.com/NumberOneBot/ChairPlay**.
> This repository is a personal, unofficial port to Linux. It is not affiliated with or endorsed by the ChairPlay author,
> DiPlay, xcertplay or Apple. **Please don't report Linux problems to the original project.**

![ChairPlay: the CarPlay home screen in the ChairPlay window](docs/images/chairplay.jpg)

<sub>Screenshot from the original Windows app. On Linux it looks the same, except that the settings open in a browser window.</sub>

---

## What is ChairPlay?

[ChairPlay](https://github.com/NumberOneBot/ChairPlay) makes your PC act as a **wireless CarPlay car head unit**. Your
iPhone connects to the PC over Bluetooth and Wi-Fi just as it would to a car. CarPlay then runs in a window on your
desktop: you can listen to music, use navigation, take calls and talk to Siri through the PC's speakers and microphone,
and control everything with the mouse and keyboard.

It's useful for building and testing CarPlay apps and navigation without a car, for demos, or just for CarPlay on a desk.

Highlights of the original app (all of them work in this port):

- CarPlay in a resizable window, with full screen; the mouse works as touch
- Keyboard controls that act like a car's knob and buttons, plus typing into CarPlay text fields
- Sound and microphone on the devices you choose, with ChairPlay's own volume control
- **AltScreen** (experimental): a second CarPlay screen like a car's instrument cluster, with a map, turn card and HUD
- **GPS spoofing** (experimental): the iPhone navigates by a virtual car that you drive with the keyboard or a gamepad

For the full feature list, release notes, and the Windows version, **visit the original project:
https://github.com/NumberOneBot/ChairPlay**. If you find ChairPlay useful, consider
[supporting its author](https://www.buymeacoffee.com/alexstrelets).

## What this Linux port does

The original app only supports Windows. This port replaces the Windows-only parts with Linux equivalents and packages
the app as a `.deb`:

| Area | Windows (original) | Linux (this port) |
| --- | --- | --- |
| Bluetooth | Windows Bluetooth APIs | BlueZ (D-Bus / `bluetoothctl`) with a kernel RFCOMM socket |
| Wi-Fi | Native Wifi API | NetworkManager (`nmcli`) or `iw` |
| Firewall | Windows Firewall | ufw or firewalld (usually off on Ubuntu) |
| Hardware video decoding | D3D11VA | VA-API (Intel, AMD), otherwise the CPU |
| Settings screen | Inside the CarPlay window (WebView2) | A separate browser window (an app window in Chrome or Chromium) |
| Installer | `setup.exe` | `.deb` installed to `/opt/chairplay`; data in `~/.local/share/ChairPlay` |
| Build tools | — | `scripts/build-linux.sh` (one-step build and install) and `scripts/check-linux.sh` (system check) |

The full list of changes is in [LINUX-PORT-NOTICE.md](LINUX-PORT-NOTICE.md).

**Tested** on Pop!_OS 24.04 (COSMIC) on an Intel PC. Other Ubuntu- and Debian-family systems should work but haven't been tested.

---

## Requirements

- **A 64-bit Linux PC** (amd64 or arm64) running **Ubuntu 22.04+**, **Debian 12+**, or a distribution built on
  them: Pop!_OS, Linux Mint, Kubuntu, Xubuntu, Zorin OS, elementary OS, KDE neon, and others. It needs a graphical
  desktop; Wayland and X11 both work.
- **Bluetooth** with classic Bluetooth (BR/EDR) support, not only Bluetooth Low Energy. Most built-in and USB adapters have it.
- **An iPhone** with CarPlay.
- **One router for the PC and the iPhone.** The PC can be on Wi-Fi or Ethernet, and the iPhone on that router's Wi-Fi.
  The network must let its devices see each other, which guest networks usually block. It must use **WPA2** or no
  password, because WPA3-only networks don't work.
- **Apple MFi identity files** (`identity.pk8` and `certificate.p7b`). **They are not included** in this project or in
  the original, and CarPlay won't start without them.
- About 2 GB free disk space and internet access for the first build.

## Install

There are two ways to install: download the ready-made `.deb`, or build it from source.

### Option A: Download the .deb (quickest)

For **64-bit Intel/AMD PCs** running **Ubuntu 24.04 or later**, or a system built on it (Pop!_OS 24.04, Linux Mint 22,
Kubuntu/Xubuntu 24.04, Zorin OS 18, elementary OS 8, and others):

1. Download `chairplay_<version>-1_amd64.deb` from the
   **[latest release](https://github.com/crash101g/ChairPlayLinux/releases/latest)**.
2. Install it:

   ```bash
   sudo apt install ./chairplay_*_amd64.deb
   ```

3. Continue with [Set up](#set-up-both-options): add your MFi files, pair your iPhone and check the PC.

On **Ubuntu 22.04, Linux Mint 21, Debian 12, or an arm64 PC**, the downloaded package won't install. Use Option B instead.

### Option B: Build from source

This works on every supported system. One script builds the app and installs it.

**1. Get the source.** The CarPlay core (DiPlay) is a git submodule, so clone with `--recursive`:

```bash
sudo apt install git
git clone --recursive https://github.com/crash101g/ChairPlayLinux.git ChairPlay
cd ChairPlay
```

**2. Build and install.** The first run takes about 5–15 minutes:

```bash
scripts/build-linux.sh --install
```

The script installs the build tools with apt (it asks for your password). It downloads JDK 17 or Node.js only if your
system lacks a suitable version, and checks each download against its published checksum. It then builds
`build/jpackage/chairplay_<version>-1_<arch>.deb` and installs it. The app bundles its own Java runtime and adds
**ChairPlay** to your applications menu.

### Set up (both options)

**3. Add your MFi files:**

```bash
mkdir -p ~/.local/share/ChairPlay/offline-mfi
cp /path/to/identity.pk8 /path/to/certificate.p7b ~/.local/share/ChairPlay/offline-mfi/
```

**4. Pair your iPhone** with the PC once, as you would headphones. Use your desktop's Bluetooth settings, or run
`bluetoothctl` and then `pair` and `trust` the iPhone.

**5. Check the PC:**

```bash
scripts/check-linux.sh
```

If you installed the downloaded `.deb`, you don't have the scripts folder. Fetch the script first:
`curl -fsSLO https://raw.githubusercontent.com/crash101g/ChairPlayLinux/linux-port/scripts/check-linux.sh && bash check-linux.sh`.

This script only reads your system and changes nothing. It checks Bluetooth, the paired iPhone, the MFi files, the
network, the firewall, sound and OpenGL, and tells you how to fix each **✘** it finds.

## Use

1. Open **ChairPlay** from the applications menu, or run `/opt/chairplay/bin/ChairPlay`. Two windows open: the
   **CarPlay window** and the **settings** in a browser window.
2. On the **Get started** screen, enter the **Wi-Fi name and password** of the router your iPhone uses. ChairPlay
   sends them to the iPhone over Bluetooth so the iPhone can find the PC.
3. Unlock the iPhone, keep it nearby, and press **Connect**. Accept the CarPlay prompt on the iPhone.
4. CarPlay appears in the CarPlay window. After the first connection, CarPlay starts by itself whenever you open
   ChairPlay with the iPhone unlocked and nearby.

### Controls

| Key | Does |
| --- | --- |
| Mouse | Touch |
| Arrow keys | Move the focus (tilt the knob) |
| Tab / Shift+Tab, mouse wheel | Turn the knob |
| Enter, Space | Select |
| Backspace, Esc | Back |
| Home | Home screen |
| S | Siri |
| F | Full screen on or off |
| F3 / F4 | Answer / end a call |
| F7 / F8 / F9 | Previous track / play or pause / next track |
| Ctrl+Up / Ctrl+Down / Ctrl+M | Volume up / down / mute |
| Ctrl+R | Reconnect |
| F12 | Screenshot (saved to `~/.local/share/ChairPlay/screenshots`) |

### Tips

- **Close ChairPlay with its window.** If you kill it mid-session, the iPhone may ignore new connections until you restart the iPhone.
- Calls sound best with headphones.
- **Start ChairPlay at login:** `mkdir -p ~/.config/autostart && cp /opt/chairplay/lib/chairplay-ChairPlay.desktop ~/.config/autostart/`
- If you closed the settings window, restart ChairPlay. The settings address is also in the log, on the line starting `UI at http://127.0.0.1:…`.
- If **ufw** or **firewalld** is on, the Get started screen offers to add the needed rules for you.

### Update

**If you installed the downloaded .deb:** download the newer `.deb` from
[Releases](https://github.com/crash101g/ChairPlayLinux/releases/latest) and install it with
`sudo apt install ./chairplay_*_amd64.deb`.

**If you built from source:**

```bash
cd ~/ChairPlay
git pull --recurse-submodules
scripts/build-linux.sh --install
```

Your settings, pairing and MFi files in `~/.local/share/ChairPlay` are kept.

### Uninstall

```bash
sudo apt remove chairplay
rm -rf ~/.local/share/ChairPlay   # optional: also delete settings, keys, MFi files and logs
```

## Troubleshooting

First run `scripts/check-linux.sh`, then look at the newest log in `~/.local/share/ChairPlay/logs/`. For more detail,
set **Log level** to **Info** on the Experiments page.

| Problem | Fix |
| --- | --- |
| `Cannot read BlueZ's devices` | `sudo systemctl enable --now bluetooth` and `rfkill unblock bluetooth` |
| `No paired phone` | Pair the iPhone (Install step 4) |
| `MFi files not found` | Put `identity.pk8` and `certificate.p7b` in `~/.local/share/ChairPlay/offline-mfi/` |
| Bluetooth works, but CarPlay never appears | Check that the iPhone and the PC are on the same router, the network isn't a guest network, the Wi-Fi password is correct, and the firewall allows ChairPlay |
| No sound | Install `pipewire-alsa` (PipeWire) or `libasound2-plugins` (PulseAudio) |
| The `.deb` won't install on another PC | Build it on the PC that will run it |

**The full installation, setup and troubleshooting guide is in [docs/LINUX.md](docs/LINUX.md).**

---

## Credits and license

- **[ChairPlay](https://github.com/NumberOneBot/ChairPlay)** by Oleksiy Strelets (NumberOneBot) wrote everything in
  this app; this port only adds the Linux parts.
- ChairPlay is built on **[DiPlay](https://github.com/shihabal3amri/DiPlay)** by shihabal3amri, which is based on
  **[xcertplay](https://github.com/shilapi/xcertplay)** by shilapi.
- The HUD's 3D maneuvers are from LuKa's [mib2q-carplay-rgi](https://github.com/luka-dev/mib2q-carplay-rgi).
  The notices for bundled libraries are in [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES).

Licensed under **GPL-3.0**, like the original ([LICENSE](LICENSE)).

CarPlay, iPhone, AirPlay and Siri are trademarks of Apple Inc. This project is not affiliated with Apple. No Apple MFi
identity is stored in this repository.
