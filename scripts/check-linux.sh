#!/usr/bin/env bash
# ====================================================================================================
#   UNOFFICIAL LINUX PORT -- THIS IS NOT THE ORIGINAL PROJECT
#
#   ChairPlay is written by Oleksiy Strelets (NumberOneBot), GPL-3.0:
#       https://github.com/NumberOneBot/ChairPlay
#   This file was ADDED by an unofficial port of ChairPlay to Ubuntu-family Linux, made for personal
#   use only. The port is not affiliated with or endorsed by the ChairPlay author, DiPlay, xcertplay,
#   or Apple. Please do not report problems with this port to the original project.
#   See LINUX-PORT-NOTICE.md.
# ====================================================================================================
#
# Checks this PC for what ChairPlay needs, and says what to fix. It only reads; it changes nothing and needs no sudo.
# Usage: scripts/check-linux.sh
set -uo pipefail

DATA="${CHAIRPLAY_HOME:-${XDG_DATA_HOME:-$HOME/.local/share}/ChairPlay}"
FAILS=0
WARNS=0
ok()   { printf '  \033[32m✔\033[0m %s\n' "$*"; }
bad()  { printf '  \033[31m✘\033[0m %s\n' "$*"; FAILS=$((FAILS + 1)); }
note() { printf '  \033[33m!\033[0m %s\n' "$*"; WARNS=$((WARNS + 1)); }
fix()  { printf '      → %s\n' "$*"; }
head_() { printf '\n\033[1m%s\033[0m\n' "$*"; }

head_ "System"
# shellcheck disable=SC1091
. /etc/os-release 2>/dev/null && ok "${PRETTY_NAME:-unknown}, $(uname -m), kernel $(uname -r)"
case "${XDG_SESSION_TYPE:-}" in
    wayland) if [ -n "${DISPLAY:-}" ]; then ok "Wayland session with XWayland (ChairPlay's window runs on XWayland)"
             else bad "Wayland session without XWayland"; fix "install xwayland, or log in to an X11 session"; fi ;;
    x11) ok "X11 session" ;;
    *) note "no graphical session detected (XDG_SESSION_TYPE=${XDG_SESSION_TYPE:-unset}); run this from the desktop" ;;
esac

head_ "ChairPlay"
if [ -x /opt/chairplay/bin/ChairPlay ]; then ok "installed: $(dpkg-query -W -f='${Package} ${Version}' chairplay 2>/dev/null || echo /opt/chairplay)"
else bad "not installed"; fix "scripts/build-linux.sh --install"; fi
if [ -d "$DATA" ]; then
    ok "data folder $DATA"
    [ "$(stat -c %a "$DATA")" = 700 ] || { note "data folder is not private (mode $(stat -c %a "$DATA"))"; fix "chmod 700 '$DATA'"; }
else note "no data folder yet; it is made when ChairPlay first starts"; fi
if [ -s "$DATA/offline-mfi/identity.pk8" ] && [ -s "$DATA/offline-mfi/certificate.p7b" ]; then ok "MFi files present"
else bad "MFi files missing: identity.pk8 and certificate.p7b"; fix "mkdir -p '$DATA/offline-mfi' && cp /path/to/identity.pk8 /path/to/certificate.p7b '$DATA/offline-mfi/'"; fi

head_ "Bluetooth"
if ! command -v bluetoothctl >/dev/null; then bad "BlueZ is not installed"; fix "sudo apt install bluez"
else
    if systemctl is-active --quiet bluetooth 2>/dev/null; then ok "bluetooth service running"
    else bad "bluetooth service not running"; fix "sudo systemctl enable --now bluetooth"; fi
    SHOW=$(bluetoothctl show 2>/dev/null </dev/null)
    if echo "$SHOW" | grep -q '^Controller'; then
        ok "adapter $(echo "$SHOW" | sed -n 's/^Controller \([0-9A-Fa-f:]*\).*/\1/p' | head -1)"
        if echo "$SHOW" | grep -q 'Powered: yes'; then ok "adapter powered on"
        else bad "adapter is off"; fix "turn Bluetooth on in Settings, or: bluetoothctl power on"; fi
        if rfkill list bluetooth 2>/dev/null | grep -q 'blocked: yes'; then bad "Bluetooth is blocked by rfkill"; fix "rfkill unblock bluetooth"; fi
    else bad "no Bluetooth adapter found"; fix "plug in a USB Bluetooth adapter with classic (BR/EDR) Bluetooth, then: sudo systemctl restart bluetooth"; fi
    PAIRED=$(bluetoothctl devices Paired 2>/dev/null </dev/null | grep '^Device')
    [ -n "$PAIRED" ] || PAIRED=$(bluetoothctl paired-devices 2>/dev/null </dev/null | grep '^Device')
    PHONES=0
    while read -r _ addr name; do
        [ -n "${addr:-}" ] || continue
        INFO=$(bluetoothctl info "$addr" 2>/dev/null </dev/null)
        if echo "$INFO" | grep -qE 'Icon: phone|Class: 0x[0-9a-fA-F]*02[0-9a-fA-F]{2}$'; then ok "paired phone: $name ($addr)"; PHONES=$((PHONES + 1)); fi
    done <<< "$PAIRED"
    if [ "$PHONES" = 0 ]; then bad "no paired phone"; fix "pair the iPhone in Settings > Bluetooth (or: bluetoothctl, then scan on / pair <address> / trust <address>)"
    elif [ "$PHONES" -gt 1 ]; then note "several paired phones"; fix "pick one: echo 'iphone.address=<address>' >> '$DATA/settings.properties'"; fi
fi

head_ "Network"
if command -v nmcli >/dev/null; then
    SSID=$(nmcli -t -f ACTIVE,SSID device wifi list --rescan no 2>/dev/null | sed -n 's/^yes://p' | head -1)
    if [ -n "$SSID" ]; then ok "Wi-Fi: $SSID"; else note "not on Wi-Fi (fine with Ethernet on the same router as the iPhone)"; fi
else note "NetworkManager's nmcli not found; ChairPlay falls back to iw"; fi
IP=$(ip -4 route get 1.1.1.1 2>/dev/null | sed -n 's/.* src \([0-9.]*\).*/\1/p')
if [ -n "$IP" ]; then ok "LAN address $IP"; else bad "no IPv4 route"; fix "connect to the router the iPhone uses"; fi
if [ -r /etc/ufw/ufw.conf ] && grep -qi '^ENABLED=yes' /etc/ufw/ufw.conf; then
    note "ufw is on: the iPhone may be blocked"; fix "use Add the rule on ChairPlay's Get started screen, or the ufw commands it shows"
elif command -v firewall-cmd >/dev/null && [ "$(firewall-cmd --state 2>/dev/null)" = running ]; then
    note "firewalld is on: the iPhone may be blocked"; fix "use Add the rule on ChairPlay's Get started screen, or the commands it shows"
else ok "no firewall running"; fi
if systemctl is-active --quiet avahi-daemon 2>/dev/null; then ok "avahi-daemon running (shares mDNS with ChairPlay)"; fi

head_ "Sound and picture"
if command -v pactl >/dev/null && pactl info >/dev/null 2>&1; then ok "sound server: $(pactl info | sed -n 's/^Server Name: //p')"
else note "no PipeWire/PulseAudio sound server answered"; fi
if [ -e /usr/share/alsa/alsa.conf.d/50-pipewire.conf ] || [ -e /usr/share/alsa/alsa.conf.d/99-pulse.conf ] || [ -e /etc/alsa/conf.d/99-pipewire-default.conf ]; then
    ok "ALSA routes to the sound server (Java sound plays through it)"
else note "no ALSA plugin for PipeWire/PulseAudio found"; fix "sudo apt install pipewire-alsa   (or: libasound2-plugins for PulseAudio)"; fi
if ldconfig -p 2>/dev/null | grep -q "libGL.so.1 "; then ok "OpenGL library present"
else bad "libGL.so.1 missing"; fix "sudo apt install libgl1"; fi
if ls /dev/dri/renderD* >/dev/null 2>&1; then ok "GPU render node present (VA-API decoding possible)"; fi

head_ "Settings window"
BROWSER=""
for b in google-chrome google-chrome-stable chromium chromium-browser brave-browser microsoft-edge vivaldi; do
    command -v "$b" >/dev/null && { BROWSER=$b; break; }
done
if [ -n "${CHAIRPLAY_BROWSER:-}" ]; then ok "CHAIRPLAY_BROWSER=$CHAIRPLAY_BROWSER"
elif [ -n "$BROWSER" ]; then ok "$BROWSER shows the settings as an app window"
else note "no Chromium-family browser: the settings open as a tab in the default browser"; fi
if command -v xdg-open >/dev/null; then ok "xdg-open present"; else bad "xdg-open missing"; fix "sudo apt install xdg-utils"; fi

echo
if [ "$FAILS" = 0 ]; then printf '\033[32mReady\033[0m%s. Open ChairPlay from the applications menu, unlock the iPhone, and press Connect.\n' "$([ "$WARNS" -gt 0 ] && echo " ($WARNS note(s) above)")"
else printf '\033[31m%s problem(s) to fix\033[0m, listed above with the fix under each.\n' "$FAILS"; exit 1; fi
