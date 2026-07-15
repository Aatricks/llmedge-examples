package com.example.llmedgeexample.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class StepEtaEstimatorTest {

    @Test
    fun `first step has no ETA in label`() {
        var currentTime = 1000L
        val clock = { currentTime }
        val estimator = StepEtaEstimator(clock)

        val snapshot = estimator.onStep(1, 20)
        assertEquals("Step 1/20", snapshot.label)
        assertEquals(5, snapshot.percent)
    }

    @Test
    fun `steady 2000ms cadence at step 10 of 20 calculates remaining time`() {
        var currentTime = 1000L
        val clock = { currentTime }
        val estimator = StepEtaEstimator(clock)

        // Step 1: registers start time
        estimator.onStep(1, 20)

        // Step 2: delta = 2000ms (discarded as warmup)
        currentTime += 2000L
        val snap2 = estimator.onStep(2, 20)
        assertEquals("Step 2/20", snap2.label)

        // Step 3: delta = 2000ms (retained). Total steps = 20, current = 3. Remaining steps = 17.
        // Remaining time = 2000ms * 17 = 34000ms -> "34s".
        currentTime += 2000L
        val snap3 = estimator.onStep(3, 20)
        assertEquals("Step 3/20 · ~34s left", snap3.label)

        // Step 10: let's advance to step 10 with 2000ms cadence
        for (step in 4..10) {
            currentTime += 2000L
            val snap = estimator.onStep(step, 20)
            if (step == 10) {
                // Remaining steps = 10. Remaining time = 2000ms * 10 = 20000ms -> "20s".
                assertEquals("Step 10/20 · ~20s left", snap.label)
                assertEquals(50, snap.percent)
            }
        }
    }

    @Test
    fun `warmup delta is excluded from the mean`() {
        var currentTime = 1000L
        val clock = { currentTime }
        val estimator = StepEtaEstimator(clock)

        // Step 1
        estimator.onStep(1, 10)

        // Step 2: warmup delta = 10000ms (discarded)
        currentTime += 10000L
        estimator.onStep(2, 10)

        // Step 3: first retained delta = 2000ms
        currentTime += 2000L
        val snap3 = estimator.onStep(3, 10)
        // Remaining steps = 7. Mean delta should be 2000ms.
        assertTrue(snap3.label.contains("14s"))
        assertFalse(snap3.label.contains("42s"))
    }

    @Test
    fun `reset clears state`() {
        var currentTime = 1000L
        val clock = { currentTime }
        val estimator = StepEtaEstimator(clock)

        estimator.onStep(1, 10)
        currentTime += 2000L
        estimator.onStep(2, 10)
        currentTime += 2000L
        val snap3 = estimator.onStep(3, 10)
        assertTrue(snap3.label.contains("left")) // has ETA

        estimator.reset()
        // Should have no ETA again as if new
        val snapReset = estimator.onStep(1, 10)
        assertEquals("Step 1/10", snapReset.label)
    }

    @Test
    fun `percent is clamped between 1 and 100`() {
        val estimator = StepEtaEstimator(clock = { 0L })

        // 0% gets clamped to 1%
        val snap0 = estimator.onStep(0, 100)
        assertEquals(1, snap0.percent)

        // 100% is 100
        val snap100 = estimator.onStep(100, 100)
        assertEquals(100, snap100.percent)

        // >100% gets clamped to 100%
        val snapOver = estimator.onStep(110, 100)
        assertEquals(100, snapOver.percent)
    }
}
