# ChairPlay on Linux — installation and setup guide (unofficial port)

> **This is an unofficial, personal-use port of [ChairPlay](https://github.com/NumberOneBot/ChairPlay) by Oleksiy
> Strelets to Ubuntu-family Linux. It is not part of, reviewed, or supported by the original project. Please don't
> report Linux problems there.** See [LINUX-PORT-NOTICE.md](../LINUX-PORT-NOTICE.md).

ChairPlay turns a Linux PC into a wireless Apple CarPlay screen: your iPhone connects to it the way it connects to a
car, and you drive CarPlay with the keyboard and mouse. Sound and the microphone go through the PC.

**Contents:** [Supported systems](#supported-systems) · [What you need](#what-you-need) ·
[Quick start](#quick-start) · [1. Get the source](#1-get-the-source) · [2. Build and install](#2-build-and-install) ·
[3. Pair the iPhone](#3-pair-the-iphone) · [4. Add the MFi files](#4-add-the-mfi-files) ·
[5. Check the PC](#5-check-the-pc) · [6. First connection](#6-first-connection) · [Everyday use](#everyday-use) ·
[Firewall](#firewall) · [Settings and files](#settings-and-files) · [Update or uninstall](#update-or-uninstall) ·
[Troubleshooting](#troubleshooting) · [How it differs from Windows](#how-it-differs-from-windows) ·
[Building by hand](#building-by-hand)

---

## Supported systems

Any **64-bit (amd64 or arm64)** distribution that uses `apt` and is based on **Ubuntu 22.04 or later** or **Debian 12
or later**, with a graphical desktop:

| Family | Examples |
| --- | --- |
| Ubuntu 22.04, 24.04 and later | Ubuntu (GNOME), Kubuntu (KDE), Xubuntu (Xfce), Ubuntu MATE, Lubuntu (LXQt), Ubuntu Budgie |
| Built on Ubuntu | Pop!_OS 22.04/24.04 (GNOME or COSMIC), Linux Mint 21/22 (Cinnamon, MATE, Xfce), Zorin OS 17, elementary OS 7/8, KDE neon |
| Debian | Debian 12 and 13, and distributions built on them |

Wayland and X11 sessions both work: on Wayland the CarPlay window runs through XWayland, which every desktop above
includes. **Tested** on Pop!_OS with COSMIC on an Intel PC; the rest is expected to work but untested, so
[check the PC](#5-check-the-pc) first.

## What you need

- **An iPhone** with CarPlay, and Bluetooth and Wi-Fi on.
- **Bluetooth on the PC** that does classic Bluetooth (BR/EDR), not only Bluetooth Low Energy. Built-in laptop
  Bluetooth and ordinary USB adapters do.
- **The PC and the iPhone on one router.** The PC on Wi-Fi or Ethernet; the iPhone on that router's Wi-Fi. The
  router must let its devices see each other (guest networks usually don't), and use **WPA2** (or WPA2/WPA3 mixed),
  or no password. WPA3-only networks don't work.
- **Apple MFi identity files**, `identity.pk8` and `certificate.p7b`. Neither ChairPlay nor this port includes them,
  and CarPlay will not start without them.
- **About 2 GB free** and an internet connection for the first build (it downloads Gradle, Java libraries and
  JavaScript packages). After that the app needs no internet.

## Quick start

For those who know their way around a terminal:

```
git clone --recursive <this repository's URL> ChairPlay && cd ChairPlay
scripts/build-linux.sh --install           # builds the .deb and installs it (asks for sudo)
mkdir -p ~/.local/share/ChairPlay/offline-mfi
cp /path/to/identity.pk8 /path/to/certificate.p7b ~/.local/share/ChairPlay/offline-mfi/
scripts/check-linux.sh                     # says what is still missing
```

Pair the iPhone in Settings › Bluetooth, open **ChairPlay** from the applications menu, enter your Wi-Fi password,
and press **Connect**. The sections below go through each step.

---

## 1. Get the source

Install git if it isn't there, and clone the repository **with `--recursive`**: the CarPlay core (DiPlay) is a git
submodule.

```
sudo apt install git
cd ~
git clone --recursive <this repository's URL> ChairPlay
cd ChairPlay
```

Already cloned without `--recursive`? Run `git submodule update --init` inside the folder. (The build script does this
for you too.) Downloaded a `.tar.gz` of the repository instead? Unpack it and `cd` into it:
`tar xzf ChairPlay-*.tar.gz && cd ChairPlay*`.

## 2. Build and install

```
scripts/build-linux.sh --install
```

The first run takes 5–15 minutes. The script:

1. checks that this is an Ubuntu- or Debian-family system;
2. installs the build tools with apt: `build-essential libgl-dev fakeroot git curl xz-utils ca-certificates python3`
   and `openjdk-17-jdk` (on a release without OpenJDK 17, such as Debian 13, it downloads Eclipse Temurin 17 into
   `~/.cache/chairplay` instead, checked against its published SHA-256);
3. uses your Node.js if it is 22.22+ or 24.15+, otherwise downloads Node.js 24 into `~/.cache/chairplay` (checked
   against nodejs.org's SHA-256 list) — used only to build the settings page, not shipped;
4. runs Gradle, which downloads the Java libraries, builds the native parts with gcc, builds the settings page, and
   packages everything with jpackage into `build/jpackage/chairplay_<version>-1_<arch>.deb`;
5. with `--install`, installs that `.deb` with apt.

The package installs into `/opt/chairplay`, adds **ChairPlay** to the applications menu, and depends on `bluez` and
`xdg-utils`. It bundles its own Java runtime, so nothing else is needed to run it.

Other options: `--test` runs the test suite first (from a desktop session), `--clean` starts from scratch,
`--version 1.0.4` stamps a version number, `--no-deps` fails instead of installing packages.

> **Build on the PC that will run ChairPlay** (or one with the same release). The `.deb` lists the library versions of
> the system it was built on; a package built on Ubuntu 24.04 will not install on 22.04, for example.

To install a `.deb` you built earlier without `--install`:

```
sudo apt install ./build/jpackage/chairplay_*.deb
```

The note `N: Download is performed unsandboxed as root…` that apt prints is harmless.

## 3. Pair the iPhone

Pair the iPhone with the PC once, as you would headphones. On the iPhone, open **Settings › Bluetooth** and keep that
screen open while you pair.

| Desktop | Where |
| --- | --- |
| GNOME (Ubuntu, Pop!_OS 22.04, Zorin) | Settings › Bluetooth, pick the iPhone |
| COSMIC (Pop!_OS 24.04) | Settings › Bluetooth |
| KDE (Kubuntu, KDE neon) | System Settings › Bluetooth › Pair device |
| Cinnamon, MATE, Xfce, LXQt (Mint, Xubuntu, Lubuntu) | the Bluetooth tray icon (Blueman) › Setup new device |

Confirm that the codes match on both. If the desktop has no Bluetooth tool, use the terminal:

```
bluetoothctl
  power on
  scan on            # wait for the iPhone to appear, note its address (XX:XX:XX:XX:XX:XX)
  pair XX:XX:XX:XX:XX:XX
  trust XX:XX:XX:XX:XX:XX
  quit
```

Check with `bluetoothctl devices Paired` (on Ubuntu 22.04: `bluetoothctl paired-devices`). If several phones are
paired, tell ChairPlay which one, after its first start:
`echo 'iphone.address=XX:XX:XX:XX:XX:XX' >> ~/.local/share/ChairPlay/settings.properties`.

## 4. Add the MFi files

ChairPlay keeps everything in `~/.local/share/ChairPlay`, made private to your user (mode 700). Put the two MFi files
in its `offline-mfi` folder:

```
mkdir -p ~/.local/share/ChairPlay/offline-mfi
cp /path/to/identity.pk8 /path/to/certificate.p7b ~/.local/share/ChairPlay/offline-mfi/
```

Don't know where they are? `find ~ /media -name identity.pk8 -o -name certificate.p7b 2>/dev/null`. The Get started
screen's **Open folder** button opens this folder too.

## 5. Check the PC

```
scripts/check-linux.sh
```

It only reads (no sudo, no changes) and reports, with the fix for each problem: the session type, whether ChairPlay
is installed, the MFi files, the Bluetooth service, adapter and paired phone, the Wi-Fi and LAN address, the firewall,
the sound server, OpenGL, and the browser the settings will open in. Fix any **✘**, then go on.

Then try the Bluetooth part on its own, with the iPhone **unlocked and nearby**:

```
/opt/chairplay/bin/ChairPlay probe-bt
cat "$(ls -t ~/.local/share/ChairPlay/logs/*.log | head -1)"
```

The log should end with `iAP2 identification accepted`, `iAP2 authentication accepted` and `GO: the iPhone accepted
ChairPlay over Linux Bluetooth`. This checks Bluetooth and the MFi files without starting CarPlay.

## 6. First connection

1. Open **ChairPlay** from the applications menu (or run `/opt/chairplay/bin/ChairPlay`). Two windows open: the black
   **CarPlay window**, and the **settings** in a browser window (an app window if Chrome, Chromium, Brave, Edge or
   Vivaldi is installed, otherwise a tab in your default browser).
2. The **Get started** screen checks the MFi files, the firewall, the paired iPhone, and the network.
3. Enter the **Wi-Fi network name and password** of the router the iPhone uses. They are sent to the iPhone over Bluetooth so it can find the PC; they stay on this PC otherwise.
4. Unlock the iPhone, keep it nearby, and press **Connect**.
5. On the iPhone, accept the CarPlay prompt. CarPlay appears in the CarPlay window.

From then on, start ChairPlay with the iPhone unlocked and nearby, and CarPlay comes up by itself (`ui.autoconnect`,
on by default).

---

## Everyday use

| Key | Does |
| --- | --- |
| Mouse | touch |
| Arrows | move the focus (tilt the knob) |
| Tab / Shift+Tab, mouse wheel | turn the knob |
| Enter, Space | select |
| Backspace, Esc | back |
| Home | home screen |
| S | Siri |
| F | full screen on or off |
| F3 / F4 | answer / end a call |
| F7 / F8 / F9 | previous track / play or pause / next track |
| Ctrl+Up / Ctrl+Down / Ctrl+M | ChairPlay's volume up / down / mute |
| Ctrl+R | reconnect |
| F12 | screenshot (into `~/.local/share/ChairPlay/screenshots`) |

The original README lists every shortcut. Calls sound best with headphones. Close ChairPlay with its window; if it is
killed, the iPhone may ignore new connections until the iPhone restarts.

**Start ChairPlay at login:** `mkdir -p ~/.config/autostart && cp /opt/chairplay/lib/chairplay-ChairPlay.desktop ~/.config/autostart/`
(remove that file to stop).

**Use the settings again:** they stay open in the browser window; if you closed it, the log
(`~/.local/share/ChairPlay/logs/chairplay-*.log`, line `UI at http://127.0.0.1:…`) has its address, or restart ChairPlay.

## Firewall

Ubuntu and its derivatives ship **ufw switched off**, so usually there is nothing to do. If you turned on ufw (or use
firewalld), ChairPlay cannot read its rules without root and shows a firewall step: press **Add the rule** (the system
asks for your password), or run the commands it shows. For ufw they are:

```
sudo ufw allow proto tcp to any port 7000:7010 comment 'ChairPlay CarPlay inbound'
sudo ufw allow proto tcp to any port 32768:60999 comment 'ChairPlay CarPlay inbound'
sudo ufw allow proto udp to any port 32768:60999 comment 'ChairPlay CarPlay inbound'
sudo ufw allow 5353/udp comment 'ChairPlay CarPlay inbound'
```

(32768:60999 is the kernel's port range, `cat /proc/sys/net/ipv4/ip_local_port_range`.) To remove them later:
`sudo ufw status numbered`, then `sudo ufw delete <number>` for each ChairPlay rule.

## Settings and files

| Path | What |
| --- | --- |
| `/opt/chairplay` | the program (read-only) |
| `~/.local/share/ChairPlay/settings.properties` | the settings (the settings screen edits them; you can too, with ChairPlay closed) |
| `~/.local/share/ChairPlay/offline-mfi/` | the MFi files |
| `~/.local/share/ChairPlay/logs/` | logs; set **Log level** to **Info** on the Experiments page for detail |
| `~/.local/share/ChairPlay/screenshots/` | F12 screenshots |
| `~/.local/share/ChairPlay/identity.properties`, `pairings/` | ChairPlay's own AirPlay keys and the iPhone's pairing |

Useful settings: `wifi.ssid`, `wifi.passphrase`, `wifi.security` (`WPA_WPA2`, `WPA3_TRANSITION`, `NONE`…),
`iphone.address`, `ui.autoconnect`.

Environment variables, for special cases (set them before starting ChairPlay from a terminal):

| Variable | Effect |
| --- | --- |
| `CHAIRPLAY_HOME=/path` | keep the data in another folder |
| `CHAIRPLAY_BROWSER="firefox --new-window"` | the browser command for the settings |
| `CHAIRPLAY_GLFW_PLATFORM=wayland` | native Wayland windows instead of XWayland (windows can't place themselves) |
| `CHAIRPLAY_RFCOMM_CHANNEL=<n>` | force the iPhone's iAP2 Bluetooth channel (diagnosis only) |

Terminal commands: `/opt/chairplay/bin/ChairPlay devices` lists the Bluetooth adapter, paired devices and sound
devices into `logs/devices-*.log`; `/opt/chairplay/bin/ChairPlay probe-bt` checks Bluetooth and MFi.

## Update or uninstall

**Update** to a newer version of this port:

```
cd ~/ChairPlay
git pull --recurse-submodules
scripts/build-linux.sh --install
```

Your settings, pairings and MFi files in `~/.local/share/ChairPlay` are kept.

**Uninstall:**

```
sudo apt remove chairplay
rm -rf ~/.local/share/ChairPlay        # only if you also want the settings, keys, MFi files and logs gone
rm -f ~/.config/autostart/chairplay-ChairPlay.desktop
rm -rf ~/.cache/chairplay              # the Node.js/JDK the build downloaded, if any
```

Also remove the iPhone's pairing in Settings › Bluetooth, and on the iPhone, Settings › General › CarPlay › ChairPlay ›
Forget This Car.

## Troubleshooting

Start with `scripts/check-linux.sh`, then the newest log in `~/.local/share/ChairPlay/logs/` (Log level **Info** on the
Experiments page for detail).

| Symptom | Cause and fix |
| --- | --- |
| Build: `No node on the PATH` | Run the build through `scripts/build-linux.sh`, which provides Node.js. |
| Build: `Could not resolve…` or download errors | The first build needs the internet (Maven Central, services.gradle.org, registry.npmjs.org). Check proxies and retry. |
| Build: `no jpackage` | `sudo apt install openjdk-17-jdk` (the full JDK, not only the JRE). |
| `apt install` of the `.deb` fails on dependencies | It was built on another release; build it on this PC. |
| `Cannot read BlueZ's devices` | `sudo systemctl enable --now bluetooth`; check `bluetoothctl show` lists a controller with `Powered: yes`; `rfkill unblock bluetooth`. |
| `No paired phone` | Pair it (step 3). If several phones are paired, set `iphone.address`. |
| `RFCOMM connect … failed` / `offers no RFCOMM channel` | Unlock the iPhone and keep it near; turn its Bluetooth off and on; remove and re-pair it on both sides. |
| `MFi files not found` | Step 4; the names must be exactly `identity.pk8` and `certificate.p7b`. |
| Bluetooth works, but CarPlay never appears | The iPhone can't reach the PC on the network: same router and Wi-Fi as the PC? not a guest network? correct Wi-Fi password and security (`wifi.security`)? firewall (see above)? |
| The settings page doesn't open | Install Chrome or Chromium, or set `CHAIRPLAY_BROWSER`; the address is in the log (`UI at …`). |
| No sound | `scripts/check-linux.sh` › Sound: install `pipewire-alsa` (PipeWire, the default since Ubuntu 22.10) or `libasound2-plugins` (PulseAudio); choose the device on the Audio page. |
| Picture stutters | Set **Decoding** to Software, or choose H264, on the CarPlay page. |
| Black or no window | OpenGL 3.3 is needed: `sudo apt install mesa-utils && glxinfo -B`. |
| The iPhone ignores new connections | ChairPlay was killed mid-session: restart the iPhone. |

## How it differs from Windows

| | Windows | Linux (this port) |
| --- | --- | --- |
| Settings screen | Inside the CarPlay window (WebView2) | Its own browser window |
| Car button on the CarPlay home screen | Shows the settings over CarPlay | Minimizes the CarPlay window; the settings stay in the browser |
| On-screen car controls window | The controls page | Empty (no embedded web view); every keyboard control works |
| Bluetooth | BluetoothApis and Winsock | BlueZ (D-Bus or bluetoothctl), SDP lookup of the iAP2 channel, kernel RFCOMM socket |
| Wi-Fi | Native Wifi API | NetworkManager (`nmcli`), or `iw` |
| Firewall | Windows Firewall rule | ufw or firewalld, usually off |
| Hardware video decoding | D3D11VA | VA-API where available (Intel, AMD), otherwise the CPU |
| Theme "System" | Windows' app theme | The desktop's dark-style preference |
| "Use my location" on the GPS page | Windows location | Not offered: type the coordinates |
| Installer | `setup.exe` | `.deb`, data in `~/.local/share/ChairPlay` |

## Building by hand

`scripts/build-linux.sh` is a convenience; the build itself is Gradle. With JDK 17, Node.js 22.22+/24.15+, gcc/g++,
`libgl-dev` and `fakeroot` installed:

```
./gradlew linuxDeb          # build/jpackage/chairplay_*.deb (also builds the app image)
./gradlew jpackageImage     # only the app image: build/jpackage/ChairPlay/bin/ChairPlay
./gradlew runImage          # build and start the app image without installing
./gradlew test              # JVM and web UI tests (needs a desktop session for the window tests)
./gradlew installDist       # plain distribution: build/install/chairplay/bin/chairplay run
```

`gradle/linux-package.gradle.kts` holds the Linux packaging; `gradle/native.gradle.kts` the gcc builds of the echo
canceller and the HUD renderer; the Windows build paths are unchanged.
