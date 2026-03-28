package com.example.llmedgeexample.demo.tools

import java.util.Locale

internal data class DeviceToolDemoIntent(
    val wantsCurrentTime: Boolean = false,
    val wantsBatteryStatus: Boolean = false,
    val browserUrl: String? = null,
    val batteryThresholdPercent: Int? = null,
) {
    val wantsBrowserAction: Boolean
        get() = browserUrl != null

    val needsDeterministicTools: Boolean
        get() = wantsCurrentTime || wantsBatteryStatus || wantsBrowserAction
}

internal sealed interface BrowserActionDecision {
    data object NotRequested : BrowserActionDecision

    data class Execute(
        val url: String,
    ) : BrowserActionDecision

    data class Skip(
        val url: String,
        val reason: String,
    ) : BrowserActionDecision
}

internal sealed interface BrowserActionStatus {
    data object NotRequested : BrowserActionStatus

    data class Executed(
        val url: String,
        val resultText: String,
    ) : BrowserActionStatus

    data class Skipped(
        val url: String,
        val reason: String,
    ) : BrowserActionStatus

    data class Failed(
        val url: String,
        val reason: String,
    ) : BrowserActionStatus
}

internal data class DeterministicToolSnapshot(
    val timeText: String? = null,
    val timestamp: String? = null,
    val batteryText: String? = null,
    val batteryPercent: Int? = null,
    val isCharging: Boolean? = null,
    val browserStatus: BrowserActionStatus = BrowserActionStatus.NotRequested,
)

internal object ToolCallingDemoPlanner {
    private val urlRegex = Regex("""https?://[^\s)\]}>'"]+""", RegexOption.IGNORE_CASE)

    private val timeKeywords =
        listOf(
            "what time",
            "time is it",
            "current time",
            "current date",
            "today's date",
            "date is it",
            "heure",
            "date actuelle",
            "quelle heure",
        )

    private val batteryKeywords =
        listOf(
            "battery",
            "charge",
            "charging",
            "battery level",
            "battery left",
            "batterie",
            "niveau de batterie",
            "charge restante",
        )

    private val browserActionKeywords =
        listOf(
            "open ",
            "open the browser",
            "launch ",
            "browser",
            "ouvrir",
            "navigateur",
            "launch the browser",
        )

    private val thresholdRegexes =
        listOf(
            Regex("""(?:above|over|greater than|more than|au-dessus de|sup(?:é|e)rieur à)\s*(\d{1,3})\s*%""", RegexOption.IGNORE_CASE),
            Regex("""(?:at least|minimum|min\.?|au moins)\s*(\d{1,3})\s*%""", RegexOption.IGNORE_CASE),
        )

    fun analyze(prompt: String): DeviceToolDemoIntent {
        val normalized = prompt.lowercase(Locale.ROOT)
        val browserUrl = extractUrl(prompt)
        val wantsBrowser = browserUrl != null && browserActionKeywords.any(normalized::contains)
        val threshold = extractBatteryThresholdPercent(normalized)
        val wantsBattery = batteryKeywords.any(normalized::contains) || (wantsBrowser && threshold != null)

        return DeviceToolDemoIntent(
            wantsCurrentTime = timeKeywords.any(normalized::contains),
            wantsBatteryStatus = wantsBattery,
            browserUrl = browserUrl.takeIf { wantsBrowser },
            batteryThresholdPercent = threshold,
        )
    }

    fun decideBrowserAction(
        intent: DeviceToolDemoIntent,
        allowActions: Boolean,
        batteryPercent: Int?,
    ): BrowserActionDecision {
        val url = intent.browserUrl ?: return BrowserActionDecision.NotRequested
        if (!allowActions) {
            return BrowserActionDecision.Skip(url, "Action tools are disabled in the demo.")
        }

        val threshold = intent.batteryThresholdPercent
        if (threshold == null) {
            return BrowserActionDecision.Execute(url)
        }

        if (batteryPercent == null) {
            return BrowserActionDecision.Skip(
                url,
                "The battery level was unavailable, so the demo could not evaluate the ${threshold}% threshold.",
            )
        }

        return if (batteryPercent >= threshold) {
            BrowserActionDecision.Execute(url)
        } else {
            BrowserActionDecision.Skip(url, "Battery is ${batteryPercent}% and does not exceed ${threshold}%.")
        }
    }

    fun buildSynthesisPrompt(
        userPrompt: String,
        snapshot: DeterministicToolSnapshot,
    ): String =
        buildString {
            appendLine("Answer the user's request using only the verified device-tool results below.")
            appendLine("Do not invent any battery value, timestamp, URL, or action result.")
            appendLine("If an action was skipped or failed, say that clearly and give the reason.")
            appendLine("Keep the answer concise and in plain text.")
            appendLine()
            appendLine("User request:")
            appendLine(userPrompt)
            appendLine()
            appendLine("Verified tool results:")
            snapshot.timeText?.let {
                appendLine("- Current time result: $it")
            }
            snapshot.batteryText?.let {
                append("- Battery result: ")
                append(it)
                snapshot.batteryPercent?.let { percent -> append(" [batteryPercent=$percent]") }
                snapshot.isCharging?.let { charging -> append(" [isCharging=$charging]") }
                appendLine()
            }
            when (val browser = snapshot.browserStatus) {
                BrowserActionStatus.NotRequested -> appendLine("- Browser action: not requested.")
                is BrowserActionStatus.Executed -> appendLine("- Browser action: opened ${browser.url}. Tool result: ${browser.resultText}")
                is BrowserActionStatus.Skipped -> appendLine("- Browser action: not executed for ${browser.url}. Reason: ${browser.reason}")
                is BrowserActionStatus.Failed -> appendLine("- Browser action: failed for ${browser.url}. Reason: ${browser.reason}")
            }
        }.trim()

    fun buildFallbackResponse(snapshot: DeterministicToolSnapshot): String {
        val lines = mutableListOf<String>()

        snapshot.timeText?.let { lines += it }
        snapshot.batteryPercent?.let { percent ->
            val chargingText =
                snapshot.isCharging?.let { charging ->
                    if (charging) " The device is charging." else " The device is not charging."
                }.orEmpty()
            lines += "Battery level is ${percent}%.$chargingText".trim()
        } ?: snapshot.batteryText?.let(lines::add)

        when (val browser = snapshot.browserStatus) {
            BrowserActionStatus.NotRequested -> Unit
            is BrowserActionStatus.Executed -> lines += "Opened ${browser.url} in the browser."
            is BrowserActionStatus.Skipped -> lines += "Did not open ${browser.url}: ${browser.reason}"
            is BrowserActionStatus.Failed -> lines += "Could not open ${browser.url}: ${browser.reason}"
        }

        return lines.joinToString("\n").ifBlank {
            "No user-visible final answer was produced. Check the verified tool results above."
        }
    }

    fun shouldUseFallback(
        answer: String,
        intent: DeviceToolDemoIntent,
        snapshot: DeterministicToolSnapshot,
    ): Boolean {
        if (answer.isBlank()) {
            return true
        }

        val normalized = answer.lowercase(Locale.ROOT)
        if (intent.wantsBatteryStatus) {
            val batteryPercent = snapshot.batteryPercent
            if (batteryPercent != null &&
                !normalized.contains("$batteryPercent%") &&
                !normalized.contains("$batteryPercent %")
            ) {
                return true
            }
        }

        if (intent.wantsCurrentTime) {
            val timeToken = snapshot.timestamp?.substringAfter(' ', "")
            val timeValue = timeToken?.take(5).orEmpty()
            if (timeValue.isNotBlank() && !answer.contains(timeValue)) {
                return true
            }
        }

        when (val browser = snapshot.browserStatus) {
            BrowserActionStatus.NotRequested -> Unit
            is BrowserActionStatus.Executed -> {
                if (!normalized.contains(browser.url.lowercase(Locale.ROOT))) {
                    return true
                }
            }

            is BrowserActionStatus.Skipped -> {
                if (normalized.contains("opened ${browser.url.lowercase(Locale.ROOT)}")) {
                    return true
                }
            }

            is BrowserActionStatus.Failed -> {
                if (normalized.contains("opened ${browser.url.lowercase(Locale.ROOT)}")) {
                    return true
                }
            }
        }

        return false
    }

    private fun extractUrl(prompt: String): String? =
        urlRegex.find(prompt)?.value?.trimEnd('.', ',', ';', ')', ']')

    private fun extractBatteryThresholdPercent(normalizedPrompt: String): Int? =
        thresholdRegexes.firstNotNullOfOrNull { regex ->
            regex.find(normalizedPrompt)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
}
