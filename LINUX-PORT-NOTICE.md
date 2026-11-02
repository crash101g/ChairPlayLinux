# Unofficial Linux port — notice

## This is not my project

**ChairPlay** — wireless Apple CarPlay on a PC — is written and maintained by **Oleksiy Strelets
([NumberOneBot](https://github.com/NumberOneBot))**: <https://github.com/NumberOneBot/ChairPlay>. It is a Windows app.

ChairPlay is itself built on [DiPlay](https://github.com/shihabal3amri/DiPlay) by shihabal3amri, which is based on
[xcertplay](https://github.com/shilapi/xcertplay) by shilapi; the HUD's 3D maneuvers are LuKa's, from
[mib2q-carplay-rgi](https://github.com/luka-dev/mib2q-carplay-rgi). All the credit for the CarPlay receiver, its
protocol work, UI, and design belongs to them.

This repository is an **unofficial port of ChairPlay 1.0.4 to Ubuntu-family Linux (Ubuntu 22.04+, Pop!_OS, Linux Mint,
Debian 12+ and others), made for my own personal use**
and shared as-is. It is:

- **not affiliated with, reviewed, or endorsed by** the ChairPlay author, DiPlay, xcertplay, LuKa, or Apple;
- **not supported**: please do **not** open issues about it on the original ChairPlay project;
- tested on one machine only (Pop!_OS with the COSMIC desktop, an Intel PC, one iPhone); other Ubuntu-family systems
  are expected to work but untested.

CarPlay, iPhone, AirPlay and Siri are trademarks of Apple Inc. **No Apple MFi identity is included**, as in the
original; you need your own `identity.pk8` and `certificate.p7b`.

## License

GPL-3.0, the same as the original ([LICENSE](LICENSE)). The original notices are kept in
[THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES) and `notices/`. As GPL-3.0 section 5(a) asks, every file this port added
or modified carries a banner at its top saying so, with the date (2026-11-02).

## What the port changes

The Windows code paths are kept; Linux implementations are chosen at runtime (`Host.isWindows`), and the Gradle build
picks the Linux natives and packaging when it runs on Linux.

| Area | Windows (original) | Linux (this port) |
| --- | --- | --- |
| Bluetooth | BluetoothApis, Winsock RFCOMM | BlueZ: devices via `busctl`, or `bluetoothctl`; RFCOMM channel from the iPhone's SDP records; kernel RFCOMM socket (`linux/LinuxBluetooth.kt`, `linux/Sdp.kt`, `linux/LibC.kt`) |
| Wi-Fi / Ethernet | Native Wifi API, PowerShell | NetworkManager (`nmcli`) or `iw`; `/sys/class/net` (`linux/LinuxNetwork.kt`) |
| TCP keep-alive | Winsock options | Linux socket options incl. `TCP_USER_TIMEOUT` (`TcpLiveness.kt`) |
| Firewall | Windows Firewall via PowerShell/netsh | ufw or firewalld state, hint, and `pkexec` rule add (`linux/LinuxFirewall.kt`) |
| Settings UI | WebView2 inside the window | A browser app window (`linux/LinuxBrowser.kt`) |
| Window hooks | Win32 title bar, close source, drag | Skipped; GLFW on X11/XWayland (`media/NativeWindow.kt`, `RunWindows.kt`) |
| Hardware decoding | D3D11VA | VA-API where available (`D3D11Video.kt`) |
| Theme, OS name, error box, links | Registry, MessageBox, explorer.exe | XDG portal/gsettings/COSMIC, os-release, zenity, xdg-open (`linux/LinuxDesktop.kt`, `UiPlaces.kt`) |
| App data | Next to the exe | `~/.local/share/ChairPlay` (`DesktopConfig.kt`, `Main.kt`) |
| Native libraries | zig → `.dll` | gcc → `.so`; GL loader via GLX/EGL (`gradle/native.gradle.kts`, `native/maneuver/chairplay/gl_core.*`) |
| Build & package | jpackage app image, Inno Setup | jpackage `.deb` (`gradle/linux-package.gradle.kts`, `scripts/build-linux.sh`), system check (`scripts/check-linux.sh`) |
| Web UI texts | Windows wording | Linux wording when the page runs on Linux (`webui/src/platform.ts` and a few pages) |

Tests: Linux tests were added under `src/test/kotlin/.../linux/`, and a few Windows-only tests now skip on Linux.

Build and install instructions: [docs/LINUX.md](docs/LINUX.md).
