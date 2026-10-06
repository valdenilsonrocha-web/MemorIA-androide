package com.memoria.mobile.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Text that never splits a word across lines. The app's big type (18sp button
 * labels, plus whatever font scale the phone uses) does not fit narrow slots —
 * two or three buttons side by side, calendar headers, tiles — and Compose then
 * breaks mid-word ("Sone/ca", "Tom/ei"). Here the size is stepped down until the
 * text fits in [maxLines] with breaks only between words; below [minScale] it
 * stops shrinking and ends with an ellipsis instead.
 *
 * Measured from the text layout (not BoxWithConstraints), so it is safe inside
 * rows that ask for intrinsic sizes. Nothing is drawn until the size settles,
 * which avoids a flash of the oversized text.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = 1,
    minScale: Float = 0.6f,
) {
    val base = style.merge(TextStyle(fontSize = fontSize, fontWeight = fontWeight, lineHeight = lineHeight))
    val baseSize = if (base.fontSize.isSpecified) base.fontSize else 14.sp
    var scale by remember(text, baseSize, maxLines) { mutableFloatStateOf(1f) }
    var settled by remember(text, baseSize, maxLines) { mutableStateOf(false) }
    Text(
        text,
        modifier = modifier.drawWithContent { if (settled) drawContent() },
        color = color,
        style = base.copy(
            fontSize = baseSize * scale,
            lineHeight = if (base.lineHeight.isSpecified) base.lineHeight * scale else TextUnit.Unspecified,
        ),
        textAlign = textAlign,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (!result.fitsWholeWords() && scale > minScale) {
                scale = (scale * 0.9f).coerceAtLeast(minScale)
            } else {
                settled = true
            }
        },
    )
}

/** No overflow, and every line break falls between words. */
private fun TextLayoutResult.fitsWholeWords(): Boolean {
    if (hasVisualOverflow) return false
    val chars = layoutInput.text.text
    for (line in 0 until lineCount - 1) {
        val end = getLineEnd(line)
        if (end in 1 until chars.length && chars[end - 1].isLetterOrDigit() && chars[end].isLetterOrDigit()) {
            return false
        }
    }
    return true
}
