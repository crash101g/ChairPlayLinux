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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxPlatformTest {
    @Test
    fun `nmcli's active network is read with its escaped BSSID and hex SSID`() {
        // "My Wi:Fi" in hex; the second line is a network in range but not joined.
        val text = "yes:4D792057693A4669:AA\\:BB\\:CC\\:DD\\:EE\\:01:36:5180 MHz:wlp2s0\n" +
            "no:4F74686572:11\\:22\\:33\\:44\\:55\\:66:6:2437 MHz:wlp2s0\n"
        val connections = LinuxNetwork.parseNmcli(text)
        assertEquals(1, connections.size)
        val wifi = connections.single()
        assertEquals("wlp2s0", wifi.interfaceDescription)
        assertEquals("My Wi:Fi", wifi.ssid)
        assertArrayEquals(intArrayOf(0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0x01).map { it.toByte() }.toByteArray(), wifi.bssid)
        assertEquals(36, wifi.channel)
        assertEquals(5180, wifi.frequencyMHz)
    }

    @Test
    fun `iw's link report gives the network and its channel`() {
        val text = "Connected to aa:bb:cc:dd:ee:02 (on wlan0)\n\tSSID: Caf\\xc3\\xa9\n\tfreq: 2437.0\n\tRX: 1 bytes\n"
        val wifi = LinuxNetwork.parseIwLink("wlan0", text)!!
        assertEquals("Café", wifi.ssid)
        assertEquals(6, wifi.channel)
        assertEquals(2437, wifi.frequencyMHz)
        assertNull(LinuxNetwork.parseIwLink("wlan0", "Not connected.\n"))
    }

    @Test
    fun `frequencies map to channel numbers on every band`() {
        assertEquals(1, LinuxNetwork.channelOf(2412))
        assertEquals(14, LinuxNetwork.channelOf(2484))
        assertEquals(149, LinuxNetwork.channelOf(5745))
        assertEquals(37, LinuxNetwork.channelOf(6135))
        assertNull(LinuxNetwork.channelOf(900))
    }

    @Test
    fun `ufw counts as on only when its configuration enables it`() {
        assertTrue(LinuxFirewall.parseUfwEnabled("# comment\nENABLED=yes\nLOGLEVEL=low\n"))
        assertFalse(LinuxFirewall.parseUfwEnabled("ENABLED=no\n"))
        assertFalse(LinuxFirewall.parseUfwEnabled(""))
    }

    @Test
    fun `the firewall hint opens the AirPlay ports, the ephemeral range and mDNS`() {
        assertEquals(32768 to 60999, LinuxFirewall.ephemeralRange("32768\t60999\n"))
        assertEquals(32768 to 60999, LinuxFirewall.ephemeralRange(null))
        val hint = LinuxFirewall.ufwHint(7000).lines()
        assertEquals(4, hint.size)
        assertTrue(hint.all { it.startsWith("sudo ufw allow ") })
        assertTrue(hint[0].contains("tcp to any port 7000:7010"))
        assertTrue(hint[3].contains("5353/udp"))
    }

    @Test
    fun `the portal's color scheme reads 1 as dark and 2 as light`() {
        assertEquals(true, LinuxDesktop.parsePortalColorScheme("""{"type":"v","data":[{"type":"v","data":{"type":"u","data":1}}]}"""))
        assertEquals(false, LinuxDesktop.parsePortalColorScheme("""{"type":"v","data":[{"type":"u","data":2}]}"""))
        assertNull(LinuxDesktop.parsePortalColorScheme("""{"type":"v","data":[{"type":"u","data":0}]}"""))
    }

    @Test
    fun `the system is named from os-release`() {
        val name = LinuxDesktop.systemName("NAME=\"Pop!_OS\"\nPRETTY_NAME=\"Pop!_OS 24.04 LTS\"\n")
        assertTrue(name, name.startsWith("Pop!_OS 24.04 LTS"))
    }

    @Test
    fun `the firewalld hint opens the same ports and reloads`() {
        val hint = LinuxFirewall.firewalldHint(7000, 32768 to 60999).lines()
        assertTrue(hint.first(), hint.first().endsWith("--add-port=7000-7010/tcp"))
        assertTrue(hint.contains("sudo firewall-cmd --permanent --add-service=mdns"))
        assertEquals("sudo firewall-cmd --reload", hint.last())
    }
}
