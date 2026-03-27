package com.example.llmedgeexample

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlavaVisionPromptBuilderTest {

    @Test
    fun `ocr assist off uses the user prompt directly`() {
        val prompt =
            LlavaVisionPromptBuilder.buildModelPrompt(
                userPrompt = "What is happening here?",
                ocrAssistEnabled = false,
                ocrText = "ignored text",
            )

        assertEquals("What is happening here?", prompt)
    }

    @Test
    fun `ocr assist on appends OCR context when available`() {
        val prompt =
            LlavaVisionPromptBuilder.buildModelPrompt(
                userPrompt = "Read the sign",
                ocrAssistEnabled = true,
                ocrText = "OPEN 24 HOURS",
            )

        assertTrue(prompt.contains("Read the sign"))
        assertTrue(prompt.contains("Supplementary OCR text from the image:"))
        assertTrue(prompt.contains("OPEN 24 HOURS"))
        assertFalse(prompt.contains("ignored"))
    }
}
