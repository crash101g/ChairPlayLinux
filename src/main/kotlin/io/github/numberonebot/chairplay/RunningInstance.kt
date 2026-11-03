// ====================================================================================================
//   UNOFFICIAL LINUX PORT -- THIS IS NOT THE ORIGINAL PROJECT
//
//   ChairPlay is written by Oleksiy Strelets (NumberOneBot), GPL-3.0:
//       https://github.com/NumberOneBot/ChairPlay
//   This file was ADDED by an unofficial port of ChairPlay to Ubuntu-family Linux, made for
//   personal use only. The port is not affiliated with or endorsed by the ChairPlay author, DiPlay,
//   xcertplay, or Apple. Please do not report problems with this port to the original project.
//   See LINUX-PORT-NOTICE.md.
// ====================================================================================================

package io.github.numberonebot.chairplay

import java.io.Closeable
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * One running ChairPlay per app folder. Opening ChairPlay again, from the applications menu for example, hands the
 * start to the running one, which shows its windows, and the new process ends. Without this every start ran a copy of
 * its own: all of them called the iPhone over Bluetooth and announced the same AirPlay name, and the iPhone stayed
 * with whichever copy it reached first, often one whose window was out of sight.
 *
 * The running app answers on a Unix socket in its folder until its window closes. A run that is still closing has
 * stopped answering, so the start goes on and [InstanceLock] has it wait for that run's session to end.
 */
internal object RunningInstance {
    const val SOCKET = "ChairPlay.sock"
    private const val SHOW: Byte = 's'.code.toByte()
    private const val DONE: Byte = 'k'.code.toByte()
    private const val REPLY_MILLIS = 2_000L

    /** Asks a running ChairPlay in [dir] to show itself; true when one answered, and this start should end. */
    fun handOver(dir: Path): Boolean {
        val address = UnixDomainSocketAddress.of(dir.resolve(SOCKET))
        val reply = ByteBuffer.allocate(1)
        val answered = runCatching {
            SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
                channel.connect(address)
                channel.write(ByteBuffer.wrap(byteArrayOf(SHOW)))
                // A running app that hangs must not keep this start waiting long, or exit it without anything shown.
                val reader = thread(isDaemon = true, name = "instance-reply") { runCatching { channel.read(reply) } }
                reader.join(REPLY_MILLIS)
                reply.position() == 1 && reply.get(0) == DONE
            }
        }
        return answered.getOrDefault(false).also { if (it) log("ChairPlay is already running; it shows its windows instead") }
    }

    /** Held for the life of the process; the system lets go of it when the process ends. */
    private val lock = AtomicReference<InstanceLock?>()

    /**
     * Calls [then] on a thread of its own once a previous run that is still ending its session, holding the iPhone's
     * Bluetooth channel, has ended; [onWait] when there is one to wait for, at most [PREVIOUS_RUN_WAIT_MILLIS].
     */
    fun afterPreviousRun(dir: Path, onWait: () -> Unit, then: () -> Unit) = thread(isDaemon = true, name = "instance-lock") {
        lock.set(InstanceLock.acquire(dir, PREVIOUS_RUN_WAIT_MILLIS) {
            log("the previous run is still closing; waiting for it")
            onWait()
        })
        then()
    }

    private const val PREVIOUS_RUN_WAIT_MILLIS = 15_000L

    /**
     * Answers later starts in [dir] by calling [show] on a thread of its own, until closed. A socket file left by a
     * run that was killed is replaced; when the socket cannot be made, later starts run as before.
     */
    fun answer(dir: Path, show: () -> Unit): Closeable {
        val path = dir.resolve(SOCKET)
        val server = runCatching {
            Files.deleteIfExists(path)
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply { bind(UnixDomainSocketAddress.of(path)) }
        }.getOrElse {
            log("cannot answer later starts ($it); opening ChairPlay again will start another copy")
            return Closeable {}
        }
        thread(isDaemon = true, name = "instance-answer") {
            while (server.isOpen) {
                val client = runCatching { server.accept() }.getOrNull() ?: continue
                client.use {
                    val asked = ByteBuffer.allocate(1)
                    if (runCatching { it.read(asked) }.getOrDefault(-1) != 1 || asked.get(0) != SHOW) return@use
                    log("ChairPlay was opened again; showing this one")
                    runCatching(show).onFailure { error -> log("could not show the windows: $error") }
                    runCatching { it.write(ByteBuffer.wrap(byteArrayOf(DONE))) }
                }
            }
        }
        val stop = Closeable {
            runCatching { server.close() }
            runCatching { Files.deleteIfExists(path) }
        }
        // Stopped by a signal, the app never returns from its window loop: the socket goes in the shutdown, too.
        Runtime.getRuntime().addShutdownHook(Thread({ if (server.isOpen) stop.close() }, "instance-answer-end"))
        return stop
    }
}
