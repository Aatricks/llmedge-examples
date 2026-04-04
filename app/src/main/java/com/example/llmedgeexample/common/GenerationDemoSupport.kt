package com.example.llmedgeexample.common

import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView

object GenerationDemoSupport {
    fun updateProgress(
        progressBar: ProgressBar,
        progressLabel: TextView,
        percent: Int,
        status: String,
    ) {
        progressBar.visibility = ProgressBar.VISIBLE
        progressBar.isIndeterminate = percent == 0
        if (!progressBar.isIndeterminate) {
            progressBar.progress = percent
        }
        progressLabel.text = status
    }

    fun parseIntField(
        field: EditText,
        defaultValue: Int,
        errorMessage: String,
        isValid: (Int) -> Boolean,
    ): Int? {
        val value = field.text.toString().ifBlank { defaultValue.toString() }.toIntOrNull()
        return validateField(field, value, errorMessage, isValid)
    }

    fun parseFloatField(
        field: EditText,
        defaultValue: Float,
        errorMessage: String,
        isValid: (Float) -> Boolean,
    ): Float? {
        val value = field.text.toString().ifBlank { defaultValue.toString() }.toFloatOrNull()
        return validateField(field, value, errorMessage, isValid)
    }

    fun parseLongField(
        field: EditText,
        defaultValue: Long,
        errorMessage: String,
        isValid: (Long) -> Boolean,
    ): Long? {
        val value = field.text.toString().ifBlank { defaultValue.toString() }.toLongOrNull()
        return validateField(field, value, errorMessage, isValid)
    }

    private fun <T> validateField(
        field: EditText,
        value: T?,
        errorMessage: String,
        isValid: (T) -> Boolean,
    ): T? =
        if (value == null || !isValid(value)) {
            field.error = errorMessage
            field.requestFocus()
            null
        } else {
            field.error = null
            value
        }
}
