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

package io.github.numberonebot.chairplay

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The operating system ChairPlay runs on. Windows is the original target; Linux (Ubuntu and its derivatives, such as
 * Pop!_OS) takes the implementations in the `linux` package wherever Windows' own APIs were called.
 */
object Host {
    val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    val isLinux: Boolean = System.getProperty("os.name").orEmpty().startsWith("Linux", ignoreCase = true)

    /** The firewall's name in messages. */
    val firewallName: String get() = if (isWindows) "Windows Firewall" else "the firewall"

    /** The first of [names] found on the PATH, or null. */
    fun which(vararg names: String): String? {
        val path = System.getenv("PATH").orEmpty().split(File.pathSeparator).filter { it.isNotEmpty() }
        return names.firstNotNullOfOrNull { name ->
            path.map { File(it, name) }.firstOrNull { it.isFile && it.canExecute() }?.path
        }
    }

    /**
     * Runs [command] and returns its standard output, or null when it could not start, failed, or took longer than
     * [timeoutSeconds]. Standard error is discarded; standard input is closed at once.
     */
    fun run(command: List<String>, timeoutSeconds: Long = 10, env: Map<String, String> = emptyMap()): String? = try {
        val output = File.createTempFile("chairplay-cmd", ".txt")
        try {
            val process = ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(output)
                .apply { environment().putAll(env) }
                .start()
            process.outputStream.close()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() != 0) {
                null
            } else {
                output.readText(Charsets.UTF_8)
            }
        } finally {
            output.delete()
        }
    } catch (_: Exception) {
        null
    }
}
