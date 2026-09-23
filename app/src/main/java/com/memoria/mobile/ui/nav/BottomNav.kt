package com.memoria.mobile.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memoria.mobile.ui.theme.BorderSubtle

/** One slot of the web's `<nav class="bottom-nav">`. */
internal data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    /** `aria-label` on the web; "Mais" is announced as "Mais opções". */
    val description: String = label,
)

/**
 * The web's bottom bar, in its order and with its labels: Início (`fa-home`),
 * Medicamentos (`fa-pills`), Saúde (`fa-heartbeat`), Atualizações
 * (`fa-clock-rotate-left`) and Mais (`fa-ellipsis-h`).
 */
internal val BOTTOM_TABS = listOf(
    BottomTab(Routes.HOME, "Início", Icons.Filled.Home),
    BottomTab(Routes.MEDS, "Medicamentos", Icons.Filled.Medication),
    BottomTab(Routes.HEALTH, "Saúde", Icons.Filled.MonitorHeart),
    BottomTab(Routes.HISTORY, "Atualizações", Icons.Filled.History),
    BottomTab(Routes.MORE, "Mais", Icons.Filled.MoreHoriz, description = "Mais opções"),
)

private val PendingRed = Color(0xFFB91C1C)
private val ActiveTint = Color(0x1410B981)
private val PendingIconRed = Color(0xFFDC2626)
private val PendingTopRed = Color(0xFFEF4444)

/**
 * `.bottom-nav` / `.nav-item` from `main.css`: a light gradient bar with a 2px
 * top rule; the active item gets the primary colour, a faint tint and a 3px
 * primary bar on top. "Atualizações" turns red while there are pending doses
 * (`.nav-item.has-pending`), exactly like the web.
 */
@Composable
internal fun MemoriaBottomBar(
    selectedRoute: String?,
    pendingCount: Int,
    onSelect: (BottomTab) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(colors.background, colors.surface)))
            .navigationBarsPadding(),
    ) {
        // `border-top: 2px solid var(--border-light)` — #f3f4f6, not themed.
        Box(Modifier.fillMaxWidth().height(2.dp).background(BorderSubtle))
        Row(Modifier.fillMaxWidth()) {
            BOTTOM_TABS.forEach { tab ->
                val selected = tab.route == selectedRoute
                val pending = tab.route == Routes.HISTORY && pendingCount > 0
                val description = if (pending) {
                    "Atualizações com pendências ($pendingCount)"
                } else {
                    tab.description
                }
                val contentColor = when {
                    pending -> PendingRed
                    selected -> colors.primary
                    else -> colors.onSurfaceVariant
                }
                val tint = when {
                    pending -> PendingTopRed.copy(alpha = 0.14f)
                    // `.nav-item.active { background-color: rgba(16, 185, 129, 0.08) }`
                    selected -> ActiveTint
                    else -> Color.Transparent
                }
                val topBar = when {
                    pending -> PendingTopRed
                    selected -> colors.primary
                    else -> Color.Transparent
                }
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 88.dp)
                        .background(tint)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(tab) },
                        )
                        .semantics { contentDescription = description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(3.dp).background(topBar))
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = null,
                            tint = if (pending) PendingIconRed else contentColor,
                            modifier = Modifier.size(28.dp),
                        )
                        NavLabel(tab.label, contentColor)
                    }
                }
            }
        }
    }
}

/**
 * The item's `<span>` at the web's 13px. On a 360dp phone each of the five slots
 * is ~70dp wide, narrower than "Medicamentos"/"Atualizações" at 13sp, so the
 * label keeps one line (no wrap, no clip into the neighbour): letter spacing is
 * tightened first and, only if that is not enough (or the user runs a large font
 * scale), the size is shrunk just enough to fit — the text itself never changes.
 */
@Composable
private fun NavLabel(text: String, color: Color) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val base = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp)
        val maxPx = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val density = LocalDensity.current
        val style = remember(text, maxPx, density) {
            val width = measurer.measure(text, base, softWrap = false, maxLines = 1).size.width
            if (width <= maxPx || width == 0) base else base.copy(fontSize = 13.sp * (maxPx.toFloat() / width * 0.97f))
        }
        Text(
            text,
            color = color,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}