// ====================================================================================================
//   UNOFFICIAL LINUX (POP!_OS) PORT: this file is from ChairPlay by Oleksiy Strelets
//   (https://github.com/NumberOneBot/ChairPlay, GPL-3.0) and was MODIFIED on 2026-11-02 for an
//   unofficial, personal-use port to Pop!_OS / Ubuntu Linux. Not affiliated with the original
//   project. See LINUX-PORT-NOTICE.md for what changed.
// ====================================================================================================

package io.github.numberonebot.chairplay.media

import io.github.numberonebot.chairplay.config.Theme
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/**
 * The night mode to send the iPhone (AirPlaySession.setNightMode) for [theme], or null to leave it to the
 * iPhone. [windowsDark] is asked only for [Theme.WINDOWS]; an unknown answer counts as light.
 */
fun nightMode(theme: Theme, windowsDark: () -> Boolean?): Boolean? = when (theme) {
    Theme.LIGHT -> false
    Theme.DARK -> true
    Theme.WINDOWS -> windowsDark() ?: false
    Theme.PHONE -> null
}

/**
 * Whether Windows apps use the dark theme (Settings > Personalization > Colors), or on Linux whether the desktop
 * prefers dark apps; null if it cannot be read.
 */
fun windowsAppsDark(): Boolean? = if (!io.github.numberonebot.chairplay.Host.isWindows) {
    io.github.numberonebot.chairplay.linux.LinuxDesktop.prefersDark()
} else runCatching {
    Advapi32Util.registryGetIntValue(
        WinReg.HKEY_CURRENT_USER,
        "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
        "AppsUseLightTheme",
    ) == 0
}.getOrNull()
