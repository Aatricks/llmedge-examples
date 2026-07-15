package com.example.llmedgeexample.common

import android.os.SystemClock

class StepEtaEstimator(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {
    data class Snapshot(val percent: Int, val label: String)

    private var lastStepTime: Long? = null
    private var hasDiscardedFirstDelta = false
    private val deltas = mutableListOf<Long>()

    fun onStep(current: Int, total: Int): Snapshot {
        val now = clock()
        val lastTime = lastStepTime
        lastStepTime = now

        if (lastTime != null) {
            val delta = now - lastTime
            if (!hasDiscardedFirstDelta) {
                hasDiscardedFirstDelta = true
            } else {
                deltas.add(delta)
                if (deltas.size > 5) {
                    deltas.removeAt(0)
                }
            }
        }

        val percent = if (total > 0) (current * 100 / total).coerceIn(1, 100) else 1
        val label = if (deltas.isEmpty()) {
            "Step $current/$total"
        } else {
            val mean = deltas.average().toLong()
            val remainingMs = mean * (total - current)
            "Step $current/$total · ~${formatSeconds(remainingMs)} left"
        }

        return Snapshot(percent, label)
    }

    fun reset() {
        lastStepTime = null
        hasDiscardedFirstDelta = false
        deltas.clear()
    }

    internal fun formatSeconds(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes == 0L) {
            "${seconds}s"
        } else if (seconds == 0L) {
            "${minutes}m"
        } else {
            "${minutes}m ${seconds}s"
        }
    }
}
