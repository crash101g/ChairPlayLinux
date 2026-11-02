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

import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Just enough of the Bluetooth Service Discovery Protocol (Core spec Vol 3 Part B) to find the RFCOMM channel of a
 * service by its 128-bit UUID: one ServiceSearchAttributeRequest for the ProtocolDescriptorList, its continuation,
 * and the data elements of the answer. Windows resolves the channel itself when connect is given the service's UUID;
 * Linux' RFCOMM sockets need the channel number.
 */
internal object Sdp {
    const val PSM = 1
    private const val SERVICE_SEARCH_ATTRIBUTE_REQUEST = 0x06
    private const val SERVICE_SEARCH_ATTRIBUTE_RESPONSE = 0x07
    private const val ERROR_RESPONSE = 0x01
    private const val PROTOCOL_DESCRIPTOR_LIST = 0x0004
    private const val RFCOMM_UUID16 = 0x0003L

    /** A data element: a number (unsigned or signed), a UUID, a string, a boolean, a sequence, or nil. */
    sealed interface Element {
        data class Number(val value: Long) : Element
        data class Uuid(val value: UUID) : Element
        data class Text(val value: String) : Element
        data class Bool(val value: Boolean) : Element
        data class Sequence(val items: List<Element>) : Element
        data object Nil : Element
    }

    /** The SIG base UUID, which a 16- or 32-bit UUID is short for. */
    private val BASE_LSB = 0x800000805F9B34FBuL.toLong()
    private const val BASE_MSB_LOW = 0x00001000L

    fun shortUuid(value: Long): UUID = UUID((value shl 32) or BASE_MSB_LOW, BASE_LSB)

    /** The request PDU for the ProtocolDescriptorList of the records with [service], with [continuation] from before. */
    fun request(transaction: Int, service: UUID, continuation: ByteArray = ByteArray(0)): ByteArray {
        val params = ByteArrayOutputStream()
        // ServiceSearchPattern: a sequence of one UUID128.
        params.write(byteArrayOf(0x35, 17, 0x1C))
        params.write(uuidBytes(service))
        // MaximumAttributeByteCount.
        params.write(byteArrayOf(0xFF.toByte(), 0xFF.toByte()))
        // AttributeIDList: a sequence of one 16-bit attribute ID.
        params.write(byteArrayOf(0x35, 3, 0x09, (PROTOCOL_DESCRIPTOR_LIST shr 8).toByte(), PROTOCOL_DESCRIPTOR_LIST.toByte()))
        params.write(continuation.size)
        params.write(continuation)
        val body = params.toByteArray()
        return byteArrayOf(
            SERVICE_SEARCH_ATTRIBUTE_REQUEST.toByte(),
            (transaction shr 8).toByte(), transaction.toByte(),
            (body.size shr 8).toByte(), body.size.toByte(),
        ) + body
    }

    /** One response PDU: its part of the attribute lists, and the continuation state to ask the rest with (empty: done). */
    class Part(val bytes: ByteArray, val continuation: ByteArray)

    fun response(pdu: ByteArray, transaction: Int): Part {
        require(pdu.size >= 5) { "SDP response too short (${pdu.size} bytes)" }
        val id = pdu[0].toInt() and 0xff
        val tid = u16(pdu, 1)
        if (id == ERROR_RESPONSE) throw IllegalStateException("SDP error 0x%04X".format(if (pdu.size >= 7) u16(pdu, 5) else -1))
        require(id == SERVICE_SEARCH_ATTRIBUTE_RESPONSE) { "unexpected SDP PDU 0x%02X".format(id) }
        require(tid == transaction) { "SDP transaction $tid, expected $transaction" }
        val count = u16(pdu, 5)
        require(pdu.size >= 7 + count + 1) { "SDP response truncated" }
        val bytes = pdu.copyOfRange(7, 7 + count)
        val stateLength = pdu[7 + count].toInt() and 0xff
        require(pdu.size >= 8 + count + stateLength) { "SDP continuation state truncated" }
        return Part(bytes, pdu.copyOfRange(8 + count, 8 + count + stateLength))
    }

    /** The RFCOMM channels the records in the joined attribute lists name, in their order. */
    fun rfcommChannels(attributeLists: ByteArray): List<Int> {
        val (top, _) = element(attributeLists, 0)
        val records = (top as? Element.Sequence)?.items ?: return emptyList()
        return records.mapNotNull { record ->
            val pairs = (record as? Element.Sequence)?.items ?: return@mapNotNull null
            val protocols = pairs.chunked(2).firstOrNull { (it[0] as? Element.Number)?.value == PROTOCOL_DESCRIPTOR_LIST.toLong() }
                ?.getOrNull(1) as? Element.Sequence ?: return@mapNotNull null
            protocols.items.firstNotNullOfOrNull { descriptor ->
                val items = (descriptor as? Element.Sequence)?.items ?: return@firstNotNullOfOrNull null
                val uuid = (items.firstOrNull() as? Element.Uuid)?.value
                if (uuid == shortUuid(RFCOMM_UUID16)) (items.getOrNull(1) as? Element.Number)?.value?.toInt() else null
            }
        }
    }

    /** The data element at [at] and the offset after it. */
    fun element(data: ByteArray, at: Int): Pair<Element, Int> {
        val header = data[at].toInt() and 0xff
        val type = header shr 3
        val sizeIndex = header and 7
        var offset = at + 1
        val length = when (sizeIndex) {
            0 -> if (type == 0) 0 else 1
            1 -> 2
            2 -> 4
            3 -> 8
            4 -> 16
            5 -> (data[offset].toInt() and 0xff).also { offset += 1 }
            6 -> u16(data, offset).also { offset += 2 }
            else -> ((u16(data, offset).toLong() shl 16) or u16(data, offset + 2).toLong()).toInt().also { offset += 4 }
        }
        require(offset + length <= data.size) { "SDP data element overruns its buffer" }
        val end = offset + length
        val value: Element = when (type) {
            0 -> Element.Nil
            1, 2 -> Element.Number(number(data, offset, length, signed = type == 2))
            3 -> Element.Uuid(
                when (length) {
                    2, 4 -> shortUuid(number(data, offset, length, signed = false))
                    16 -> uuidOf(data, offset)
                    else -> throw IllegalArgumentException("SDP UUID of $length bytes")
                },
            )
            4, 8 -> Element.Text(String(data, offset, length, Charsets.UTF_8))
            5 -> Element.Bool(data[offset].toInt() != 0)
            6, 7 -> {
                val items = mutableListOf<Element>()
                var p = offset
                while (p < end) {
                    val (item, next) = element(data, p)
                    items += item
                    p = next
                }
                Element.Sequence(items)
            }
            else -> throw IllegalArgumentException("SDP data element type $type")
        }
        return value to end
    }

    private fun number(data: ByteArray, at: Int, length: Int, signed: Boolean): Long {
        if (length > 8) return 0 // 128-bit integers: not used here.
        var value = 0L
        for (i in 0 until length) value = (value shl 8) or (data[at + i].toLong() and 0xff)
        if (signed && length < 8) {
            val shift = 64 - length * 8
            value = (value shl shift) shr shift
        }
        return value
    }

    private fun u16(data: ByteArray, at: Int) = ((data[at].toInt() and 0xff) shl 8) or (data[at + 1].toInt() and 0xff)

    fun uuidBytes(uuid: UUID): ByteArray = ByteArray(16) { i ->
        val bits = if (i < 8) uuid.mostSignificantBits else uuid.leastSignificantBits
        (bits ushr (56 - (i % 8) * 8)).toByte()
    }

    private fun uuidOf(data: ByteArray, at: Int): UUID {
        fun long(from: Int) = (0 until 8).fold(0L) { acc, i -> (acc shl 8) or (data[from + i].toLong() and 0xff) }
        return UUID(long(at), long(at + 8))
    }
}
