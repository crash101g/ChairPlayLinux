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

package io.github.numberonebot.chairplay.bluetooth

import com.shilapi.xcertplay.transport.BlockingDuplexByteStream
import io.github.numberonebot.chairplay.Host
import io.github.numberonebot.chairplay.linux.LinuxBluetooth
import java.io.Closeable
import java.util.UUID

/** An RFCOMM connection to the iPhone's iAP2 service, on either system. */
interface RfcommConnection : Closeable {
    fun toDuplexStream(onDiagnostic: (String) -> Unit): BlockingDuplexByteStream
}

/** The PC's Bluetooth: Windows' BluetoothApis and Winsock, or BlueZ on Linux. */
object HostBluetooth {
    fun localRadioAddress(): String =
        if (Host.isWindows) WindowsBluetooth.localRadioAddress() else LinuxBluetooth.localRadioAddress()

    fun pairedDevices(): List<PairedDevice> =
        if (Host.isWindows) WindowsBluetooth.pairedDevices() else LinuxBluetooth.pairedDevices()

    fun connectRfcomm(address: String, service: UUID): RfcommConnection =
        if (Host.isWindows) WindowsBluetooth.connectRfcomm(address, service) else LinuxBluetooth.connectRfcomm(address, service)
}
