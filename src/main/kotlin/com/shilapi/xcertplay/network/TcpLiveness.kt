// ====================================================================================================
//   UNOFFICIAL LINUX (POP!_OS) PORT: this file is from ChairPlay by Oleksiy Strelets
//   (https://github.com/NumberOneBot/ChairPlay, GPL-3.0) and was MODIFIED on 2026-11-02 for an
//   unofficial, personal-use port to Pop!_OS / Ubuntu Linux. Not affiliated with the original
//   project. See LINUX-PORT-NOTICE.md for what changed.
// ====================================================================================================

package com.shilapi.xcertplay.network

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import java.io.FileDescriptor
import java.io.IOException
import java.net.Socket
import java.net.SocketImpl

/**
 * Desktop stand-in for shared/.../network/TcpLiveness.kt: the same timing, so a vanished iPhone ends
 * the session in about 20 s, without treating a legitimately idle CarPlay screen as a failure.
 *
 * Windows' default keep-alive waits two hours before its first probe, and JDK 17 offers
 * TCP_KEEPIDLE/KEEPINTERVAL/KEEPCOUNT on Linux and macOS only (supportedOptions() on Windows lists
 * SO_KEEPALIVE alone). Winsock has had the same three options since Windows 10 1709, and TCP_MAXRT
 * stands in for Linux's TCP_USER_TIMEOUT, so they are set on the socket's handle directly. Reaching
 * the handle takes reflection into java.net and java.io, which the launchers open; without those
 * opens the socket keeps plain keep-alive and the diagnostic says so.
 */
object TcpLiveness {
    // <ws2ipdef.h>
    private const val IPPROTO_TCP = 6
    private const val TCP_KEEPIDLE = 3
    private const val TCP_MAXRT = 5
    private const val TCP_KEEPCNT = 16
    private const val TCP_KEEPINTVL = 17

    private interface Winsock : Library {
        fun setsockopt(socket: Long, level: Int, name: Int, value: IntByReference, length: Int): Int
        fun getsockopt(socket: Long, level: Int, name: Int, value: IntByReference, length: IntByReference): Int
        fun WSAGetLastError(): Int
    }

    private val winsock by lazy { Native.load("Ws2_32", Winsock::class.java) }

    // Linux (<netinet/tcp.h>): the same timing, with TCP_USER_TIMEOUT in milliseconds for TCP_MAXRT's seconds.
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
    private const val LINUX_TCP_KEEPIDLE = 4
    private const val LINUX_TCP_KEEPINTVL = 5
    private const val LINUX_TCP_KEEPCNT = 6
    private const val LINUX_TCP_USER_TIMEOUT = 18

    private interface Libc : Library {
        fun setsockopt(fd: Int, level: Int, name: Int, value: IntByReference, length: Int): Int
        fun getsockopt(fd: Int, level: Int, name: Int, value: IntByReference, length: IntByReference): Int
    }

    private val libc by lazy { Native.load("c", Libc::class.java) }

    private fun linuxSet(fd: Int, name: Int, value: Int) {
        if (libc.setsockopt(fd, IPPROTO_TCP, name, IntByReference(value), 4) != 0) {
            throw IOException("setsockopt($name) failed: errno ${Native.getLastError()}")
        }
    }

    private fun linuxGet(fd: Int, name: Int): Int {
        val value = IntByReference()
        if (libc.getsockopt(fd, IPPROTO_TCP, name, value, IntByReference(4)) != 0) {
            throw IOException("getsockopt($name) failed: errno ${Native.getLastError()}")
        }
        return value.value
    }

    fun configure(socket: Socket, diagnostic: (String) -> Unit) {
        socket.keepAlive = true
        try {
            val handle = handle(socket)
            if (!windows) {
                val fd = handle.toInt()
                linuxSet(fd, LINUX_TCP_KEEPIDLE, 10)
                linuxSet(fd, LINUX_TCP_KEEPINTVL, 3)
                linuxSet(fd, LINUX_TCP_KEEPCNT, 3)
                linuxSet(fd, LINUX_TCP_USER_TIMEOUT, 20_000)
                diagnostic("TCP peer health enabled idle=10s interval=3s count=3 deadline=20000ms; no video-idle timeout")
                return
            }
            set(handle, TCP_KEEPIDLE, 10)
            set(handle, TCP_KEEPINTVL, 3)
            set(handle, TCP_KEEPCNT, 3)
            // Seconds, where Linux's TCP_USER_TIMEOUT takes milliseconds.
            set(handle, TCP_MAXRT, 20)
            diagnostic("TCP peer health enabled idle=10s interval=3s count=3 deadline=20000ms; no video-idle timeout")
        } catch (failure: Exception) {
            diagnostic("TCP peer health tuning unavailable: ${failure.javaClass.simpleName}: ${failure.message}")
        }
    }

    /** Idle, interval, count and deadline as Winsock reports them back; for tests. */
    internal fun timing(socket: Socket): List<Int> {
        val handle = handle(socket)
        if (!windows) {
            val fd = handle.toInt()
            return listOf(
                linuxGet(fd, LINUX_TCP_KEEPIDLE), linuxGet(fd, LINUX_TCP_KEEPINTVL), linuxGet(fd, LINUX_TCP_KEEPCNT),
                linuxGet(fd, LINUX_TCP_USER_TIMEOUT) / 1000,
            )
        }
        return listOf(TCP_KEEPIDLE, TCP_KEEPINTVL, TCP_KEEPCNT, TCP_MAXRT).map { name ->
            val value = IntByReference()
            if (winsock.getsockopt(handle, IPPROTO_TCP, name, value, IntByReference(4)) != 0) {
                throw IOException("getsockopt($name) failed: WSA error ${winsock.WSAGetLastError()}")
            }
            value.value
        }
    }

    private fun set(handle: Long, name: Int, value: Int) {
        if (winsock.setsockopt(handle, IPPROTO_TCP, name, IntByReference(value), 4) != 0) {
            throw IOException("setsockopt($name) failed: WSA error ${winsock.WSAGetLastError()}")
        }
    }

    /** A Windows socket's FileDescriptor keeps its SOCKET in `fd`, not `handle`. */
    private fun handle(socket: Socket): Long {
        val impl = Socket::class.java.getDeclaredField("impl").apply { isAccessible = true }.get(socket)
        val descriptor = SocketImpl::class.java.getDeclaredMethod("getFileDescriptor")
            .apply { isAccessible = true }.invoke(impl) as FileDescriptor
        val fd = FileDescriptor::class.java.getDeclaredField("fd").apply { isAccessible = true }.getInt(descriptor)
        if (fd < 0) throw IOException("socket is closed")
        return fd.toLong()
    }
}
