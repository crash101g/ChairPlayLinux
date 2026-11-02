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

import com.shilapi.xcertplay.transport.BlockingDuplexByteStream
import com.shilapi.xcertplay.transport.BluetoothRfcommDuplexStream
import com.shilapi.xcertplay.transport.BluetoothRfcommSocketAccess
import com.sun.jna.Memory
import io.github.numberonebot.chairplay.Host
import io.github.numberonebot.chairplay.bluetooth.PairedDevice
import io.github.numberonebot.chairplay.bluetooth.RfcommConnection
import io.github.numberonebot.chairplay.ui.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Linux Bluetooth through BlueZ: the adapter and the paired devices from bluetoothd's object tree (read with busctl,
 * which every systemd system has), the RFCOMM channel from the iPhone's SDP records, and the RFCOMM socket from the
 * kernel. Pair the iPhone in the desktop's Bluetooth settings first, as on Windows.
 */
object LinuxBluetooth {
    private val libc get() = LibC.INSTANCE

    /** A paired device and whether bluetoothd knows it as paired; the adapter's address and power. */
    internal data class Adapter(val path: String, val address: String, val powered: Boolean)

    internal data class Objects(val adapters: List<Adapter>, val devices: List<Pair<String, PairedDevice>>)

    /** Class of device "Phone, smartphone" (major class 2), for a phone BlueZ gives no class. */
    private const val PHONE_CLASS = 0x00020C

    fun localRadioAddress(): String {
        val adapter = objects().adapters.firstOrNull()
            ?: throw IOException("No Bluetooth adapter is available (is bluetoothd running? `systemctl status bluetooth`)")
        if (!adapter.powered) throw IOException("Bluetooth is off: turn it on in Settings > Bluetooth")
        return adapter.address
    }

    fun pairedDevices(): List<PairedDevice> {
        val all = objects()
        val adapter = all.adapters.firstOrNull()?.path ?: return emptyList()
        return all.devices.filter { (path, _) -> path.startsWith("$adapter/") }.map { it.second }
    }

    /**
     * BlueZ's adapter and devices: from D-Bus through busctl, or, where that call gives nothing (busctl's JSON output
     * differs between systemd releases), from bluetoothctl, which every BlueZ install has.
     */
    private fun objects(): Objects {
        Host.run(
            listOf("busctl", "--system", "--json=short", "call", "org.bluez", "/", "org.freedesktop.DBus.ObjectManager", "GetManagedObjects"),
        )?.let { json -> runCatching { parseObjects(json) }.getOrNull()?.takeIf { it.adapters.isNotEmpty() }?.let { return it } }
        return bluetoothctlObjects()
            ?: throw IOException("Cannot read BlueZ's devices: is the bluetooth service running? (`systemctl status bluetooth`)")
    }

    private fun bluetoothctl(vararg args: String): String? =
        Host.run(listOf("bluetoothctl") + args, timeoutSeconds = 10, env = mapOf("NO_COLOR" to "1", "TERM" to "dumb"))

    /** The default adapter and its paired devices as bluetoothctl prints them; null when bluetoothctl gives nothing. */
    private fun bluetoothctlObjects(): Objects? {
        val show = bluetoothctl("show") ?: return null
        val adapter = parseBluetoothctlShow(show) ?: return Objects(emptyList(), emptyList())
        // BlueZ 5.65 and later take `devices Paired`; 5.64 (Ubuntu 22.04, Pop!_OS 22.04) has `paired-devices`.
        val listing = bluetoothctl("devices", "Paired")?.takeIf { DEVICE_LINE.containsMatchIn(it) } ?: bluetoothctl("paired-devices").orEmpty()
        val devices = parseBluetoothctlDevices(listing).mapNotNull { address ->
            val info = bluetoothctl("info", address) ?: return@mapNotNull null
            parseBluetoothctlInfo(address, info)?.let { "${adapter.path}/dev_${address.replace(':', '_')}" to it }
        }
        return Objects(listOf(adapter), devices)
    }

    private val ESCAPES = Regex("""\u001B\[[0-9;?]*[A-Za-z]|\u0001|\u0002""")
    private val DEVICE_LINE = Regex("""^\s*Device ([0-9A-Fa-f:]{17})\b""", RegexOption.MULTILINE)

    private fun clean(text: String) = text.replace(ESCAPES, "")

    /** `bluetoothctl show`: "Controller 00:1A:7D:DA:71:13 (public)" and "Powered: yes". */
    internal fun parseBluetoothctlShow(text: String): Adapter? {
        val clean = clean(text)
        val address = Regex("""^\s*Controller ([0-9A-Fa-f:]{17})""", RegexOption.MULTILINE).find(clean)?.groupValues?.get(1) ?: return null
        val powered = Regex("""^\s*Powered: (\w+)""", RegexOption.MULTILINE).find(clean)?.groupValues?.get(1) == "yes"
        return Adapter("/org/bluez/hci0", address.uppercase(), powered)
    }

    /** `bluetoothctl devices Paired`: one "Device A4:B1:C2:D3:E4:F5 iPhone" line per device. */
    internal fun parseBluetoothctlDevices(text: String): List<String> =
        DEVICE_LINE.findAll(clean(text)).map { it.groupValues[1].uppercase() }.distinct().toList()

    /** `bluetoothctl info <address>`: Name, Alias, Class, Icon, Paired. Null for a device not paired. */
    internal fun parseBluetoothctlInfo(address: String, text: String): PairedDevice? {
        val clean = clean(text)
        fun field(name: String) = Regex("""^\s*$name: (.*)$""", RegexOption.MULTILINE).find(clean)?.groupValues?.get(1)?.trim()
        if (field("Paired") == "no" && field("Bonded") != "yes") return null
        val cod = field("Class")?.removePrefix("0x")?.toIntOrNull(16)?.takeIf { it != 0 }
            ?: if (field("Icon") == "phone") PHONE_CLASS else 0
        return PairedDevice(field("Alias") ?: field("Name") ?: address, address.uppercase(), cod)
    }

    /** busctl's JSON of GetManagedObjects: `{"type":"a{oa{sa{sv}}}","data":[{path: {interface: {name: {type, data}}}}]}`. */
    @Suppress("UNCHECKED_CAST")
    internal fun parseObjects(json: String): Objects {
        val root = Json.parse(json) as? Map<String, Any?> ?: return Objects(emptyList(), emptyList())
        val tree = (root["data"] as? List<Any?>)?.firstOrNull() as? Map<String, Any?> ?: return Objects(emptyList(), emptyList())
        fun value(props: Map<String, Any?>?, name: String): Any? = (props?.get(name) as? Map<String, Any?>)?.get("data")
        val adapters = mutableListOf<Adapter>()
        val devices = mutableListOf<Pair<String, PairedDevice>>()
        for ((path, ifaces) in tree.entries.sortedBy { it.key }) {
            val interfaces = ifaces as? Map<String, Any?> ?: continue
            (interfaces["org.bluez.Adapter1"] as? Map<String, Any?>)?.let { props ->
                val address = value(props, "Address") as? String ?: return@let
                adapters += Adapter(path, address.uppercase(), value(props, "Powered") == true)
            }
            (interfaces["org.bluez.Device1"] as? Map<String, Any?>)?.let { props ->
                if (value(props, "Paired") != true && value(props, "Bonded") != true) return@let
                val address = value(props, "Address") as? String ?: return@let
                val name = (value(props, "Alias") ?: value(props, "Name")) as? String ?: address
                // BlueZ omits Class for a device it learned of over Bluetooth LE first; its Icon still says phone.
                val cod = (value(props, "Class") as? Number)?.toInt()?.takeIf { it != 0 }
                    ?: if (value(props, "Icon") == "phone") PHONE_CLASS else 0
                devices += path to PairedDevice(name, address.uppercase(), cod)
            }
        }
        return Objects(adapters, devices)
    }

    /**
     * Connects to [service] on [address]: its RFCOMM channel from the device's SDP records (CHAIRPLAY_RFCOMM_CHANNEL
     * overrides it, for diagnosis), then an authenticated RFCOMM socket.
     */
    fun connectRfcomm(address: String, service: UUID): RfcommConnection {
        val channel = System.getenv("CHAIRPLAY_RFCOMM_CHANNEL")?.toIntOrNull()
            ?: rfcommChannel(address, service)
            ?: throw IOException("$address offers no RFCOMM channel for $service: is it an iPhone with CarPlay, and unlocked?")
        val fd = libc.socket(LibC.AF_BLUETOOTH, LibC.SOCK_STREAM, LibC.BTPROTO_RFCOMM)
        if (fd < 0) throw IOException("RFCOMM socket failed: ${LibC.error()}")
        // The iPhone's iAP2 service wants an authenticated (paired) link; an unknown level is left at the default.
        libc.setsockopt(fd, LibC.SOL_BLUETOOTH, LibC.BT_SECURITY, Memory(2).apply { setByte(0, LibC.BT_SECURITY_MEDIUM.toByte()); setByte(1, 0) }, 2)
        val sockaddr = Memory(10).apply {
            clear()
            setShort(0, LibC.AF_BLUETOOTH.toShort())
            write(2, bdaddr(address), 0, 6)
            setByte(8, channel.toByte())
        }
        if (libc.connect(fd, sockaddr, 10) != 0) {
            val error = LibC.error()
            libc.close(fd)
            throw IOException("RFCOMM connect to $address channel $channel failed: $error")
        }
        return Socket(fd)
    }

    /** The service's RFCOMM channel from the device's SDP server, or null when no record names one. */
    internal fun rfcommChannel(address: String, service: UUID): Int? {
        val fd = libc.socket(LibC.AF_BLUETOOTH, LibC.SOCK_SEQPACKET, LibC.BTPROTO_L2CAP)
        if (fd < 0) throw IOException("L2CAP socket failed: ${LibC.error()}")
        try {
            val timeout = LibC.timeval(10_000)
            libc.setsockopt(fd, LibC.SOL_SOCKET, LibC.SO_RCVTIMEO, timeout, 16)
            libc.setsockopt(fd, LibC.SOL_SOCKET, LibC.SO_SNDTIMEO, timeout, 16)
            // struct sockaddr_l2: family, PSM (little-endian), bdaddr, CID, address type (BR/EDR).
            val sockaddr = Memory(14).apply {
                clear()
                setShort(0, LibC.AF_BLUETOOTH.toShort())
                setByte(2, Sdp.PSM.toByte())
                setByte(3, 0)
                write(4, bdaddr(address), 0, 6)
            }
            if (libc.connect(fd, sockaddr, 14) != 0) throw IOException("SDP connect to $address failed: ${LibC.error()}")
            val lists = java.io.ByteArrayOutputStream()
            var continuation = ByteArray(0)
            var transaction = 1
            do {
                val request = Sdp.request(transaction, service, continuation)
                if (libc.write(fd, request, request.size.toLong()) != request.size.toLong()) throw IOException("SDP send failed: ${LibC.error()}")
                val buffer = ByteArray(4096)
                val count = libc.read(fd, buffer, buffer.size.toLong())
                if (count <= 0) throw IOException("SDP receive failed: ${LibC.error()}")
                val part = Sdp.response(buffer.copyOf(count.toInt()), transaction)
                lists.write(part.bytes)
                continuation = part.continuation
                transaction++
            } while (continuation.isNotEmpty() && transaction < 64)
            val joined = lists.toByteArray()
            return if (joined.isEmpty()) null else Sdp.rfcommChannels(joined).firstOrNull()
        } finally {
            libc.close(fd)
        }
    }

    /** bdaddr_t: the address' six bytes, least significant first. */
    internal fun bdaddr(address: String): ByteArray {
        val parts = address.split(":")
        require(parts.size == 6) { "not a Bluetooth address: $address" }
        return ByteArray(6) { parts[5 - it].toInt(16).toByte() }
    }

    private class Socket(fd: Int) : RfcommConnection {
        private val streams = FdStreams(fd, "RFCOMM")

        override fun close() = streams.close()

        override fun toDuplexStream(onDiagnostic: (String) -> Unit): BlockingDuplexByteStream =
            BluetoothRfcommDuplexStream(
                object : BluetoothRfcommSocketAccess {
                    override fun inputStream(): InputStream = streams.input
                    override fun outputStream(): OutputStream = streams.output
                    override fun close() = streams.close()
                },
                onDiagnostic,
            )
    }
}
