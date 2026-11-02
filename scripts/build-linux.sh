#!/usr/bin/env bash
# ====================================================================================================
#   UNOFFICIAL LINUX PORT -- THIS IS NOT THE ORIGINAL PROJECT
#
#   ChairPlay is written by Oleksiy Strelets (NumberOneBot), GPL-3.0:
#       https://github.com/NumberOneBot/ChairPlay
#   This file was ADDED by an unofficial port of ChairPlay to Ubuntu-family Linux (Ubuntu, Pop!_OS,
#   Linux Mint, Kubuntu, Zorin, elementary, Debian...), made for personal use only. The port is not
#   affiliated with or endorsed by the ChairPlay author, DiPlay, xcertplay, or Apple. Please do not
#   report problems with this port to the original project. See LINUX-PORT-NOTICE.md.
# ====================================================================================================
#
# Builds ChairPlay into a .deb for this machine: build/jpackage/chairplay_<version>-1_<arch>.deb
#
# Usage: scripts/build-linux.sh [--install] [--test] [--version X.Y.Z] [--clean] [--no-deps]
#   --install     install the .deb when it is built (sudo apt install)
#   --test        run the test suite (JVM and web UI) before packaging; needs a desktop session
#   --version     the release version to stamp (default: a development build, 0.0.0)
#   --clean       delete earlier build output first
#   --no-deps     do not apt-install missing packages; fail instead
#
# Works on Ubuntu 22.04 or later and the distributions built on it or on Debian 12 or later, on x86-64 (amd64)
# or arm64. It installs the build tools with apt (asking for sudo), uses OpenJDK 17 from apt or, where the release
# no longer ships it, Eclipse Temurin 17 from adoptium.net, uses your Node.js if recent enough or fetches one from
# nodejs.org (both downloads are checked against their published SHA-256), then runs Gradle's linuxDeb task.
# Build on the machine you will run ChairPlay on: the .deb's dependencies are those of this release.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT=$(pwd)

INSTALL=0
TEST=0
VERSION=""
CLEAN=0
DEPS=1
while [ $# -gt 0 ]; do
    case "$1" in
        --install) INSTALL=1 ;;
        --test) TEST=1 ;;
        --version) VERSION="${2:?--version needs X.Y.Z}"; shift ;;
        --clean) CLEAN=1 ;;
        --no-deps) DEPS=0 ;;
        -h|--help) sed -n 13,26p "$0"; exit 0 ;;
        *) echo "unknown option: $1 (see --help)" >&2; exit 2 ;;
    esac
    shift
done

say() { printf '\033[1m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[33mwarning:\033[0m %s\n' "$*" >&2; }
die() { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }

# 0. The system: an apt-based distribution of the Ubuntu or Debian family.
[ -r /etc/os-release ] || die "cannot read /etc/os-release"
# shellcheck disable=SC1091
. /etc/os-release
FAMILY=" ${ID:-} ${ID_LIKE:-} "
case "$FAMILY" in
    *" ubuntu "*|*" debian "*) ;;
    *) die "this script is for Ubuntu, Debian and the distributions built on them (found ${PRETTY_NAME:-unknown})" ;;
esac
command -v apt-get >/dev/null || die "apt-get not found"
say "System: ${PRETTY_NAME:-$ID}"
version_at_least() { [ "$(printf '%s\n%s\n' "$2" "$1" | sort -V | head -1)" = "$2" ]; }
if [ "${ID:-}" = ubuntu ] && ! version_at_least "${VERSION_ID:-0}" 22.04; then
    warn "Ubuntu ${VERSION_ID:-?} is older than 22.04, the oldest release this port is meant for; continuing anyway"
elif [ "${ID:-}" = debian ] && ! version_at_least "${VERSION_ID:-0}" 12; then
    warn "Debian ${VERSION_ID:-?} is older than 12; continuing anyway"
fi

ARCH=$(dpkg --print-architecture)
case "$ARCH" in
    amd64) NODE_ARCH=x64; JDK_ARCH=x64 ;;
    arm64) NODE_ARCH=arm64; JDK_ARCH=aarch64 ;;
    *) die "unsupported architecture: $ARCH (amd64 and arm64 are)" ;;
esac

apt_install() {
    [ "$DEPS" = 1 ] || die "missing packages: $* (sudo apt install $*)"
    say "Installing $*"
    sudo apt-get update
    sudo apt-get install -y "$@"
}
available() { apt-cache policy "$1" 2>/dev/null | sed -n 's/^ *Candidate: //p' | grep -qv '(none)'; }

# 1. Build tools: a C/C++ compiler and the GL headers for the native parts, fakeroot and dpkg-deb for the .deb.
PACKAGES=(build-essential libgl-dev fakeroot git curl xz-utils ca-certificates python3)
MISSING=()
for p in "${PACKAGES[@]}"; do dpkg -s "$p" >/dev/null 2>&1 || MISSING+=("$p"); done
[ ${#MISSING[@]} -eq 0 ] || apt_install "${MISSING[@]}"

# 2. JDK 17 with jpackage: the build's Kotlin toolchain is pinned to 17.
CACHE="$HOME/.cache/chairplay"
JAVA_HOME="/usr/lib/jvm/java-17-openjdk-$ARCH"
if [ ! -x "$JAVA_HOME/bin/jpackage" ]; then
    if available openjdk-17-jdk; then
        apt_install openjdk-17-jdk
    else
        # Debian 13 and some newer releases dropped OpenJDK 17 from their archive.
        JAVA_HOME="$CACHE/jdk-17-$JDK_ARCH"
        if [ ! -x "$JAVA_HOME/bin/jpackage" ]; then
            say "OpenJDK 17 is not in this release's archive; fetching Eclipse Temurin 17 into $JAVA_HOME"
            mkdir -p "$CACHE"
            TMP=$(mktemp -d)
            curl -fsSL -o "$TMP/assets.json" \
                "https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=$JDK_ARCH&image_type=jdk&os=linux&vendor=eclipse"
            read -r LINK SUM < <(python3 -c 'import json,sys; p=json.load(open(sys.argv[1]))[0]["binary"]["package"]; print(p["link"], p["checksum"])' "$TMP/assets.json")
            curl -fsSL -o "$TMP/jdk.tar.gz" "$LINK"
            echo "$SUM  $TMP/jdk.tar.gz" | sha256sum -c -
            mkdir -p "$TMP/jdk" && tar -xzf "$TMP/jdk.tar.gz" -C "$TMP/jdk" --strip-components=1
            rm -rf "$JAVA_HOME" && mv "$TMP/jdk" "$JAVA_HOME"
            rm -rf "$TMP"
        fi
    fi
fi
[ -x "$JAVA_HOME/bin/jpackage" ] || die "no jpackage in $JAVA_HOME; install openjdk-17-jdk"
export JAVA_HOME

# 3. DiPlay, the CarPlay core, is the upstream submodule.
if [ ! -f upstream/shared/src/main/java/com/shilapi/xcertplay/airplay/AirPlayConfig.kt ]; then
    say "Fetching the DiPlay core (git submodule)"
    [ -d .git ] || die "upstream/ is empty and this is not a git checkout: clone with  git clone --recursive <url>"
    git submodule update --init --recursive
fi

# 4. Node.js for the web UI: webui/package-lock.json wants 22.22+ or 24.15+ (or 26+).
NODE_VERSION=24.22.1
node_ok() {
    command -v node >/dev/null 2>&1 || return 1
    # A snap's node is a link to snap itself, beside which the Gradle build cannot find npm.
    case "$(readlink -f "$(command -v node)")" in */snap) return 1 ;; esac
    local v major minor
    v=$(node -v | sed 's/^v//')
    major=${v%%.*}; minor=$(echo "$v" | cut -d. -f2)
    { [ "$major" = 22 ] && [ "$minor" -ge 22 ]; } || { [ "$major" = 24 ] && [ "$minor" -ge 15 ]; } || [ "$major" -ge 26 ]
}
if ! node_ok; then
    NODE_DIR="$CACHE/node-v$NODE_VERSION-linux-$NODE_ARCH"
    if [ ! -x "$NODE_DIR/bin/node" ]; then
        say "Fetching Node.js $NODE_VERSION into $NODE_DIR (the build's only use of it; nothing of it ships)"
        mkdir -p "$CACHE"
        TARBALL="node-v$NODE_VERSION-linux-$NODE_ARCH.tar.xz"
        TMP=$(mktemp -d)
        curl -fsSL -o "$TMP/$TARBALL" "https://nodejs.org/dist/v$NODE_VERSION/$TARBALL"
        curl -fsSL -o "$TMP/SHASUMS256.txt" "https://nodejs.org/dist/v$NODE_VERSION/SHASUMS256.txt"
        (cd "$TMP" && grep " $TARBALL\$" SHASUMS256.txt | sha256sum -c -)
        tar -xJf "$TMP/$TARBALL" -C "$CACHE"
        rm -rf "$TMP"
    fi
    export PATH="$NODE_DIR/bin:$PATH"
fi
say "Node $(node -v), JDK $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"

# 5. The build.
chmod +x gradlew
ARGS=(--no-daemon "-Porg.gradle.java.installations.paths=$JAVA_HOME")
[ -n "$VERSION" ] && ARGS+=("-Pchairplay.version=$VERSION")
if [ "$CLEAN" = 1 ]; then
    say "Cleaning"
    ./gradlew "${ARGS[@]}" clean
fi
if [ "$TEST" = 1 ]; then
    say "Running the tests"
    ./gradlew "${ARGS[@]}" test
fi
say "Building the .deb"
./gradlew "${ARGS[@]}" linuxDeb

DEB=$(find build/jpackage -maxdepth 1 -name 'chairplay_*.deb' -printf '%T@ %p\n' | sort -n | tail -1 | cut -d' ' -f2-)
[ -n "$DEB" ] || die "the build made no .deb"
say "Built $ROOT/$DEB"
if [ "$INSTALL" = 1 ]; then
    say "Installing it"
    sudo apt-get install -y "./$DEB"
    echo "Installed. Next: scripts/check-linux.sh, then open ChairPlay from the applications menu."
else
    echo "Install it with:  sudo apt install ./$DEB"
    echo "Or try it without installing:  build/jpackage/ChairPlay/bin/ChairPlay"
fi
