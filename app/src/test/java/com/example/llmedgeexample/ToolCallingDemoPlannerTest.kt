package com.example.llmedgeexample

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallingDemoPlannerTest {
    @Test
    fun `analyze detects time battery browser and threshold from sample prompt`() {
        val intent =
            ToolCallingDemoPlanner.analyze(
                "What time is it and how much battery is left? " +
                    "If action tools are enabled and the battery is above 20%, " +
                    "open https://developer.android.com in the browser.",
            )

        assertTrue(intent.wantsCurrentTime)
        assertTrue(intent.wantsBatteryStatus)
        assertEquals("https://developer.android.com", intent.browserUrl)
        assertEquals(20, intent.batteryThresholdPercent)
    }

    @Test
    fun `analyze infers battery check from browser threshold even without battery keyword`() {
        val intent =
            ToolCallingDemoPlanner.analyze(
                "Open https://example.com in the browser if it is above 35%.",
            )

        assertTrue(intent.wantsBrowserAction)
        assertTrue(intent.wantsBatteryStatus)
        assertEquals(35, intent.batteryThresholdPercent)
    }

    @Test
    fun `decideBrowserAction executes when actions are enabled and threshold passes`() {
        val intent =
            DeviceToolDemoIntent(
                wantsBatteryStatus = true,
                browserUrl = "https://developer.android.com",
                batteryThresholdPercent = 20,
            )

        val decision = ToolCallingDemoPlanner.decideBrowserAction(intent, allowActions = true, batteryPercent = 49)

        assertEquals(
            BrowserActionDecision.Execute("https://developer.android.com"),
            decision,
        )
    }

    @Test
    fun `decideBrowserAction executes when battery equals threshold`() {
        val intent =
            DeviceToolDemoIntent(
                wantsBatteryStatus = true,
                browserUrl = "https://developer.android.com",
                batteryThresholdPercent = 20,
            )

        val decision = ToolCallingDemoPlanner.decideBrowserAction(intent, allowActions = true, batteryPercent = 20)

        assertEquals(
            BrowserActionDecision.Execute("https://developer.android.com"),
            decision,
        )
    }

    @Test
    fun `decideBrowserAction skips when actions are disabled`() {
        val intent = DeviceToolDemoIntent(browserUrl = "https://developer.android.com")

        val decision = ToolCallingDemoPlanner.decideBrowserAction(intent, allowActions = false, batteryPercent = 90)

        assertEquals(
            BrowserActionDecision.Skip(
                url = "https://developer.android.com",
                reason = "Action tools are disabled in the demo.",
            ),
            decision,
        )
    }

    @Test
    fun `decideBrowserAction skips when threshold is not met`() {
        val intent =
            DeviceToolDemoIntent(
                wantsBatteryStatus = true,
                browserUrl = "https://developer.android.com",
                batteryThresholdPercent = 20,
            )

        val decision = ToolCallingDemoPlanner.decideBrowserAction(intent, allowActions = true, batteryPercent = 18)

        assertEquals(
            BrowserActionDecision.Skip(
                url = "https://developer.android.com",
                reason = "Battery is 18% and does not exceed 20%.",
            ),
            decision,
        )
    }

    @Test
    fun `fallback response includes exact verified values and skipped browser reason`() {
        val response =
            ToolCallingDemoPlanner.buildFallbackResponse(
                DeterministicToolSnapshot(
                    timeText = "Current Date and Time: 2026-03-25 19:15:00",
                    timestamp = "2026-03-25 19:15:00",
                    batteryText = "Battery Level: 49%, Charging: false",
                    batteryPercent = 49,
                    isCharging = false,
                    browserStatus =
                        BrowserActionStatus.Skipped(
                            url = "https://developer.android.com",
                            reason = "Battery is 18% and does not exceed 20%.",
                        ),
                ),
            )

        assertTrue(response.contains("2026-03-25 19:15:00"))
        assertTrue(response.contains("49%"))
        assertTrue(response.contains("https://developer.android.com"))
        assertTrue(response.contains("does not exceed 20%"))
    }
}
