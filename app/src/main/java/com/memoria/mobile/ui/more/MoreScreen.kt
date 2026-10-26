package com.memoria.mobile.ui.more

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.wrapContentHeight
import com.memoria.mobile.ui.theme.TextPrimary
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.R
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.nav.Routes

/**
 * How an entry's icon is drawn. The web mixes PNG illustrations (`*-menu-icon.png`)
 * with Font Awesome glyphs on a tinted tile (`.more-menu .menu-item[data-page=…] i`).
 */
private sealed interface MenuIcon {
    data class Picture(
        val res: Int,
        val tileBackground: Color,
        val size: Int = 42,
        val borderColor: Color = Color(0x292B6FA3), // rgba(43,111,163,.16)
        val framed: Boolean = true,
    ) : MenuIcon
    data class Glyph(
        val icon: ImageVector,
        val tint: Color,
        val tileBackground: Brush,
        val iconSize: Int = 18,
        val borderColor: Color = Color(0x140F172A), // rgba(15,23,42,.08)
    ) : MenuIcon
}

private data class MoreEntry(
    val route: String,
    val label: String,
    val icon: MenuIcon,
    val adminOnly: Boolean = false,
)

/**
 * The web's `#moreMenu` (index.html), item for item and in the same order.
 *
 * "Meu Cadastro" is not here — on the web it is the avatar in the header; the
 * privacy policy is opened from Configurações › Privacidade e LGPD, as on the web.
 */
private val ENTRIES = listOf(
    MoreEntry(
        Routes.PLANS, "Inscreva-se no MemórIA",
        // `.plans-menu-logo-icon`: 46px (`--menu-icon-size-prominent`), no tile.
        MenuIcon.Picture(R.drawable.ic_menu_plans, Color.Transparent, size = 46, framed = false),
    ),
    MoreEntry(
        Routes.MEDS, "Medicamentos",
        MenuIcon.Picture(R.drawable.ic_menu_medications, Color(0xFF10161D), borderColor = Color(0x24FFFFFF)),
    ),
    MoreEntry(
        Routes.CALENDAR, "Calendário",
        MenuIcon.Picture(R.drawable.ic_menu_calendar, Color.White, borderColor = Color(0x2E2B6FA3)),
    ),
    MoreEntry(
        Routes.REPORTS, "Relatórios",
        MenuIcon.Picture(R.drawable.ic_menu_reports, Color(0xFFEEF7FF), borderColor = Color(0x241E5A9E)),
    ),
    MoreEntry(
        Routes.REPLENISHMENT, "Reposição",
        MenuIcon.Picture(R.drawable.ic_menu_replenishment, Color(0xFFF8FCFF)),
    ),
    MoreEntry(
        Routes.DOCTORS, "Meus Médicos",
        MenuIcon.Picture(R.drawable.ic_menu_doctors, Color(0xFFF8FCFF)),
    ),
    // `.health-menu-icon`: coração vermelho sobre degradê rosado.
    MoreEntry(
        Routes.HEALTH, "Saúde",
        MenuIcon.Glyph(
            Icons.Filled.MonitorHeart,
            tint = Color(0xFFDC2626),
            tileBackground = Brush.linearGradient(listOf(Color(0xFFFEE2E2), Color(0xFFFECACA))),
            iconSize = 24,
            borderColor = Color(0x2EDC2626),
        ),
    ),
    MoreEntry(
        Routes.PRESCRIPTIONS, "Minhas Receitas",
        MenuIcon.Picture(R.drawable.ic_menu_prescriptions, Color(0xFFF7FCFF)),
    ),
    MoreEntry(
        Routes.VOICE, "Conversar com o MemorIA",
        MenuIcon.Glyph(Icons.Filled.Mic, Color(0xFF0369A1), Brush.linearGradient(listOf(Color(0xFFEAF8FF), Color(0xFFEAF8FF)))),
    ),
    MoreEntry(
        Routes.SETTINGS, "Configurações",
        MenuIcon.Glyph(Icons.Filled.Settings, Color(0xFF334155), Brush.linearGradient(listOf(Color(0xFFEEF2F7), Color(0xFFEEF2F7)))),
    ),
    MoreEntry(
        Routes.OPTIMIZATION, "Otimização do App",
        MenuIcon.Glyph(Icons.Filled.Battery5Bar, Color(0xFFB45309), Brush.linearGradient(listOf(Color(0xFFFFF7ED), Color(0xFFFFF7ED)))),
    ),
    MoreEntry(
        Routes.HELP, "Ajuda",
        MenuIcon.Glyph(Icons.AutoMirrored.Filled.HelpOutline, Color(0xFF0369A1), Brush.linearGradient(listOf(Color(0xFFEAF8FF), Color(0xFFEAF8FF)))),
    ),
    MoreEntry(
        Routes.ADMIN, "Painel do Proprietário",
        // No `data-page` colour rule for the admin entry on the web: plain tile.
        MenuIcon.Glyph(Icons.Filled.ShowChart, TextPrimary, Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))),
        adminOnly = true,
    ),
)

/**
 * "Mais" as the web shows it: a sheet sliding up from the bottom
 * (`.more-menu-content`, cantos de 24px), with a close "×" in the corner. The
 * admin entry only appears for an account the server marks as admin, matching
 * `admin-only-menu-item` on the web.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreMenuSheet(onDismiss: () -> Unit, onNavigate: (String) -> Unit) {
    val vm = repoViewModel { MoreViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 24.dp, bottom = 16.dp)
                    .padding(horizontal = 24.dp),
            ) {
                MenuList(isAdmin = state.isAdmin) { route ->
                    onDismiss()
                    onNavigate(route)
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 6.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Fechar menu",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/**
 * The same menu as a full page — kept for the [Routes.MORE] destination so a
 * deep link or a restored back stack still lands somewhere sensible.
 */
@Composable
fun MoreScreen(contentPadding: PaddingValues, onNavigate: (String) -> Unit) {
    val vm = repoViewModel { MoreViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load() }

    // The header and the bottom bar are the nav host's; no app bar of its own.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .padding(bottom = contentPadding.calculateBottomPadding()),
    ) {
        MenuList(isAdmin = state.isAdmin, onNavigate = onNavigate)
    }
}

@Composable
private fun MenuList(isAdmin: Boolean, onNavigate: (String) -> Unit) {
    val visible = ENTRIES.filter { !it.adminOnly || isAdmin }
    // `.more-menu-nav { gap: 8px }`; each item carries its own 1px bottom rule
    // (`border-bottom: 1px solid var(--border-color)`), except the last one.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        visible.forEachIndexed { index, entry ->
            Column {
                MoreRow(entry) { onNavigate(entry.route) }
                if (index < visible.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/**
 * `.more-menu .menu-item`: a grid of the icon column
 * (`--menu-icon-size-prominent` + 4px = 50px), 14px gap, then the label at
 * `--font-size-base` behind a thin vertical rule; 18px of vertical padding.
 */
@Composable
private fun MoreRow(entry: MoreEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(50.dp), contentAlignment = Alignment.Center) {
            MenuIconTile(entry.icon)
        }
        Box(
            Modifier
                .padding(start = 14.dp)
                .width(1.dp)
                .height(44.dp)
                .background(Color(0x7394A3B8)), // rgba(148,163,184,.45)
        )
        Text(
            entry.label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = TextPrimary,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(start = 14.dp),
        )
    }
}

@Composable
private fun MenuIconTile(icon: MenuIcon) {
    val shape = RoundedCornerShape(12.dp)
    when (icon) {
        is MenuIcon.Picture -> if (icon.framed) {
            Image(
                painter = painterResource(icon.res),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(icon.size.dp)
                    .shadow(2.dp, shape)
                    .clip(shape)
                    .background(icon.tileBackground)
                    .border(1.dp, icon.borderColor, shape),
            )
        } else {
            Image(
                painter = painterResource(icon.res),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(icon.size.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
        }
        is MenuIcon.Glyph -> Box(
            Modifier
                .size(42.dp)
                .shadow(2.dp, shape)
                .clip(shape)
                .background(icon.tileBackground)
                .border(1.dp, icon.borderColor, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon.icon, contentDescription = null, tint = icon.tint, modifier = Modifier.size(icon.iconSize.dp))
        }
    }
}
