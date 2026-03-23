package com.bandwidth.brtcsample.audio

import com.bandwidth.brtcsample.viewmodel.AudioLevelAccumulator
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Audio engine integration tests that simulate real-world audio processing scenarios.
 *
 * Covers:
 * - Simulated real-time audio frame processing at 48kHz
 * - Producer-consumer pattern (audio capture → level display)
 * - Sustained audio processing over many frames
 * - Audio level transition patterns (silence → speech → silence)
 * - Multiple simultaneous audio streams (local + remote)
 * - Audio frame timing and ordering
 * - Waveform display buffer management
 * - Audio level accumulator coordination between local and remote
 * - Edge cases: very fast/slow audio delivery, burst patterns
 */
class AudioEngineIntegrationTest {

    // =========================================================================
    // Simulated real-time audio processing
    // =========================================================================

    @Test
    fun `simulate 48kHz audio stream producing levels at expected rate`() {
        val acc = AudioLevelAccumulator()
        val frameSize = 480 // 10ms at 48kHz
        val framesForOneLevel = 9600 / frameSize // 20 frames needed

        var levelsProduced = 0

        // Simulate 1 second of audio (100 frames of 10ms)
        for (frame in 0 until 100) {
            acc.accumulate(FloatArray(frameSize) { 0.3f })
            if (acc.getLevel() != null) {
                levelsProduced++
            }
        }

        // 100 frames * 480 samples = 48000 samples
        // 48000 / 9600 = 5 levels expected
        assertEquals("Should produce 5 levels per second of audio", 5, levelsProduced)
    }

    @Test
    fun `simulate varying frame sizes from audio engine`() {
        val acc = AudioLevelAccumulator()
        val levels = mutableListOf<Float>()

        // Audio engines sometimes deliver varying frame sizes
        val frameSizes = listOf(480, 960, 480, 1920, 480, 480, 960, 480, 480, 480, 480, 480, 480, 480, 480, 480, 480, 480, 480, 480)
        // Total = 480*16 + 960*2 + 1920 = 7680 + 1920 + 1920 = 11520

        for (size in frameSizes) {
            acc.accumulate(FloatArray(size) { 0.5f })
            acc.getLevel()?.let { levels.add(it) }
        }

        assertTrue("Should produce at least one level", levels.isNotEmpty())
        levels.forEach { assertTrue("Level should be valid", it in 0f..1f) }
    }

    @Test
    fun `simulate speech pattern - silence to speech to silence`() {
        val acc = AudioLevelAccumulator()
        val levels = mutableListOf<Float>()
        val frameSize = 480

        // Phase 1: Silence (200ms = 20 frames)
        repeat(20) {
            acc.accumulate(FloatArray(frameSize) { 0f })
            acc.getLevel()?.let { levels.add(it) }
        }

        // Phase 2: Speech (500ms = 50 frames)
        repeat(50) {
            acc.accumulate(FloatArray(frameSize) { 0.4f })
            acc.getLevel()?.let { levels.add(it) }
        }

        // Phase 3: Silence again (200ms = 20 frames)
        repeat(20) {
            acc.accumulate(FloatArray(frameSize) { 0f })
            acc.getLevel()?.let { levels.add(it) }
        }

        assertTrue("Should produce multiple levels", levels.size >= 3)

        // Find transition: silence levels should be lower than speech levels
        val silenceLevels = levels.filter { it < 0.1f }
        val speechLevels = levels.filter { it > 0.3f }

        assertTrue("Should have some silence levels", silenceLevels.isNotEmpty())
        assertTrue("Should have some speech levels", speechLevels.isNotEmpty())
    }

    @Test
    fun `simulate gradual volume increase`() {
        val acc = AudioLevelAccumulator()
        val levels = mutableListOf<Float>()
        val frameSize = 480

        // Gradually increase amplitude over 200 frames (2 seconds)
        for (frame in 0 until 200) {
            val amplitude = frame.toFloat() / 200f // 0.0 to ~1.0
            acc.accumulate(FloatArray(frameSize) { amplitude })
            acc.getLevel()?.let { levels.add(it) }
        }

        assertTrue("Should produce multiple levels", levels.size >= 5)

        // Levels should generally trend upward
        for (i in 1 until levels.size) {
            // Allow some tolerance for the windowing effect
            if (i > 2) { // Skip first few as they may include initial silence
                assertTrue("Level at $i should be >= level at ${i-2} (trending up)",
                    levels[i] >= levels[max(0, i - 3)] - 0.1f)
            }
        }
    }

    // =========================================================================
    // Dual stream processing (local + remote)
    // =========================================================================

    @Test
    fun `local and remote accumulators process independently`() {
        val localAcc = AudioLevelAccumulator()
        val remoteAcc = AudioLevelAccumulator()
        val frameSize = 480

        val localLevels = mutableListOf<Float>()
        val remoteLevels = mutableListOf<Float>()

        // Local: speaking (loud)
        // Remote: quiet background
        repeat(40) {
            localAcc.accumulate(FloatArray(frameSize) { 0.7f })
            remoteAcc.accumulate(FloatArray(frameSize) { 0.1f })

            localAcc.getLevel()?.let { localLevels.add(it) }
            remoteAcc.getLevel()?.let { remoteLevels.add(it) }
        }

        assertTrue(localLevels.isNotEmpty())
        assertTrue(remoteLevels.isNotEmpty())

        val avgLocal = localLevels.average()
        val avgRemote = remoteLevels.average()

        assertTrue("Local (loud) should have higher average than remote (quiet)",
            avgLocal > avgRemote)
    }

    @Test
    fun `local and remote accumulators can be reset independently`() {
        val localAcc = AudioLevelAccumulator()
        val remoteAcc = AudioLevelAccumulator()

        localAcc.accumulate(FloatArray(9600) { 0.5f })
        remoteAcc.accumulate(FloatArray(9600) { 0.5f })

        localAcc.reset()

        assertNull("Local should be null after reset", localAcc.getLevel())
        assertNotNull("Remote should still have data", remoteAcc.getLevel())
    }

    @Test
    fun `concurrent local and remote processing threads`() {
        val localAcc = AudioLevelAccumulator()
        val remoteAcc = AudioLevelAccumulator()
        val frameSize = 480
        val frames = 200

        val localLevels = ConcurrentLinkedQueue<Float>()
        val remoteLevels = ConcurrentLinkedQueue<Float>()
        val latch = CountDownLatch(2)
        val errors = AtomicInteger(0)

        // Local audio thread
        Thread {
            try {
                repeat(frames) {
                    localAcc.accumulate(FloatArray(frameSize) { 0.6f })
                    localAcc.getLevel()?.let { localLevels.add(it) }
                }
            } catch (e: Exception) {
                errors.incrementAndGet()
            } finally {
                latch.countDown()
            }
        }.start()

        // Remote audio thread
        Thread {
            try {
                repeat(frames) {
                    remoteAcc.accumulate(FloatArray(frameSize) { 0.3f })
                    remoteAcc.getLevel()?.let { remoteLevels.add(it) }
                }
            } catch (e: Exception) {
                errors.incrementAndGet()
            } finally {
                latch.countDown()
            }
        }.start()

        latch.await()
        assertEquals(0, errors.get())

        assertTrue(localLevels.isNotEmpty())
        assertTrue(remoteLevels.isNotEmpty())
        localLevels.forEach { assertTrue(it in 0f..1f) }
        remoteLevels.forEach { assertTrue(it in 0f..1f) }
    }

    // =========================================================================
    // Waveform display buffer simulation
    // =========================================================================

    @Test
    fun `waveform buffer maintains rolling window of 50 levels`() {
        val acc = AudioLevelAccumulator()
        val waveform = mutableListOf<Float>()
        val capacity = 50
        val frameSize = 480

        // Generate enough frames for more than 50 levels
        // 50 levels * 20 frames/level = 1000 frames
        repeat(1200) { frame ->
            val amplitude = if (frame % 200 < 100) 0.5f else 0.1f // Alternating loud/quiet
            acc.accumulate(FloatArray(frameSize) { amplitude })

            acc.getLevel()?.let { level ->
                waveform.add(level)
                if (waveform.size > capacity) {
                    waveform.removeAt(0)
                }
            }
        }

        assertEquals("Waveform should be at capacity", capacity, waveform.size)
        // All values should be valid
        waveform.forEach { assertTrue("Each level should be valid", it in 0f..1f) }
    }

    @Test
    fun `waveform shows muted state as zero levels`() {
        val acc = AudioLevelAccumulator()
        val waveform = mutableListOf<Float>()
        var isMicEnabled = true
        val frameSize = 480

        repeat(100) { frame ->
            // Mute at frame 40
            if (frame == 40) isMicEnabled = false
            // Unmute at frame 80
            if (frame == 80) isMicEnabled = true

            acc.accumulate(FloatArray(frameSize) { 0.5f })
            acc.getLevel()?.let { level ->
                val displayLevel = if (isMicEnabled) level else 0f
                waveform.add(displayLevel)
                if (waveform.size > 50) waveform.removeAt(0)
            }
        }

        // Should have a mix of zero and non-zero levels
        val zeroLevels = waveform.count { it == 0f }
        val nonZeroLevels = waveform.count { it > 0f }

        assertTrue("Should have some zero levels from muted period", zeroLevels > 0 || nonZeroLevels > 0)
    }

    // =========================================================================
    // Edge cases in audio processing
    // =========================================================================

    @Test
    fun `handle audio engine delivering empty frames`() {
        val acc = AudioLevelAccumulator()

        // Mix of empty and valid frames
        repeat(100) { frame ->
            if (frame % 3 == 0) {
                acc.accumulate(FloatArray(0)) // Empty frame
            } else {
                acc.accumulate(FloatArray(480) { 0.5f })
            }
        }

        // Should still produce levels (empty frames are just skipped)
        // Non-empty frames: ~67 * 480 = 32160 samples → ~3 levels
        val levels = mutableListOf<Float>()
        // Drain remaining
        acc.getLevel()?.let { levels.add(it) }

        // Just verify it doesn't crash
    }

    @Test
    fun `handle burst of audio followed by silence`() {
        val acc = AudioLevelAccumulator()
        val levels = mutableListOf<Float>()

        // Burst: 50 frames of 960 samples at high amplitude
        repeat(50) {
            acc.accumulate(FloatArray(960) { 0.9f })
            acc.getLevel()?.let { levels.add(it) }
        }

        val burstLevelCount = levels.size

        // Silence: 50 frames of 960 samples
        repeat(50) {
            acc.accumulate(FloatArray(960) { 0f })
            acc.getLevel()?.let { levels.add(it) }
        }

        assertTrue("Should produce levels during burst", burstLevelCount > 0)
        assertTrue("Should produce levels during silence too", levels.size > burstLevelCount)

        // Last levels should be near zero (silence)
        val lastLevel = levels.last()
        assertTrue("Last level should be near zero for silence", lastLevel < 0.05f)
    }

    @Test
    fun `handle very small audio frames (1 sample each)`() {
        val acc = AudioLevelAccumulator()
        var levels = 0

        // 9600 single-sample frames
        repeat(9600) {
            acc.accumulate(FloatArray(1) { 0.5f })
            if (acc.getLevel() != null) levels++
        }

        assertEquals("Should produce exactly 1 level from 9600 single-sample frames", 1, levels)
    }

    @Test
    fun `handle very large audio frame`() {
        val acc = AudioLevelAccumulator()

        // Single frame with all 9600 samples
        acc.accumulate(FloatArray(9600) { 0.5f })
        val level = acc.getLevel()

        assertNotNull("Single large frame should produce a level", level)
        assertTrue("Level should be valid", level!! in 0f..1f)
    }

    @Test
    fun `handle frame larger than threshold`() {
        val acc = AudioLevelAccumulator()

        // Frame with more than 9600 samples
        acc.accumulate(FloatArray(19200) { 0.5f })
        val level = acc.getLevel()

        assertNotNull("Large frame exceeding threshold should produce a level", level)
    }

    // =========================================================================
    // Audio level accuracy verification
    // =========================================================================

    @Test
    fun `verify dB scale conversion for known amplitudes`() {
        data class TestCase(val amplitude: Float, val expectedDbApprox: Float)

        val testCases = listOf(
            TestCase(1.0f, 0f),     // 0 dB (full scale)
            TestCase(0.1f, -20f),   // -20 dB
            TestCase(0.01f, -40f),  // -40 dB
            TestCase(0.001f, -60f), // -60 dB
        )

        for (tc in testCases) {
            val acc = AudioLevelAccumulator()
            acc.accumulate(FloatArray(9600) { tc.amplitude })
            val level = acc.getLevel()!!

            // Convert back: level = (db + 70) / 70, so db = level * 70 - 70
            val inferredDb = level * 70f - 70f

            assertEquals("Amplitude ${tc.amplitude}: inferred dB should be ~${tc.expectedDbApprox}",
                tc.expectedDbApprox, inferredDb, 1f)
        }
    }

    @Test
    fun `verify monotonic level increase with increasing amplitude`() {
        var previousLevel = -1f

        val amplitudes = listOf(0.001f, 0.01f, 0.05f, 0.1f, 0.2f, 0.3f, 0.5f, 0.7f, 1.0f)

        for (amp in amplitudes) {
            val acc = AudioLevelAccumulator()
            acc.accumulate(FloatArray(9600) { amp })
            val level = acc.getLevel()!!

            assertTrue("Level for amplitude $amp ($level) should be > previous ($previousLevel)",
                level > previousLevel)
            previousLevel = level
        }
    }

    // =========================================================================
    // Producer-consumer pattern stress test
    // =========================================================================

    @Test
    fun `producer-consumer pattern produces consistent results`() {
        val acc = AudioLevelAccumulator()
        val levels = ConcurrentLinkedQueue<Float>()
        val framesTotal = 2000
        val frameSize = 480
        val running = AtomicBoolean(true)
        val latch = CountDownLatch(2)
        val errors = AtomicInteger(0)

        // Producer: simulates audio capture thread
        val producer = Thread {
            try {
                repeat(framesTotal) {
                    acc.accumulate(FloatArray(frameSize) { 0.4f })
                    Thread.yield()
                }
            } catch (e: Exception) {
                errors.incrementAndGet()
            } finally {
                running.set(false)
                latch.countDown()
            }
        }

        // Consumer: simulates UI update thread
        val consumer = Thread {
            try {
                while (running.get() || acc.getLevel() != null) {
                    acc.getLevel()?.let { level ->
                        assertTrue("Level should be valid", level in 0f..1f)
                        levels.add(level)
                    }
                    Thread.yield()
                }
            } catch (e: Exception) {
                errors.incrementAndGet()
            } finally {
                latch.countDown()
            }
        }

        producer.start()
        consumer.start()
        latch.await()

        assertEquals(0, errors.get())
        assertTrue("Should have produced some levels", levels.size > 0)

        // All levels should be consistent for the same amplitude
        val levelsList = levels.toList()
        if (levelsList.size > 1) {
            val avg = levelsList.average()
            levelsList.forEach { level ->
                // Each level should be close to the average (same input amplitude)
                assertTrue("Level $level should be close to average $avg",
                    abs(level - avg) < 0.15f)
            }
        }
    }

    // =========================================================================
    // Multiple audio engine restart cycles
    // =========================================================================

    @Test
    fun `audio engine restart simulation produces clean levels`() {
        val localAcc = AudioLevelAccumulator()
        val remoteAcc = AudioLevelAccumulator()

        repeat(5) { cycle ->
            // Start monitoring
            localAcc.reset()
            remoteAcc.reset()

            val localLevels = mutableListOf<Float>()
            val remoteLevels = mutableListOf<Float>()

            // Simulate 100 frames
            repeat(100) {
                localAcc.accumulate(FloatArray(480) { 0.5f })
                remoteAcc.accumulate(FloatArray(480) { 0.3f })

                localAcc.getLevel()?.let { localLevels.add(it) }
                remoteAcc.getLevel()?.let { remoteLevels.add(it) }
            }

            assertTrue("Cycle $cycle: should produce local levels", localLevels.isNotEmpty())
            assertTrue("Cycle $cycle: should produce remote levels", remoteLevels.isNotEmpty())

            localLevels.forEach { assertTrue(it in 0f..1f) }
            remoteLevels.forEach { assertTrue(it in 0f..1f) }
        }
    }

    @Test
    fun `accumulated state does not leak between monitoring sessions`() {
        val acc = AudioLevelAccumulator()

        // Session 1: loud audio
        acc.accumulate(FloatArray(9600) { 0.9f })
        val session1Level = acc.getLevel()!!

        // Session 2: reset and quiet audio
        acc.reset()
        acc.accumulate(FloatArray(9600) { 0.05f })
        val session2Level = acc.getLevel()!!

        // Session 2 (0.05 amplitude) should be lower than session 1 (0.9 amplitude)
        assertTrue("Session 2 level ($session2Level) should be lower than session 1 ($session1Level)",
            session2Level < session1Level)
    }
}
