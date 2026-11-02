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

package io.github.numberonebot.chairplay.linux

import io.github.numberonebot.chairplay.Host
import java.io.File
import java.nio.file.Path

/**
 * The Linux desktop around the app: the dark-theme preference, the system's name, a dialog for a failed start, and
 * opening settings pages, folders and web pages. Every part works on GNOME (Ubuntu), COSMIC (Pop!_OS), KDE (Kubuntu),
 * Cinnamon (Mint), and the blueman/NetworkManager tools of Xfce, MATE and LXQt, and
 * falls back to doing nothing where a tool is missing.
 */
object LinuxDesktop {
    /**
     * Whether the desktop prefers dark apps: the XDG desktop portal's color-scheme (1 is dark), which GNOME, KDE and
     * COSMIC all answer, then GNOME's own setting, then COSMIC's theme file. Null when none says.
     */
    fun prefersDark(): Boolean? {
        Host.run(
            listOf("busctl", "--user", "--json=short", "call", "org.freedesktop.portal.Desktop", "/org/freedesktop/portal/desktop",
                "org.freedesktop.portal.Settings", "Read", "ss", "org.freedesktop.appearance", "color-scheme"),
            timeoutSeconds = 3,
        )?.let(::parsePortalColorScheme)?.let { return it }
        Host.run(listOf("gsettings", "get", "org.gnome.desktop.interface", "color-scheme"), timeoutSeconds = 3)?.trim()?.let {
            if (it.contains("dark")) return true
            if (it.contains("light") || it.contains("default")) return false
        }
        val config = System.getenv("XDG_CONFIG_HOME")?.let(::File) ?: File(System.getProperty("user.home"), ".config")
        val cosmic = File(config, "cosmic/com.system76.CosmicTheme.Mode/v1/is_dark")
        return runCatching { cosmic.readText().trim().toBooleanStrictOrNull() }.getOrNull()
    }

    /** The portal answers `{"type":"v","data":[{"type":"v","data":{"type":"u","data":1}}]}`, nesting varies by version. */
    internal fun parsePortalColorScheme(json: String): Boolean? {
        val value = Regex(""""type"\s*:\s*"u"\s*,\s*"data"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return when (value) {
            1 -> true
            2 -> false
            else -> null
        }
    }

    /** "Pop!_OS 22.04 LTS (Linux 6.9.3)", from /etc/os-release and the kernel. */
    fun systemName(osRelease: String? = runCatching { File("/etc/os-release").readText() }.getOrNull()): String {
        val pretty = osRelease?.lines()?.firstOrNull { it.startsWith("PRETTY_NAME=") }?.substringAfter('=')?.trim('"', '\'')
        val kernel = System.getProperty("os.version")
        return listOfNotNull(pretty ?: "Linux", kernel?.let { "(Linux $it)" }).joinToString(" ")
    }

    /** An error dialog for a failed start: zenity (GNOME, COSMIC), kdialog (KDE), or a notification. */
    fun showError(text: String) {
        val command = when {
            Host.which("zenity") != null -> listOf("zenity", "--error", "--title=ChairPlay", "--no-markup", "--text=$text")
            Host.which("kdialog") != null -> listOf("kdialog", "--title", "ChairPlay", "--error", text)
            Host.which("notify-send") != null -> listOf("notify-send", "-u", "critical", "ChairPlay", text)
            else -> null
        }
        System.err.println(text)
        if (command != null) Host.run(command, timeoutSeconds = 600)
    }

    /** Opens a web address, a file or a folder with the desktop's default application. */
    fun open(target: String) {
        val opener = Host.which("xdg-open", "gio") ?: throw IllegalStateException("xdg-open is not installed")
        val command = if (opener.endsWith("gio")) listOf(opener, "open", target) else listOf(opener, target)
        ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().outputStream.close()
    }

    /**
     * The command that opens a settings page ("bluetooth", "wifi", "firewall", "location") on this desktop, or null
     * when no settings app for it is installed.
     */
    fun settingsCommand(page: String): List<String>? {
        // COSMIC Settings opens a page by its id; the network pages' ids have moved between releases, so they open
        // the app itself.
        val cosmic = mapOf("bluetooth" to listOf("bluetooth"), "wifi" to emptyList(), "location" to emptyList())
        val gnome = mapOf("bluetooth" to "bluetooth", "wifi" to "wifi", "location" to "location")
        val kde = mapOf("bluetooth" to "kcm_bluetooth", "wifi" to "kcm_networkmanagement")
        return when {
            page == "firewall" -> when {
                Host.which("gufw") != null -> listOf("gufw")
                Host.which("firewall-config") != null -> listOf("firewall-config")
                else -> null
            }
            Host.which("cosmic-settings") != null && page in cosmic -> listOf("cosmic-settings") + cosmic.getValue(page)
            Host.which("gnome-control-center") != null && page in gnome -> listOf("gnome-control-center", gnome.getValue(page))
            Host.which("systemsettings") != null && page in kde -> listOf("systemsettings", kde.getValue(page))
            // Linux Mint (Cinnamon): its own network page; Bluetooth is blueman's, below.
            Host.which("cinnamon-settings") != null && page == "wifi" -> listOf("cinnamon-settings", "network")
            Host.which("blueman-manager") != null && page == "bluetooth" -> listOf("blueman-manager")
            Host.which("nm-connection-editor") != null && page == "wifi" -> listOf("nm-connection-editor")
            else -> null
        }
    }

    /**
     * Where a packaged ChairPlay keeps its data on Linux: the program sits read-only in /opt, so settings, keys,
     * pairings, MFi files, logs and screenshots go to `$XDG_DATA_HOME/ChairPlay` (~/.local/share/ChairPlay).
     */
    /**
     * [dataHome], made if missing and kept private to the user (rwx------): it holds the Wi-Fi password, the AirPlay
     * keys and pairings, and the MFi private key.
     */
    fun privateDataHome(): Path {
        val dir = dataHome()
        java.nio.file.Files.createDirectories(dir)
        runCatching { java.nio.file.Files.setPosixFilePermissions(dir, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")) }
        return dir
    }

    fun dataHome(): Path {
        val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            ?: Path.of(System.getProperty("user.home"), ".local", "share")
        return base.resolve("ChairPlay")
    }
}
