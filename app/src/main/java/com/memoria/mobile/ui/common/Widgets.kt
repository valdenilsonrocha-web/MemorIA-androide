package com.memoria.mobile.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.sp
import com.memoria.mobile.R
import com.memoria.mobile.ui.theme.SurfaceLight
import com.memoria.mobile.ui.theme.TextPrimary
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Whether [BackTopBar] draws a back arrow. The web has none: its bottom bar
 * stays on every page, so the main flow (which does the same) turns it off;
 * the sign-in flow, which has no bottom bar, keeps it.
 */
val LocalShowBackArrow = staticCompositionLocalOf { true }

/** How the web styles a page's title. */
private enum class TitleKind {
    /** `.page-header h2`: 32px, bold, `--primary-color`. */
    PAGE_HEADER,

    /** A bare `.page > h2`: the browser's 24px bold in `--text-primary`. */
    PLAIN,
}

/** Pages whose `<h2>` is NOT inside a `.page-header` on the web. */
private val PLAIN_TITLES = setOf(
    "Adicionar Medicamento", "Editar Medicamento", "Minhas Receitas", "Planos",
    "Configurações", "Histórico",
)

/** The Font Awesome glyph the web puts inside some `<h2>`s. */
private fun titleGlyph(title: String): ImageVector? = when (title) {
    "Saúde" -> Icons.Filled.MonitorHeart
    "Otimização do App" -> Icons.Filled.Battery5Bar
    "Painel do Proprietário" -> Icons.Filled.ShowChart
    "Minhas Receitas" -> Icons.Filled.Description
    else -> null
}

/**
 * A page's title as the web renders it — the `<h2>` at the top of the page,
 * on the page's own background, not a Material app bar.
 *
 * Only the premium mark of "Planos" is an image on the web; the menu PNGs
 * other screens pass as [iconRes] are not shown next to titles there, so they
 * are ignored here. "Detalhes do Medicamento" is the one page with a
 * `.btn-back` ("Voltar") above its title, and it gets it.
 */
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, iconRes: Int? = null, subtitle: String? = null) {
    val colors = MaterialTheme.colorScheme
    val showArrow = LocalShowBackArrow.current
    val webBackButton = title == "Detalhes do Medicamento"

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            // `.main-content { padding: var(--spacing-md) }` = 20px.
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
    ) {
        if (webBackButton) {
            // `.btn-back`: 12px 20px, fundo `--bg-secondary`, borda 2px, raio 16px.
            Row(
                Modifier
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceLight)
                    .border(2.dp, colors.outline, RoundedCornerShape(16.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                Text("Voltar", fontSize = 16.sp, color = TextPrimary)
            }
        } else if (showArrow) {
            IconButton(onClick = onBack, modifier = Modifier.padding(bottom = 4.dp).offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = TextPrimary)
            }
        }
        PageTitle(title, iconRes = iconRes, subtitle = subtitle)
    }
}

/**
 * Just the `<h2>` (and `.subtitle`) of a page, with no padding or insets — for a
 * screen that lays the title out inside its own scrolling content, e.g. under
 * the web's `#pageTopGuide`, which `showPageTopGuide()` prepends ABOVE the h2.
 */
@Composable
fun PageTitle(title: String, modifier: Modifier = Modifier, iconRes: Int? = null, subtitle: String? = null) {
    val kind = if (title in PLAIN_TITLES) TitleKind.PLAIN else TitleKind.PAGE_HEADER
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val glyph = titleGlyph(title)
            if (iconRes == R.drawable.ic_premium) {
                // `.premium-icon-heading`: 44px.
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
            } else if (glyph != null) {
                Icon(
                    glyph,
                    contentDescription = null,
                    tint = if (kind == TitleKind.PAGE_HEADER) colors.primary else TextPrimary,
                    modifier = Modifier.size(if (kind == TitleKind.PAGE_HEADER) 30.dp else 24.dp),
                )
            }
            Text(
                title,
                fontSize = if (kind == TitleKind.PAGE_HEADER) 32.sp else 24.sp,
                lineHeight = if (kind == TitleKind.PAGE_HEADER) 38.sp else 30.sp,
                fontWeight = FontWeight.Bold,
                color = if (kind == TitleKind.PAGE_HEADER) colors.primary else TextPrimary,
            )
        }
        if (subtitle != null) {
            Text(
                subtitle,
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 16.sp,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** A titled card — the mobile stand-in for the web's `.setting-item`/`.report-card`. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    /**
     * The web colours most section titles `--primary-color` (`.profile-section h3`,
     * `.help-section h3`, `.detail-section h3`, `.report-card h3`); Configurações'
     * `.setting-item label` and the admin panel use `--text-primary` — pass
     * [TextPrimary] there.
     */
    titleColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    // `.setting-item`: fundo branco, borda de 2px `--border-color`, raio de 16px.
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // `leading` existe para os ícones multicolor (o do Premium, por
                // exemplo): passá-los por `icon` tingi-los-ia de primary e
                // achatava-os numa silhueta só.
                if (leading != null) {
                    Box(Modifier.padding(end = 8.dp)) { leading() }
                } else if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                // `font-size: var(--font-size-large)` (20px), bold.
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = titleColor)
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

/** Label on the left, value on the right — the web's `.detail-row`. */
@Composable
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Big number over a caption — the web's `.stat-card`. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
) {
    // `.stat-card`: borda de 2px e raio de 24px (`--border-radius-lg`).
    Card(
        modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(vertical = 14.dp, horizontal = 8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            FitText(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
        }
    }
}

/**
 * Horizontal bar with the label above it — stands in for the web's chart
 * canvases. [fraction] is clamped, so a zero total cannot produce NaN width.
 */
@Composable
fun BarRow(
    label: String,
    value: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val safe = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
            if (safe > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(safe)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(color),
                )
            }
        }
    }
}

/** Fixed-width spacer used inside rows of tiles. */
@Composable
fun HGap(width: Int = 8) = Box(Modifier.width(width.dp))
