// ====================================================================================================
//   UNOFFICIAL LINUX (POP!_OS) PORT -- THIS IS NOT THE ORIGINAL PROJECT
//
//   ChairPlay is written by Oleksiy Strelets (NumberOneBot), GPL-3.0:
//       https://github.com/NumberOneBot/ChairPlay
//   This file was ADDED by an unofficial port of ChairPlay to Pop!_OS / Ubuntu Linux, made for
//   personal use only. The port is not affiliated with or endorsed by the ChairPlay author, DiPlay,
//   xcertplay, or Apple. Please do not report problems with this port to the original project.
//   See LINUX-PORT-NOTICE.md.
// ====================================================================================================

/**
 * Whether the page runs on Linux: ChairPlay's Linux build opens it in the PC's own browser, whose user agent names
 * Linux or X11, where Windows shows it in WebView2. The texts that name the system's own settings, firewall, and
 * sound devices follow it. (jsdom's user agent names the platform in lower case, so the tests keep Windows' texts.)
 */
export const onLinux =
  typeof navigator !== 'undefined' &&
  /\b(Linux|X11)\b/.test(navigator.userAgent) &&
  !/Android/.test(navigator.userAgent)

/** The system's name in texts. */
export const systemName = onLinux ? 'Linux' : 'Windows'

/** The firewall's name in texts. */
export const firewallName = onLinux ? 'the firewall' : 'Windows Firewall'
