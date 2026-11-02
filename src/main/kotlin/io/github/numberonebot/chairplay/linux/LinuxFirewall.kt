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
import io.github.numberonebot.chairplay.session.FIREWALL_RULE
import io.github.numberonebot.chairplay.session.FirewallState
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Ubuntu's firewall is ufw, installed but off by default (Pop!_OS likewise). Its rules can be read only as root, so
 * when it is on the state is unknown and the hint gives the rules to add; [addRules] adds them through pkexec, which
 * asks for the administrator's password in a dialog. firewalld (installed on some Ubuntu-based systems instead)
 * gets the same treatment with firewall-cmd commands.
 */
object LinuxFirewall {
    /** The ufw configuration, world-readable: ENABLED=yes when the firewall starts with the system. */
    private val UFW_CONF = File("/etc/ufw/ufw.conf")

    private fun ufwOn() = UFW_CONF.isFile && parseUfwEnabled(runCatching { UFW_CONF.readText() }.getOrDefault(""))

    private fun firewalldOn() =
        Host.which("firewall-cmd") != null && Host.run(listOf("firewall-cmd", "--state"), 5)?.trim() == "running"

    internal fun read(): FirewallState? {
        if (!ufwOn() && !firewalldOn()) return FirewallState(allowRule = false, blocking = emptyList(), off = true)
        return null
    }

    internal fun parseUfwEnabled(conf: String): Boolean =
        conf.lines().map { it.trim() }.firstOrNull { it.startsWith("ENABLED=") }?.substringAfter('=')?.trim('"', '\'', ' ')
            ?.equals("yes", ignoreCase = true) == true

    /**
     * The ufw commands that let the iPhone in: the AirPlay control ports, the ephemeral range the event, data and media
     * streams bind in (the kernel's ip_local_port_range), and mDNS.
     */
    fun hint(port: Int): String = if (!ufwOn() && firewalldOn()) firewalldHint(port) else ufwHint(port)

    /** The same openings for firewalld, made permanent and then loaded. */
    internal fun firewalldHint(port: Int, range: Pair<Int, Int> = ephemeralRange()): String {
        val (low, high) = range
        return "sudo firewall-cmd --permanent --add-port=$port-${port + 10}/tcp\n" +
            "sudo firewall-cmd --permanent --add-port=$low-$high/tcp\n" +
            "sudo firewall-cmd --permanent --add-port=$low-$high/udp\n" +
            "sudo firewall-cmd --permanent --add-service=mdns\n" +
            "sudo firewall-cmd --reload"
    }

    internal fun ufwHint(port: Int): String {
        val (low, high) = ephemeralRange()
        val comment = "comment '$FIREWALL_RULE'"
        return "sudo ufw allow proto tcp to any port $port:${port + 10} $comment\n" +
            "sudo ufw allow proto tcp to any port $low:$high $comment\n" +
            "sudo ufw allow proto udp to any port $low:$high $comment\n" +
            "sudo ufw allow 5353/udp $comment"
    }

    internal fun ephemeralRange(text: String? = runCatching { File("/proc/sys/net/ipv4/ip_local_port_range").readText() }.getOrNull()): Pair<Int, Int> {
        val parts = text?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toIntOrNull() }.orEmpty()
        return if (parts.size == 2 && parts[0] in 1..parts[1]) parts[0] to parts[1] else 32768 to 60999
    }

    /** Runs the [commands] [hint] wrote (sudo dropped) as root through pkexec; true when every one succeeded. */
    fun addRules(commands: String): Boolean {
        val pkexec = Host.which("pkexec") ?: return false
        val script = commands.lines().map { it.trim().removePrefix("sudo ").trim() }.filter { it.isNotEmpty() }.joinToString(" && ")
        if (script.isEmpty()) return false
        return try {
            val process = ProcessBuilder(pkexec, "/bin/sh", "-c", script)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            process.outputStream.close()
            if (!process.waitFor(5, TimeUnit.MINUTES)) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
        } catch (_: Exception) {
            false
        }
    }
}
