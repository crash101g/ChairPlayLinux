package io.github.numberonebot.chairplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RunningInstanceTest {
    @Test(timeout = 10_000)
    fun `a start with no running ChairPlay goes on`() {
        val dir = Files.createTempDirectory("chairplay-instance")

        assertFalse(RunningInstance.handOver(dir))
    }

    // Opening ChairPlay again shows the running one instead of starting a second, which would compete for the iPhone.
    @Test(timeout = 10_000)
    fun `a start while ChairPlay runs hands over to it and shows it`() {
        val dir = Files.createTempDirectory("chairplay-instance")
        val shown = CountDownLatch(1)

        RunningInstance.answer(dir) { shown.countDown() }.use {
            assertTrue(RunningInstance.handOver(dir))
            assertTrue(shown.await(2, TimeUnit.SECONDS))
        }
    }

    // A run that is closing stops answering at once, so the next start waits for it (InstanceLock) rather than exit.
    @Test(timeout = 10_000)
    fun `a start after the running one closed goes on`() {
        val dir = Files.createTempDirectory("chairplay-instance")
        RunningInstance.answer(dir) {}.close()

        assertFalse(RunningInstance.handOver(dir))
    }

    // A run that was killed leaves its socket file behind; the next run must still answer the one after it.
    @Test(timeout = 10_000)
    fun `a run answers despite a socket file left by a killed run`() {
        val dir = Files.createTempDirectory("chairplay-instance")
        Files.createFile(dir.resolve(RunningInstance.SOCKET))
        val shown = CountDownLatch(1)

        RunningInstance.answer(dir) { shown.countDown() }.use {
            assertTrue(RunningInstance.handOver(dir))
            assertTrue(shown.await(2, TimeUnit.SECONDS))
        }
    }

    // A show that fails in the running app must not keep it from answering later starts.
    @Test(timeout = 10_000)
    fun `a failing show does not stop the answers`() {
        val dir = Files.createTempDirectory("chairplay-instance")
        var calls = 0

        RunningInstance.answer(dir) { calls++; error("no window") }.use {
            assertTrue(RunningInstance.handOver(dir))
            assertTrue(RunningInstance.handOver(dir))
        }
        assertTrue(calls == 2)
    }
}
