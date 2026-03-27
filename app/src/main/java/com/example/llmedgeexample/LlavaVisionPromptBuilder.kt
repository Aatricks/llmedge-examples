package com.example.llmedgeexample

internal object LlavaVisionPromptBuilder {
    private const val DEFAULT_PROMPT = "Describe the image."

    fun buildModelPrompt(
        userPrompt: String,
        ocrAssistEnabled: Boolean,
        ocrText: String?,
    ): String {
        val normalizedPrompt = userPrompt.trim().ifBlank { DEFAULT_PROMPT }
        if (!ocrAssistEnabled) {
            return normalizedPrompt
        }

        val ocrSnippet = ocrText?.trim().orEmpty().take(1000)
        if (ocrSnippet.isBlank()) {
            return normalizedPrompt
        }

        return buildString {
            appendLine(normalizedPrompt)
            appendLine()
            appendLine("Supplementary OCR text from the image:")
            append(ocrSnippet)
        }
    }
}
