package com.backtosafety.core

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Placeholders ("e.g., 150 lbs") are thin grey text that people read to know what to type, so
 * they hold 7:1 against every fill a field sits on, in both modes. At the 4.5:1 floor the
 * anti-aliased glyphs measured nearer 3:1 on the dark field fill.
 */
class PlaceholderContrastTest {
    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(a: Long, b: Long): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun placeholdersHoldSevenToOneOnEveryFieldFill() {
        val modes = mapOf(
            "light" to listOf(DesignTokens.Light.inputPlaceholder, DesignTokens.Light.inputBackground, DesignTokens.Light.card),
            "dark" to listOf(DesignTokens.Dark.inputPlaceholder, DesignTokens.Dark.inputBackground, DesignTokens.Dark.card),
        )
        for ((mode, colors) in modes) {
            val placeholder = colors.first()
            for (fill in colors.drop(1)) {
                val ratio = contrast(placeholder, fill)
                assertTrue(ratio >= 7.0, "$mode placeholder on ${fill.toString(16)} is ${"%.2f".format(ratio)}:1")
            }
        }
    }
}
