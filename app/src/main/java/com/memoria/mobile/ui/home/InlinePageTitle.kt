package com.memoria.mobile.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.memoria.mobile.ui.common.BackTopBar

/**
 * A page's `<h2>` (and `.subtitle`) placed INSIDE the scrolling content, under
 * the page-top illustration — on the web `showPageTopGuide()` *prepends*
 * `#pageTopGuide` to the page, so the image sits above the title and both
 * scroll with the page.
 *
 * It reuses [BackTopBar] (same size, colour and Font Awesome glyph per page)
 * but cancels its own 20dp side padding, so the title lines up with the
 * content of the page it sits in. [topTrim] removes part of its 20dp top
 * padding when the parent already spaces the items.
 */
@Composable
fun InlinePageTitle(title: String, subtitle: String? = null, topTrim: Dp = 0.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val bleed = 20.dp.roundToPx()
                val trim = topTrim.roundToPx()
                if (!constraints.hasBoundedWidth) {
                    val p = measurable.measure(constraints)
                    return@layout layout(p.width, p.height) { p.place(0, 0) }
                }
                val width = constraints.maxWidth + 2 * bleed
                val placeable = measurable.measure(
                    constraints.copy(minWidth = width, maxWidth = width, minHeight = 0),
                )
                val height = (placeable.height - trim).coerceAtLeast(0)
                layout(constraints.maxWidth, height) { placeable.place(-bleed, -trim) }
            },
    ) {
        BackTopBar(title = title, onBack = {}, subtitle = subtitle)
    }
}
