package com.example.llmedgeexample.demo.image

import com.example.llmedgeexample.common.GenerationDemoSupport

internal object ImageGenerationFormSupport {
    fun parseDimensionField(
        field: android.widget.EditText,
        defaultValue: Int,
        label: String,
    ): Int? =
        GenerationDemoSupport.parseIntField(
            field = field,
            defaultValue = defaultValue,
            errorMessage = "$label must be a multiple of 8 between 128 and 1024",
        ) { value ->
            value in 128..1024 && value % 8 == 0
        }

    fun parseStepsField(
        field: android.widget.EditText,
        defaultValue: Int,
    ): Int? =
        GenerationDemoSupport.parseIntField(
            field = field,
            defaultValue = defaultValue,
            errorMessage = "Steps must be between 1 and 50",
        ) { value ->
            value in 1..50
        }

    fun parseCfgField(
        field: android.widget.EditText,
        defaultValue: Float,
    ): Float? =
        GenerationDemoSupport.parseFloatField(
            field = field,
            defaultValue = defaultValue,
            errorMessage = "CFG must be between 1.0 and 15.0",
        ) { value ->
            value in 1.0f..15.0f
        }

    fun parseSeedField(
        field: android.widget.EditText,
        defaultValue: Long,
    ): Long? =
        GenerationDemoSupport.parseLongField(
            field = field,
            defaultValue = defaultValue,
            errorMessage = "Seed must be -1 or non-negative",
        ) { value ->
            value >= -1L
        }
}
