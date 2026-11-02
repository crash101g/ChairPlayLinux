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
import io.github.numberonebot.chairplay.ui.WebViewBackend
import io.github.numberonebot.chairplay.ui.WebViewUnavailable

/**
 * Linux has no WebView2, so the settings page opens in a browser window of its own. A Chromium-family browser
 * shows it as an app window (`--app`), without tabs or an address bar, much as it sits in the window on Windows;
 * otherwise the default browser opens it in a tab. CHAIRPLAY_BROWSER names a browser command to use instead.
 */
object LinuxBrowser {
    private val APP_BROWSERS = listOf(
        "google-chrome", "google-chrome-stable", "chromium", "chromium-browser", "brave-browser", "microsoft-edge",
        "vivaldi", "opera",
    )

    fun open(url: String) {
        System.getenv("CHAIRPLAY_BROWSER")?.takeIf { it.isNotBlank() }?.let { browser ->
            start(browser.trim().split(Regex("\\s+")) + url)
            return
        }
        val app = Host.which(*APP_BROWSERS.toTypedArray())
        if (app != null) start(listOf(app, "--app=$url", "--class=ChairPlay", "--new-window"))
        else LinuxDesktop.open(url)
    }

    private fun start(command: List<String>) {
        ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().outputStream.close()
    }

    /** The web view on Linux: none, so EmbeddedUi falls back to [open] (or leaves the controls window empty). */
    object NoWebView : WebViewBackend {
        override fun create(parent: Long): Long =
            throw WebViewUnavailable("no embedded web view on Linux; the settings open in a browser window")
        override fun navigate(view: Long, url: String) {}
        override fun setBounds(view: Long, width: Int, height: Int) {}
        override fun setVisible(view: Long, visible: Boolean) {}
        override fun focus(view: Long) {}
        override fun focusParent(parent: Long) {}
        override fun parentHasFocus(parent: Long): Boolean = true
        override fun pageHasFocus(parent: Long): Boolean = false
        override fun mouseHeld(): Boolean = false
        override fun destroy(view: Long) {}
    }
}
