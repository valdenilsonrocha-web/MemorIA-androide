package com.memoria.mobile.ui.plans

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Card fields are formatted for DISPLAY only; the state holds raw digits.
 *
 * Formatting inside `onValueChange` looked simpler but was wrong: rewriting the
 * string mid-edit leaves the cursor where it was, so every separator the
 * formatter inserted pushed the caret out of step and the digits typed after it
 * landed scrambled. Verified by typing a known test card and reading back a
 * different number. A VisualTransformation keeps the value and the caret honest
 * because the text itself never changes — only how it is painted.
 */

/** 5031433215406351 -> 5031 4332 1540 6351 */
class CardNumberTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(19)
        val formatted = digits.chunked(4).joinToString(" ")
        return TransformedText(AnnotatedString(formatted), GroupOffsetMapping(digits.length, groupSize = 4))
    }
}

/** 1130 -> 11/30 */
class ExpiryTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(4)
        val formatted = if (digits.length <= 2) digits else "${digits.take(2)}/${digits.drop(2)}"
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = if (offset <= 2) offset else offset + 1
            override fun transformedToOriginal(offset: Int): Int = if (offset <= 2) offset else offset - 1
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

/** 12345678909 -> 123.456.789-09 */
class CpfTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(11)
        val formatted = buildString {
            digits.forEachIndexed { index, c ->
                if (index == 3 || index == 6) append('.')
                if (index == 9) append('-')
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 3 -> offset
                offset <= 6 -> offset + 1
                offset <= 9 -> offset + 2
                else -> offset + 3
            }

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 3 -> offset
                offset <= 7 -> offset - 1
                offset <= 11 -> offset - 2
                else -> offset - 3
            }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

/**
 * Maps caret positions across evenly spaced separators, clamped to the text that
 * actually exists — an unclamped mapping crashes Compose when the caret sits at
 * the very end.
 */
private class GroupOffsetMapping(
    private val length: Int,
    private val groupSize: Int,
) : OffsetMapping {

    override fun originalToTransformed(offset: Int): Int {
        val clamped = offset.coerceIn(0, length)
        val separators = if (clamped == 0) 0 else (clamped - 1) / groupSize
        return clamped + separators
    }

    override fun transformedToOriginal(offset: Int): Int {
        val separators = offset / (groupSize + 1)
        return (offset - separators).coerceIn(0, length)
    }
}
