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

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** The few libc calls the Linux side needs: Bluetooth sockets (BlueZ's kernel API) and TCP socket options. */
internal interface LibC : Library {
    fun socket(domain: Int, type: Int, protocol: Int): Int
    fun connect(fd: Int, address: Pointer, length: Int): Int
    fun read(fd: Int, buffer: ByteArray, count: Long): Long
    fun write(fd: Int, buffer: ByteArray, count: Long): Long
    fun shutdown(fd: Int, how: Int): Int
    fun close(fd: Int): Int
    fun setsockopt(fd: Int, level: Int, name: Int, value: Pointer, length: Int): Int
    fun getsockopt(fd: Int, level: Int, name: Int, value: Pointer, length: Pointer): Int
    fun strerror(errno: Int): String?

    companion object {
        val INSTANCE: LibC by lazy { Native.load("c", LibC::class.java) }

        const val AF_BLUETOOTH = 31
        const val SOCK_STREAM = 1
        const val SOCK_SEQPACKET = 5
        const val BTPROTO_L2CAP = 0
        const val BTPROTO_RFCOMM = 3
        const val SOL_SOCKET = 1
        const val SO_RCVTIMEO = 20
        const val SO_SNDTIMEO = 21
        const val SOL_BLUETOOTH = 274
        const val BT_SECURITY = 4
        const val BT_SECURITY_MEDIUM = 2
        const val IPPROTO_TCP = 6
        const val TCP_KEEPIDLE = 4
        const val TCP_KEEPINTVL = 5
        const val TCP_KEEPCNT = 6
        const val TCP_USER_TIMEOUT = 18
        const val SHUT_RDWR = 2

        /** The last call's errno, with its text. */
        fun error(): String {
            val errno = Native.getLastError()
            val text = runCatching { INSTANCE.strerror(errno) }.getOrNull()
            return if (text.isNullOrBlank()) "errno $errno" else "$text (errno $errno)"
        }

        /** An int option's value, as setsockopt takes it. */
        fun int(value: Int): Memory = Memory(4).apply { setInt(0, value) }

        /** A struct timeval of [millis], for SO_RCVTIMEO and SO_SNDTIMEO (x86-64 and arm64: two longs). */
        fun timeval(millis: Long): Memory = Memory(16).apply {
            setLong(0, millis / 1000)
            setLong(8, (millis % 1000) * 1000)
        }
    }
}

/**
 * A connected socket's file descriptor as a pair of streams. Reads block in the kernel; [close] shuts the socket down
 * first, which wakes a read blocked on another thread (a bare close does not on Linux).
 */
internal class FdStreams(private val fd: Int, private val what: String) {
    private val libc = LibC.INSTANCE

    @Volatile
    private var closed = false

    val input: InputStream = object : InputStream() {
        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (closed) return -1
            val buffer = ByteArray(len)
            val count = libc.read(fd, buffer, len.toLong())
            if (count < 0) {
                if (closed) return -1
                throw IOException("$what read failed: ${LibC.error()}")
            }
            if (count == 0L) return -1
            System.arraycopy(buffer, 0, b, off, count.toInt())
            return count.toInt()
        }
    }

    val output: OutputStream = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            var sent = 0
            while (sent < len) {
                if (closed) throw IOException("$what is closed")
                val chunk = b.copyOfRange(off + sent, off + len)
                val count = libc.write(fd, chunk, chunk.size.toLong())
                if (count < 0) throw IOException("$what write failed: ${LibC.error()}")
                sent += count.toInt()
            }
        }
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        libc.shutdown(fd, LibC.SHUT_RDWR)
        libc.close(fd)
    }
}
