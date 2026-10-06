package com.fiksr.inkreader.data

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

object BionicReadingHelper {

    /**
     * Converts a raw text string into a Bionic Reading AnnotatedString.
     * Bolds the fixation points (first 40-50% of characters) of each word.
     */
    fun transformToBionic(text: String): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")

        return buildAnnotatedString {
            var index = 0
            val length = text.length

            while (index < length) {
                // Skip non-letter / whitespace
                val nonWordStart = index
                while (index < length && !text[index].isLetterOrDigit()) {
                    index++
                }
                if (index > nonWordStart) {
                    append(text.substring(nonWordStart, index))
                }

                if (index >= length) break

                // Find word boundaries
                val wordStart = index
                while (index < length && text[index].isLetterOrDigit()) {
                    index++
                }
                val wordEnd = index
                val word = text.substring(wordStart, wordEnd)
                val wordLen = word.length

                // Calculate fixation length
                val boldCount = when {
                    wordLen <= 3 -> 1
                    wordLen <= 5 -> 2
                    wordLen <= 8 -> 3
                    wordLen <= 11 -> 4
                    else -> (wordLen * 0.45).toInt().coerceAtLeast(4)
                }.coerceAtMost(wordLen)

                val boldPart = word.substring(0, boldCount)
                val restPart = word.substring(boldCount)

                // Append bold part with bold span
                val startSpan = length
                val currentBuilderLen = this.length
                append(boldPart)
                addStyle(
                    style = SpanStyle(fontWeight = FontWeight.Bold),
                    start = currentBuilderLen,
                    end = currentBuilderLen + boldPart.length
                )

                if (restPart.isNotEmpty()) {
                    append(restPart)
                }
            }
        }
    }
}
