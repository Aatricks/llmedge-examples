package com.example.llmedgeexample.common

/**
 * Compatibility helper for legacy trim-memory levels.
 *
 * Android still reports these integer levels through [android.content.ComponentCallbacks2], but
 * some running-state constants are now deprecated. Centralizing the values here keeps call sites
 * warning-free while preserving the platform semantics.
 */
internal object TrimMemorySupport {
    private const val RUNNING_MODERATE = 5
    private const val RUNNING_LOW = 10
    private const val RUNNING_CRITICAL = 15
    private const val UI_HIDDEN = 20
    private const val BACKGROUND = 40
    private const val MODERATE = 60
    private const val COMPLETE = 80

    fun isRunningLow(level: Int): Boolean =
        level == RUNNING_LOW || level == RUNNING_CRITICAL

    fun isCritical(level: Int): Boolean =
        level == RUNNING_CRITICAL || level == COMPLETE

    fun isBackgroundPressure(level: Int): Boolean =
        level == BACKGROUND || level == MODERATE

    fun describe(level: Int): String =
        when (level) {
            RUNNING_MODERATE -> "RUNNING_MODERATE"
            RUNNING_LOW -> "RUNNING_LOW"
            RUNNING_CRITICAL -> "RUNNING_CRITICAL"
            UI_HIDDEN -> "UI_HIDDEN"
            BACKGROUND -> "BACKGROUND"
            MODERATE -> "MODERATE"
            COMPLETE -> "COMPLETE"
            else -> "UNKNOWN($level)"
        }
}
