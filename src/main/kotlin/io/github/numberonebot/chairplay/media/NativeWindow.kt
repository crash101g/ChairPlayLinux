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

package io.github.numberonebot.chairplay.media

import io.github.numberonebot.chairplay.Host
import org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window

/**
 * The GLFW [window]'s native handle for the Windows-only window hooks (title bar colour, close source, drag, the
 * WebView2 child): its HWND on Windows, 0 elsewhere, where each hook does nothing. glfwGetWin32Window does not exist
 * in Linux' GLFW, so it is never called there.
 */
internal fun nativeWindow(window: Long): Long = if (Host.isWindows) glfwGetWin32Window(window) else 0L
