// ====================================================================================================
//   UNOFFICIAL LINUX (POP!_OS) PORT: this file is from ChairPlay by Oleksiy Strelets
//   (https://github.com/NumberOneBot/ChairPlay, GPL-3.0) and was MODIFIED on 2026-11-02 for an
//   unofficial, personal-use port to Pop!_OS / Ubuntu Linux. Not affiliated with the original
//   project. See LINUX-PORT-NOTICE.md for what changed.
// ====================================================================================================

package io.github.numberonebot.chairplay.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloseSourceTest {
    private val source = CloseSource()

    // The close button and Alt+F4 come as WM_SYSCOMMAND SC_CLOSE, which Windows then turns into WM_CLOSE: the person
    // closing the window, which only hides.
    @Test
    fun `a close after SC_CLOSE is the person's`() {
        org.junit.Assume.assumeTrue(io.github.numberonebot.chairplay.Host.isWindows)
        source.systemCommand(CloseSource.SC_CLOSE)

        assertTrue(source.userClosed())
    }

    // taskkill without /F posts WM_CLOSE alone, to one of the app's windows: the app is asked to end.
    @Test
    fun `a close with no SC_CLOSE before it comes from outside`() {
        org.junit.Assume.assumeTrue(io.github.numberonebot.chairplay.Host.isWindows)
        assertFalse(source.userClosed())
    }

    // The low four bits of the command are Windows' own; another command is no close.
    @Test
    fun `only SC_CLOSE counts, whatever its low bits, and once`() {
        org.junit.Assume.assumeTrue(io.github.numberonebot.chairplay.Host.isWindows)
        source.systemCommand(CloseSource.SC_CLOSE or 0x2)
        assertTrue(source.userClosed())
        assertFalse(source.userClosed())

        source.systemCommand(0xF020)
        assertFalse(source.userClosed())
    }

    @Test
    fun `on Linux every close of a window is the user's`() {
        org.junit.Assume.assumeTrue(!io.github.numberonebot.chairplay.Host.isWindows)
        assertTrue(CloseSource().userClosed())
    }
}
