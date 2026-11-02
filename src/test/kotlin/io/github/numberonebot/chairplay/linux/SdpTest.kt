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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SdpTest {
    private val iap2 = UUID.fromString("00000000-deca-fade-deca-deafdecacafe")

    private fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()

    // One record: ProtocolDescriptorList = { {L2CAP}, {RFCOMM, channel} }.
    private fun attributeLists(channel: Int) = bytes(
        0x35, 0x13,
        0x35, 0x11,
        0x09, 0x00, 0x04,
        0x35, 0x0C,
        0x35, 0x03, 0x19, 0x01, 0x00,
        0x35, 0x05, 0x19, 0x00, 0x03, 0x08, channel,
    )

    private fun responsePdu(transaction: Int, part: ByteArray, continuation: ByteArray = ByteArray(0)): ByteArray {
        val params = bytes(part.size shr 8, part.size) + part + bytes(continuation.size) + continuation
        return bytes(0x07, transaction shr 8, transaction, params.size shr 8, params.size) + params
    }

    @Test
    fun `the request searches for the service's UUID128 and asks for its protocol list`() {
        val pdu = Sdp.request(0x0102, iap2)
        assertEquals(0x06, pdu[0].toInt())
        assertEquals(0x0102, ((pdu[1].toInt() and 0xff) shl 8) or (pdu[2].toInt() and 0xff))
        val length = ((pdu[3].toInt() and 0xff) shl 8) or (pdu[4].toInt() and 0xff)
        assertEquals(pdu.size - 5, length)
        // The search pattern: a sequence holding one 128-bit UUID, big-endian.
        assertArrayEquals(bytes(0x35, 17, 0x1C) + Sdp.uuidBytes(iap2), pdu.copyOfRange(5, 24))
        // Max byte count, then the attribute list { 0x0004 }, then no continuation.
        assertArrayEquals(bytes(0xFF, 0xFF, 0x35, 3, 0x09, 0x00, 0x04, 0), pdu.copyOfRange(24, pdu.size))
    }

    @Test
    fun `the RFCOMM channel is read from the protocol descriptor list`() {
        assertEquals(listOf(7), Sdp.rfcommChannels(attributeLists(7)))
    }

    @Test
    fun `a response split by continuation states joins into one list`() {
        val lists = attributeLists(12)
        val first = Sdp.response(responsePdu(1, lists.copyOfRange(0, 9), bytes(0xAB, 0xCD)), 1)
        assertArrayEquals(bytes(0xAB, 0xCD), first.continuation)
        val second = Sdp.response(responsePdu(2, lists.copyOfRange(9, lists.size)), 2)
        assertTrue(second.continuation.isEmpty())
        assertEquals(listOf(12), Sdp.rfcommChannels(first.bytes + second.bytes))
    }

    @Test
    fun `no record means no channel`() {
        assertEquals(emptyList<Int>(), Sdp.rfcommChannels(bytes(0x35, 0x00)))
    }

    @Test(expected = IllegalStateException::class)
    fun `an error response fails`() {
        Sdp.response(bytes(0x01, 0x00, 0x01, 0x00, 0x02, 0x00, 0x03), 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a response to another transaction fails`() {
        Sdp.response(responsePdu(5, attributeLists(3)), 4)
    }

    @Test
    fun `16-bit UUIDs expand on the Bluetooth base UUID`() {
        assertEquals(UUID.fromString("00000003-0000-1000-8000-00805f9b34fb"), Sdp.shortUuid(3))
        val (element, end) = Sdp.element(bytes(0x1C) + Sdp.uuidBytes(iap2), 0)
        assertEquals(Sdp.Element.Uuid(iap2), element)
        assertEquals(17, end)
    }

    @Test
    fun `lengths in the header's following bytes are honoured`() {
        // A sequence with a one-byte length (size index 5) and a string with a two-byte length (size index 6).
        val (sequence, end) = Sdp.element(bytes(0x35, 0x06, 0x26, 0x00, 0x03, 'a'.code, 'b'.code, 'c'.code), 0)
        assertEquals(Sdp.Element.Sequence(listOf(Sdp.Element.Text("abc"))), sequence)
        assertEquals(8, end)
    }
}
