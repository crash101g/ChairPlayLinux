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

import io.github.numberonebot.chairplay.bluetooth.PairedDevice
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LinuxBluetoothTest {
    @Test
    fun `bdaddr holds the address least significant byte first`() {
        val expected = intArrayOf(0xF1, 0x0E, 0x00, 0xCC, 0xBB, 0xAA).map { it.toByte() }.toByteArray()
        assertArrayEquals(expected, LinuxBluetooth.bdaddr("AA:BB:CC:00:0E:F1"))
    }

    // busctl --json=short call org.bluez / org.freedesktop.DBus.ObjectManager GetManagedObjects, cut down.
    private val objects = """
        {"type":"a{oa{sa{sv}}}","data":[{
          "/org/bluez":{"org.bluez.AgentManager1":{}},
          "/org/bluez/hci0":{"org.bluez.Adapter1":{"Address":{"type":"s","data":"00:1a:7d:da:71:13"},"Powered":{"type":"b","data":true}}},
          "/org/bluez/hci0/dev_AA_BB_CC_00_0E_F1":{"org.bluez.Device1":{"Address":{"type":"s","data":"AA:BB:CC:00:0E:F1"},
            "Name":{"type":"s","data":"iPhone"},"Alias":{"type":"s","data":"Test iPhone"},"Class":{"type":"u","data":7995916},
            "Paired":{"type":"b","data":true}}},
          "/org/bluez/hci0/dev_11_22_33_44_55_66":{"org.bluez.Device1":{"Address":{"type":"s","data":"11:22:33:44:55:66"},
            "Name":{"type":"s","data":"Speaker"},"Paired":{"type":"b","data":false}}}
        }]}
    """.trimIndent()

    @Test
    fun `the adapter and the paired devices come from BlueZ's object tree`() {
        val parsed = LinuxBluetooth.parseObjects(objects)
        assertEquals(listOf(LinuxBluetooth.Adapter("/org/bluez/hci0", "00:1A:7D:DA:71:13", true)), parsed.adapters)
        assertEquals(
            listOf("/org/bluez/hci0/dev_AA_BB_CC_00_0E_F1" to PairedDevice("Test iPhone", "AA:BB:CC:00:0E:F1", 7995916)),
            parsed.devices,
        )
    }

    @Test
    fun `an empty answer has no adapter`() {
        val parsed = LinuxBluetooth.parseObjects("""{"type":"a{oa{sa{sv}}}","data":[{}]}""")
        assertEquals(emptyList<LinuxBluetooth.Adapter>(), parsed.adapters)
        assertEquals(0, parsed.devices.size)
    }

    @Test
    fun `a phone without a class counts as a phone by its icon`() {
        val json = """{"type":"a{oa{sa{sv}}}","data":[{"/org/bluez/hci0":{"org.bluez.Adapter1":{"Address":{"type":"s","data":"00:1A:7D:DA:71:13"},"Powered":{"type":"b","data":true}}},
            "/org/bluez/hci0/dev_AA_BB_CC_00_0E_F1":{"org.bluez.Device1":{"Address":{"type":"s","data":"AA:BB:CC:00:0E:F1"},
            "Name":{"type":"s","data":"iPhone"},"Icon":{"type":"s","data":"phone"},"Paired":{"type":"b","data":true}}}}]}"""
        val device = LinuxBluetooth.parseObjects(json).devices.single().second
        assertEquals(2, (device.classOfDevice shr 8) and 0x1F)
    }

    @Test
    fun `bluetoothctl's adapter, paired list and device info are read`() {
        val show = "Controller 00:1A:7D:DA:71:13 (public)\n\tName: pop-os\n\tPowered: yes\n\tDiscoverable: no\n"
        assertEquals(LinuxBluetooth.Adapter("/org/bluez/hci0", "00:1A:7D:DA:71:13", true), LinuxBluetooth.parseBluetoothctlShow(show))
        assertEquals(listOf("A4:B1:C2:D3:E4:F5"), LinuxBluetooth.parseBluetoothctlDevices("\u001B[0;94mDevice A4:B1:C2:D3:E4:F5 Test iPhone\n"))
        val info = "Device A4:B1:C2:D3:E4:F5 (public)\n\tName: Test iPhone\n\tAlias: Test iPhone\n\tClass: 0x007a020c\n\tIcon: phone\n\tPaired: yes\n"
        assertEquals(PairedDevice("Test iPhone", "A4:B1:C2:D3:E4:F5", 0x7a020c), LinuxBluetooth.parseBluetoothctlInfo("A4:B1:C2:D3:E4:F5", info))
        assertEquals(null, LinuxBluetooth.parseBluetoothctlInfo("A4:B1:C2:D3:E4:F5", "\tName: x\n\tPaired: no\n"))
    }
}
