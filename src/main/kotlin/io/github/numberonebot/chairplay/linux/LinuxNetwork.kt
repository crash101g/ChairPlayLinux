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
import io.github.numberonebot.chairplay.network.EthernetAdapter
import io.github.numberonebot.chairplay.network.WlanConnection
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * The PC's networks on Linux. Wi-Fi comes from NetworkManager (nmcli), which Ubuntu and Pop!_OS use, or from iw on
 * a system without it. Ethernet adapters are the physical, non-wireless ARPHRD_ETHER interfaces in /sys/class/net.
 * A WlanConnection's interfaceDescription is the interface name, which is what Java's NetworkInterface.displayName
 * reports on Linux, so hotspotInfoFor matches them as it matches Windows' adapter descriptions.
 */
object LinuxNetwork {
    fun wifiConnections(): List<WlanConnection> {
        if (Host.which("nmcli") != null) {
            Host.run(listOf("nmcli", "-t", "-e", "yes", "-f", "ACTIVE,SSID-HEX,BSSID,CHAN,FREQ,DEVICE", "device", "wifi", "list", "--rescan", "no"))
                ?.let { return parseNmcli(it) }
        }
        if (Host.which("iw", "/usr/sbin/iw") == null && !File("/usr/sbin/iw").canExecute()) return emptyList()
        val iw = Host.which("iw") ?: "/usr/sbin/iw"
        val interfaces = Host.run(listOf(iw, "dev"))?.lines()?.mapNotNull { Regex("""^\s*Interface\s+(\S+)""").find(it)?.groupValues?.get(1) }
            ?: return emptyList()
        return interfaces.mapNotNull { name -> Host.run(listOf(iw, "dev", name, "link"))?.let { parseIwLink(name, it) } }
    }

    /** nmcli's terse, escaped listing: `ACTIVE:SSID-HEX:BSSID:CHAN:FREQ:DEVICE`, colons in values written `\:`. */
    internal fun parseNmcli(text: String): List<WlanConnection> = text.lines().mapNotNull { line ->
        val fields = splitTerse(line)
        if (fields.size < 6 || fields[0] != "yes") return@mapNotNull null
        val ssid = runCatching { String(hex(fields[1]), Charsets.UTF_8) }.getOrNull() ?: return@mapNotNull null
        val bssid = mac(fields[2]) ?: ByteArray(6)
        val frequency = Regex("""\d+""").find(fields[4])?.value?.toIntOrNull()
        val channel = fields[3].toIntOrNull() ?: frequency?.let(::channelOf) ?: 0
        WlanConnection(fields[5], ssid, bssid, channel, frequency)
    }.distinctBy { it.interfaceDescription }

    /** `iw dev <name> link`: "Connected to aa:bb:.. (on wlan0)", then "SSID: ..." and "freq: 5180[.0]". */
    internal fun parseIwLink(name: String, text: String): WlanConnection? {
        val bssid = Regex("""Connected to ([0-9a-fA-F:]{17})""").find(text)?.groupValues?.get(1)?.let(::mac) ?: return null
        val ssid = Regex("""^\s*SSID: (.*)$""", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)?.let(::unescapeIw) ?: return null
        val frequency = Regex("""^\s*freq: (\d+)""", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)?.toIntOrNull()
        return WlanConnection(name, ssid, bssid, frequency?.let(::channelOf) ?: 0, frequency)
    }

    /** The physical, wired Ethernet interfaces that are up with an IPv4 address; the interface name is the id. */
    fun ethernet(): List<EthernetAdapter> {
        val sys = File("/sys/class/net")
        return (sys.listFiles() ?: emptyArray()).sortedBy { it.name }.mapNotNull { dir ->
            val wired = File(dir, "type").readTextOrNull()?.trim() == "1" && !File(dir, "wireless").exists() &&
                !File(dir, "phy80211").exists() && File(dir, "device").exists()
            if (!wired) return@mapNotNull null
            val nic = runCatching { NetworkInterface.getByName(dir.name) }.getOrNull() ?: return@mapNotNull null
            val addresses = nic.inetAddresses.toList().filterIsInstance<Inet4Address>()
                .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress || it.isAnyLocalAddress || it.isMulticastAddress }
                .map { it.hostAddress }.sorted()
            if (!nic.isUp || addresses.isEmpty()) null else EthernetAdapter(dir.name, dir.name, dir.name, addresses)
        }
    }

    internal fun channelOf(frequency: Int): Int? = when (frequency) {
        2484 -> 14
        in 2412..2472 -> (frequency - 2407) / 5
        in 5160..5885 -> (frequency - 5000) / 5
        in 5955..7115 -> (frequency - 5950) / 5
        else -> null
    }

    internal fun splitTerse(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\\' && i + 1 < line.length -> { current.append(line[i + 1]); i++ }
                c == ':' -> { fields += current.toString(); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        fields += current.toString()
        return fields
    }

    private fun hex(text: String): ByteArray {
        val clean = text.removePrefix("0x").trim()
        require(clean.length % 2 == 0)
        return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private fun mac(text: String): ByteArray? {
        val parts = text.trim().split(":")
        if (parts.size != 6) return null
        return runCatching { ByteArray(6) { parts[it].toInt(16).toByte() } }.getOrNull()
    }

    /** iw prints bytes outside printable ASCII as `\xNN`. */
    private fun unescapeIw(text: String): String {
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < text.length) {
            if (text[i] == '\\' && i + 3 < text.length && text[i + 1] == 'x') {
                bytes.write(text.substring(i + 2, i + 4).toInt(16))
                i += 4
            } else {
                bytes.write(text.substring(i, i + 1).toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun File.readTextOrNull(): String? = runCatching { readText() }.getOrNull()
}
