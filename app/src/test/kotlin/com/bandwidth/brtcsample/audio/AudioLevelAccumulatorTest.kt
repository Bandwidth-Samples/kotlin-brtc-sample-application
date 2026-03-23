package com.bandwidth.brtcsample.audio

import com.bandwidth.brtcsample.viewmodel.AudioLevelAccumulator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Comprehensive tests for AudioLevelAccumulator.
 *
 * Covers:
 * - RMS calculation correctness
 * - dB conversion and normalization
 * - Frame threshold gating (9600 samples)
 * - Edge cases (silence, max amplitude, single sample)
 * - Thread-safety under concurrent accumulation
 * - Reset behavior and state isolation
 * - Deterministic output across repeated runs
 */
class AudioLevelAccumulatorTest {

    private lateinit var accumulator: AudioLevelAccumulator

    @Before
    fun setUp() {
        accumulator = AudioLevelAccumulator()
    }

    // =========================================================================
    // Basic functionality
    // =========================================================================

    @Test
    fun `getLevel returns null when insufficient samples accumulated`() {
        // Accumulate fewer than 9600 samples
        accumulator.accumulate(FloatArray(9599) { 0.5f })
        assertNull("Should return null with fewer than 9600 samples", accumulator.getLevel())
    }

    @Test
    fun `getLevel returns value when exactly 9600 samples accumulated`() {
        accumulator.accumulate(FloatArray(9600) { 0.5f })
        val level = accumulator.getLevel()
        assertNotNull("Should return a level with exactly 9600 samples", level)
    }

    @Test
    fun `getLevel returns value when more than 9600 samples accumulated`() {
        accumulator.accumulate(FloatArray(10000) { 0.5f })
        val level = accumulator.getLevel()
        assertNotNull("Should return a level with more than 9600 samples", level)
    }

    @Test
    fun `getLevel resets internal state after returning a value`() {
        accumulator.accumulate(FloatArray(9600) { 0.5f })
        val first = accumulator.getLevel()
        assertNotNull(first)

        // Second call should return null since state was reset
        val second = accumulator.getLevel()
        assertNull("Should return null after state was reset by previous getLevel", second)
    }

    @Test
    fun `getLevel does not reset state when returning null`() {
        // Add partial samples
        accumulator.accumulate(FloatArray(5000) { 0.5f })
        assertNull(accumulator.getLevel())

        // Add more to reach threshold
        accumulator.accumulate(FloatArray(5000) { 0.5f })
        val level = accumulator.getLevel()
        assertNotNull("Samples should accumulate across multiple calls", level)
    }

    // =========================================================================
    // RMS and dB calculation correctness
    // =========================================================================

    @Test
    fun `silence produces minimum level`() {
        accumulator.accumulate(FloatArray(9600) { 0f })
        val level = accumulator.getLevel()!!
        assertEquals("Silence should produce level 0", 0f, level, 0.01f)
    }

    @Test
    fun `full amplitude signal produces maximum level`() {
        accumulator.accumulate(FloatArray(9600) { 1.0f })
        val level = accumulator.getLevel()!!
        assertEquals("Full amplitude should produce level 1.0", 1.0f, level, 0.01f)
    }

    @Test
    fun `negative full amplitude produces same level as positive`() {
        val accPos = AudioLevelAccumulator()
        val accNeg = AudioLevelAccumulator()

        accPos.accumulate(FloatArray(9600) { 1.0f })
        accNeg.accumulate(FloatArray(9600) { -1.0f })

        val levelPos = accPos.getLevel()!!
        val levelNeg = accNeg.getLevel()!!

        assertEquals("Negative amplitude should produce same level as positive",
            levelPos, levelNeg, 0.001f)
    }

    @Test
    fun `known RMS value produces expected dB level`() {
        // RMS of 0.1 -> dB = 20*log10(0.1) = -20 dB
        // level = max(0, min(1, (-20 + 70) / 70)) = 50/70 = 0.714
        val amplitude = 0.1f
        accumulator.accumulate(FloatArray(9600) { amplitude })
        val level = accumulator.getLevel()!!

        val expectedRms = amplitude
        val expectedDb = 20f * log10(max(expectedRms, 1e-7f))
        val expectedLevel = max(0f, min(1f, (expectedDb + 70f) / 70f))

        assertEquals("Level should match expected dB-to-level conversion",
            expectedLevel, level, 0.001f)
    }

    @Test
    fun `alternating signal produces correct RMS`() {
        // Alternating +0.5 and -0.5: RMS should be 0.5
        val samples = FloatArray(9600) { if (it % 2 == 0) 0.5f else -0.5f }
        accumulator.accumulate(samples)
        val level = accumulator.getLevel()!!

        val expectedRms = 0.5f
        val expectedDb = 20f * log10(max(expectedRms, 1e-7f))
        val expectedLevel = max(0f, min(1f, (expectedDb + 70f) / 70f))

        assertEquals(expectedLevel, level, 0.001f)
    }

    @Test
    fun `very small amplitude clamps to near zero`() {
        // 1e-8 amplitude -> dB = 20*log10(1e-7) = -140 dB (clamped by epsilon)
        // level = max(0, (-140 + 70) / 70) = max(0, -1) = 0
        accumulator.accumulate(FloatArray(9600) { 1e-8f })
        val level = accumulator.getLevel()!!
        assertEquals("Very small amplitude should clamp to 0", 0f, level, 0.01f)
    }

    @Test
    fun `level output is always between 0 and 1`() {
        // Test various amplitudes
        val amplitudes = listOf(0f, 0.001f, 0.01f, 0.1f, 0.25f, 0.5f, 0.75f, 1.0f, 2.0f)

        for (amp in amplitudes) {
            val acc = AudioLevelAccumulator()
            acc.accumulate(FloatArray(9600) { amp })
            val level = acc.getLevel()!!
            assertTrue("Level $level should be >= 0 for amplitude $amp", level >= 0f)
            assertTrue("Level $level should be <= 1 for amplitude $amp", level <= 1f)
        }
    }

    @Test
    fun `amplitude above 1 is clamped to max level`() {
        // amplitude > 1.0: dB > 0, level = min(1, (dB + 70) / 70) = 1.0
        accumulator.accumulate(FloatArray(9600) { 5.0f })
        val level = accumulator.getLevel()!!
        assertEquals("Amplitude > 1 should still clamp to 1.0", 1.0f, level, 0.01f)
    }

    // =========================================================================
    // Multiple accumulation batches
    // =========================================================================

    @Test
    fun `accumulate across multiple small batches`() {
        val batchSize = 480 // Typical audio frame size
        val numBatches = 20 // Total: 9600

        for (i in 0 until numBatches) {
            accumulator.accumulate(FloatArray(batchSize) { 0.5f })
        }

        val level = accumulator.getLevel()
        assertNotNull("Should produce level after multiple small batches", level)
    }

    @Test
    fun `accumulate mixed amplitude batches produces correct weighted average`() {
        // 4800 samples at 0.0 and 4800 samples at 1.0
        // sumSq = 0 + 4800 = 4800
        // RMS = sqrt(4800/9600) = sqrt(0.5) ≈ 0.707
        accumulator.accumulate(FloatArray(4800) { 0f })
        accumulator.accumulate(FloatArray(4800) { 1.0f })

        val level = accumulator.getLevel()!!
        val expectedRms = sqrt(0.5f)
        val expectedDb = 20f * log10(max(expectedRms, 1e-7f))
        val expectedLevel = max(0f, min(1f, (expectedDb + 70f) / 70f))

        assertEquals(expectedLevel, level, 0.01f)
    }

    @Test
    fun `single sample batches accumulate correctly`() {
        for (i in 0 until 9600) {
            accumulator.accumulate(FloatArray(1) { 0.3f })
        }

        val level = accumulator.getLevel()
        assertNotNull("Should work with single-sample batches", level)
    }

    // =========================================================================
    // Reset behavior
    // =========================================================================

    @Test
    fun `reset clears accumulated state`() {
        accumulator.accumulate(FloatArray(5000) { 0.5f })
        accumulator.reset()

        assertNull("Should return null after reset with partial data",
            accumulator.getLevel())
    }

    @Test
    fun `reset followed by fresh accumulation works correctly`() {
        accumulator.accumulate(FloatArray(9600) { 0.9f })
        accumulator.reset()

        accumulator.accumulate(FloatArray(9600) { 0.1f })
        val level = accumulator.getLevel()!!

        val expectedRms = 0.1f
        val expectedDb = 20f * log10(max(expectedRms, 1e-7f))
        val expectedLevel = max(0f, min(1f, (expectedDb + 70f) / 70f))

        assertEquals("Reset should completely clear old data", expectedLevel, level, 0.001f)
    }

    @Test
    fun `double reset is safe`() {
        accumulator.accumulate(FloatArray(9600) { 0.5f })
        accumulator.reset()
        accumulator.reset()

        assertNull(accumulator.getLevel())
    }

    @Test
    fun `reset on fresh accumulator is safe`() {
        accumulator.reset()
        assertNull(accumulator.getLevel())
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Test
    fun `empty array accumulation does not affect state`() {
        accumulator.accumulate(FloatArray(0))
        assertNull(accumulator.getLevel())

        accumulator.accumulate(FloatArray(9600) { 0.5f })
        val level = accumulator.getLevel()
        assertNotNull(level)
    }

    @Test
    fun `NaN samples handled gracefully`() {
        // NaN * NaN = NaN, which propagates through sumSq
        // This tests that the accumulator doesn't crash
        accumulator.accumulate(FloatArray(9600) { Float.NaN })
        val level = accumulator.getLevel()
        // Level will be NaN, but shouldn't crash
        assertNotNull(level)
    }

    @Test
    fun `very large number of batches does not overflow`() {
        // Test with many small-amplitude batches
        for (i in 0 until 100) {
            accumulator.accumulate(FloatArray(960) { 0.01f })
        }
        // 100 * 960 = 96000 samples, well over threshold
        // Should have produced ~10 levels worth of data
        val level = accumulator.getLevel()
        assertNotNull(level)
        assertTrue("Level should be valid", level!! >= 0f && level <= 1f)
    }

    @Test
    fun `exactly at threshold boundary - 9599 then 1 more`() {
        accumulator.accumulate(FloatArray(9599) { 0.5f })
        assertNull(accumulator.getLevel())

        accumulator.accumulate(FloatArray(1) { 0.5f })
        assertNotNull("One more sample should trigger level calculation",
            accumulator.getLevel())
    }

    // =========================================================================
    // Determinism tests
    // =========================================================================

    @Test
    fun `same input produces identical output across multiple runs`() {
        val results = mutableListOf<Float>()

        repeat(10) {
            val acc = AudioLevelAccumulator()
            acc.accumulate(FloatArray(9600) { index -> (index % 100).toFloat() / 100f })
            results.add(acc.getLevel()!!)
        }

        val first = results.first()
        results.forEach { level ->
            assertEquals("All runs should produce identical output", first, level, 0f)
        }
    }

    @Test
    fun `batch size does not affect final result`() {
        // Same total data, different batch sizes should produce same result
        val samples = FloatArray(9600) { index -> (index % 100).toFloat() / 100f }

        val acc1 = AudioLevelAccumulator()
        acc1.accumulate(samples) // Single batch

        val acc2 = AudioLevelAccumulator()
        for (i in samples.indices step 480) {
            acc2.accumulate(samples.sliceArray(i until minOf(i + 480, samples.size)))
        }

        val acc3 = AudioLevelAccumulator()
        for (i in samples.indices step 1) {
            acc3.accumulate(floatArrayOf(samples[i]))
        }

        val level1 = acc1.getLevel()!!
        val level2 = acc2.getLevel()!!
        val level3 = acc3.getLevel()!!

        assertEquals("Single batch and multi-batch should match", level1, level2, 1e-5f)
        assertEquals("Single-sample batches should match", level1, level3, 1e-5f)
    }

    // =========================================================================
    // Concurrent access tests
    // =========================================================================

    @Test
    fun `concurrent accumulate calls do not corrupt state`() {
        val numThreads = 8
        val samplesPerThread = 1200 // Total: 9600
        val barrier = CyclicBarrier(numThreads)
        val latch = CountDownLatch(numThreads)
        val errors = AtomicInteger(0)

        val threads = List(numThreads) { threadIndex ->
            Thread {
                try {
                    barrier.await() // Synchronize start
                    accumulator.accumulate(FloatArray(samplesPerThread) { 0.5f })
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        latch.await()

        assertEquals("No errors during concurrent accumulation", 0, errors.get())

        val level = accumulator.getLevel()
        assertNotNull("Should produce valid level after concurrent accumulation", level)
        assertTrue("Level should be valid", level!! >= 0f && level <= 1f)
    }

    @Test
    fun `concurrent accumulate and getLevel do not deadlock`() {
        val iterations = 100
        val latch = CountDownLatch(2)
        val errors = AtomicInteger(0)
        val levels = mutableListOf<Float?>()

        val producer = Thread {
            try {
                for (i in 0 until iterations) {
                    accumulator.accumulate(FloatArray(960) { 0.5f })
                    Thread.yield()
                }
            } catch (e: Exception) {
                errors.incrementAndGet()
            } finally {
                latch.countDown()
            }
        }

        val consumer = Thread {
            try {
                for (i in 0 until iterations) {
                    val level = accumulator.getLevel()
                    if (level != null) {
                        synchronized(levels) { levels.add(level) }
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

        assertEquals("No errors during concurrent produce/consume", 0, errors.get())
        levels.forEach { level ->
            assertTrue("Each level should be in [0, 1]", level!! >= 0f && level <= 1f)
        }
    }

    @Test
    fun `concurrent reset and accumulate do not crash`() {
        val iterations = 200
        val latch = CountDownLatch(2)
        val errors = AtomicInteger(0)

        val accumulator = AudioLevelAccumulator()

        val accThread = Thread {
            try {
                for (i in 0 until iterations) {
                    accumulator.accumulate(FloatArray(480) { 0.5f })
                }
            } catch (e: Exception) {
                errors.incrementAndGet()
            } finally {
                latch.countDown()
            }
        }

        val resetThread = Thread {
            try {
                for (i in 0 until iterations) {
                    accumulator.reset()
                    Thread.yield()
                }
            } catch (e: Exception) {
                errors.incrementAndGet()
            } finally {
                latch.countDown()
            }
        }

        accThread.start()
        resetThread.start()
        latch.await()

        assertEquals("No errors during concurrent reset/accumulate", 0, errors.get())
    }

    @Test
    fun `many concurrent writers produce consistent total`() {
        val numThreads = 16
        val samplesPerThread = 600 // Total: 9600
        val barrier = CyclicBarrier(numThreads)
        val latch = CountDownLatch(numThreads)

        // All threads write the same amplitude
        val amplitude = 0.5f

        val threads = List(numThreads) {
            Thread {
                barrier.await()
                accumulator.accumulate(FloatArray(samplesPerThread) { amplitude })
                latch.countDown()
            }
        }

        threads.forEach { it.start() }
        latch.await()

        val level = accumulator.getLevel()!!

        // Expected: RMS of constant 0.5 = 0.5
        val expectedDb = 20f * log10(amplitude)
        val expectedLevel = max(0f, min(1f, (expectedDb + 70f) / 70f))

        assertEquals("Concurrent writes of same value should produce correct level",
            expectedLevel, level, 0.01f)
    }

    // =========================================================================
    // Consecutive level cycles
    // =========================================================================

    @Test
    fun `multiple consecutive level cycles produce independent results`() {
        // Cycle 1: loud signal
        accumulator.accumulate(FloatArray(9600) { 0.8f })
        val level1 = accumulator.getLevel()!!

        // Cycle 2: quiet signal
        accumulator.accumulate(FloatArray(9600) { 0.1f })
        val level2 = accumulator.getLevel()!!

        assertTrue("Loud signal should produce higher level than quiet",
            level1 > level2)
    }

    @Test
    fun `rapid accumulate-getLevel cycles are stable`() {
        val levels = mutableListOf<Float>()

        repeat(50) {
            accumulator.accumulate(FloatArray(9600) { 0.5f })
            levels.add(accumulator.getLevel()!!)
        }

        val first = levels.first()
        levels.forEach { level ->
            assertEquals("All levels with same input should be identical",
                first, level, 0f)
        }
    }
}
