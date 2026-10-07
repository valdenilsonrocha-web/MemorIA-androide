package com.memoria.mobile.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.R
import com.memoria.mobile.data.local.MedicationLook
import com.memoria.mobile.reminders.ReminderTexts
import com.memoria.mobile.ui.common.ErrorState
import com.memoria.mobile.ui.common.MemoriaSnackbar
import com.memoria.mobile.ui.common.MemoriaToast
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.home.InlinePageTitle
import com.memoria.mobile.ui.meds.MedicationIcon
import com.memoria.mobile.ui.meds.StatusBadge
import com.memoria.mobile.ui.meds.doseStatusLabel
import com.memoria.mobile.ui.common.FitText
import com.memoria.mobile.ui.common.ReloadOnDoseChanges

private val filters = listOf("all" to "Todos", "taken" to "Tomados", "missed" to "Perdidos")


private val TextPrimary = Color(0xFF1F2937)   // --text-primary
private val TextSecondary = Color(0xFF6B7280) // --text-secondary
private val BorderColor = Color(0xFFE5E7EB)   // --border-color
private val BgSecondary = Color(0xFFF9FAFB)   // --bg-secondary

/**
 * "Atualizações" — the web `historyPage`: `<h2>Histórico</h2>`, the filters,
 * the premium banner, today's pending doses with "Marcar como tomado", the
 * three reminder banners, stock replenishment and the history grouped by day.
 *
 * The navigation callbacks are optional so the screen compiles against any
 * version of the nav graph; a banner whose destination is not wired shows no
 * button rather than a dead one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    contentPadding: PaddingValues,
    onOpenPlans: () -> Unit,
    onOpenReplenishment: (() -> Unit)? = null,
    onOpenPrescriptions: (() -> Unit)? = null,
    onOpenDoctors: (() -> Unit)? = null,
    onOpenProfile: (() -> Unit)? = null,
) {
    val vm = repoViewModel { HistoryViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.load() }
    // A dose confirmed in the alert must leave the list without reopening the page.
    ReloadOnDoseChanges(vm::load)
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) { MemoriaSnackbar(it) } }) { inner ->
        when {
            state.loading && state.entries.isEmpty() -> LoadingBox(Modifier.padding(inner))
            state.error != null && state.entries.isEmpty() ->
                ErrorState(state.error!!, onRetry = vm::load, modifier = Modifier.padding(inner))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 20.dp,
                    bottom = contentPadding.calculateBottomPadding() + 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // `#historyPage h2`
                item {
                    InlinePageTitle("Histórico", topTrim = 20.dp)
                }

                // .history-filters
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        filters.forEach { (value, label) ->
                            FilterButton(label, active = state.filter == value) { vm.setFilter(value) }
                        }
                    }
                }

                // .updates-premium-banner.ad-only-free
                if (!state.isPremium) {
                    item { UpdatesPremiumBanner(onOpenPlans) }
                }

                val grouped = state.grouped
                val hasPending = state.visiblePending.isNotEmpty() || state.visibleStock.isNotEmpty()
                if (grouped.isEmpty() && !hasPending) {
                    item { EmptyHistory() }
                }

                state.pendingBannerText?.let { text ->
                    item { PendingBanner(text) }
                }
                items(state.visiblePending, key = { (slot, _) -> "p-${slot.medication.id}-${slot.time}" }) { (slot, status) ->
                    HistoryItem(
                        look = state.lookFor(slot.medication.id, slot.medication.name),
                        name = slot.medication.name.ifBlank { "Medicamento" },
                        dosage = slot.medication.dosage.ifBlank { "Dose não informada" },
                        time = slot.time.ifBlank { "--:--" },
                        status = status,
                        kind = ItemKind.PENDING,
                    ) {
                        CtaPill(Icons.Filled.Check, "Marcar como tomado") { vm.resolve(slot) }
                    }
                }

                if (state.visiblePending.isNotEmpty()) {
                    item {
                        ReminderBanner(
                            palette = PRESCRIPTION,
                            image = R.drawable.guide_top_replenishment,
                            imageDescription = "Ilustração de reposição para lembrar de salvar receitas",
                            icon = Icons.Filled.Description,
                            title = "Guarde suas receitas",
                            text = "Quando a receita fica salva, você protege sua memória nos dias corridos e evita erros de dose. Com a assinatura Premium, esse cuidado vira rotina com mais segurança.",
                            ctaIcon = Icons.Filled.PhotoCamera,
                            cta = "Salvar receitas agora",
                            onCta = onOpenPrescriptions,
                        )
                    }
                    item {
                        ReminderBanner(
                            palette = CONSULTATION,
                            image = R.drawable.guide_top_doctors,
                            imageDescription = "Ilustração para lembrar de salvar consultas médicas agendadas",
                            icon = Icons.Filled.EventAvailable,
                            title = "Salve suas consultas agendadas",
                            text = "Registrar cada consulta ajuda você a se organizar, lembrar retornos e chegar mais preparado. Com a assinatura Premium, você mantém seu acompanhamento médico completo em um só lugar.",
                            ctaIcon = Icons.Filled.CalendarMonth,
                            cta = "Salvar consulta",
                            onCta = onOpenDoctors,
                        )
                    }
                    item {
                        ReminderBanner(
                            palette = MEDICAL_DATA,
                            image = R.drawable.guide_top_settings,
                            imageDescription = "Ilustração de dados médicos e escala de cuidados",
                            icon = Icons.Filled.HealthAndSafety,
                            title = "Dados médicos e escala de cuidados",
                            text = "Com seus dados médicos e a escala de cuidados do app, você acompanha sua evolução com clareza. A assinatura Premium reforça esse cuidado com mais organização e suporte no dia a dia.",
                            ctaIcon = Icons.Filled.AdminPanelSettings,
                            cta = "Atualizar meus dados",
                            onCta = onOpenProfile,
                        )
                    }
                }

                if (state.visibleStock.isNotEmpty()) {
                    // .stock-updates-title
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Inventory2, contentDescription = null, tint = Color(0xFF92400E))
                            Text(
                                "  Reposição de estoque (${state.visibleStock.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E),
                            )
                        }
                    }
                    items(state.visibleStock, key = { "s-${it.medication.id ?: it.medication.name}" }) { need ->
                        HistoryItem(
                            look = state.lookFor(need.medication.id, need.medication.name),
                            name = need.medication.name.ifBlank { "Medicamento" },
                            dosage = "Estoque atual: ${need.stock} dose(s)",
                            time = null,
                            status = "missed",
                            statusText = need.message.title,
                            guidance = need.message.body,
                            kind = ItemKind.STOCK,
                        ) {
                            if (onOpenReplenishment != null) {
                                CtaPill(Icons.Filled.Inventory, "Ir para reposição") {
                                    // The web navigates and toasts a fresh replenishment title.
                                    val msg = ReminderTexts.randomReplenishment(
                                        need.medication.name.ifBlank { "Medicamento" },
                                        need.stock,
                                    )
                                    onOpenReplenishment()
                                    MemoriaToast.show("📦 ${msg.title}")
                                }
                            }
                        }
                    }
                }

                grouped.forEach { (date, entries) ->
                    item(key = "d-$date") {
                        Text(
                            date,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                    }
                    items(entries) { entry ->
                        HistoryItem(
                            look = state.lookFor(entry.medicationId, entry.medicationName),
                            name = entry.medicationName,
                            dosage = entry.dosage,
                            time = entry.scheduleTime,
                            status = entry.status,
                            statusText = historyStatusLabel(entry.status),
                        )
                    }
                }
            }
        }
    }
}

/** `getStatusLabel()`; the phone's older "snoozed" entries read "Adiado". */
private fun historyStatusLabel(status: String): String =
    if (status == "snoozed") "Adiado" else doseStatusLabel(status)

/** `.filter-btn` / `.filter-btn.active`. */
@Composable
private fun FilterButton(label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (active) MaterialTheme.colorScheme.primary else BgSecondary)
            .border(2.dp, if (active) MaterialTheme.colorScheme.primary else BorderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else TextPrimary)
    }
}

/** `.updates-cta-button`: rounded primary pill, centred. */
@Composable
private fun CtaPill(icon: ImageVector, text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .heightIn(min = 46.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.History,
            contentDescription = null,
            modifier = Modifier.size(56.dp).alpha(0.3f),
            tint = TextSecondary,
        )
        Box(Modifier.height(20.dp))
        Text("Nenhum histórico encontrado.", fontSize = 20.sp, color = TextSecondary, textAlign = TextAlign.Center)
    }
}

private enum class ItemKind { PLAIN, PENDING, STOCK }

/** `.history-item` (+ `-pending` / `-stock`): icon + name, dosage, time, badge, actions. */
@Composable
private fun HistoryItem(
    look: MedicationLook?,
    name: String,
    dosage: String,
    time: String?,
    status: String,
    statusText: String = doseStatusLabel(status),
    guidance: String? = null,
    kind: ItemKind = ItemKind.PLAIN,
    actions: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    val (border, background) = when (kind) {
        ItemKind.PENDING -> Color(0x66EF4444) to
            Brush.verticalGradient(listOf(Color(0x14EF4444), Color(0xF0FFFFFF)))
        ItemKind.STOCK -> Color(0x73F59E0B) to
            Brush.verticalGradient(listOf(Color(0x1FF59E0B), Color(0xF2FFFFFF)))
        ItemKind.PLAIN -> BorderColor to Brush.verticalGradient(listOf(Color.White, Color.White))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape)
            .clip(shape)
            .background(background)
            .border(2.dp, border, shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MedicationIcon(look, size = 28.dp)
            FitText(name, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 3)
        }
        if (dosage.isNotBlank()) {
            Text(dosage, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
        }
        if (!time.isNullOrBlank()) {
            Text(time, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        StatusBadge(statusText, status)
        guidance?.let { Text(it, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = TextSecondary) }
        actions?.let { Box(Modifier.padding(top = 4.dp)) { it() } }
    }
}

/** `.updates-pending-banner`: red-tinted card, "⚠ Atenção" title. */
@Composable
private fun PendingBanner(text: String) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xF2FEF2F2), Color(0xFAFFFFFF))))
            .border(1.dp, Color(0xFFFECACA), shape)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFDC2626))
            Text("Atenção", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
        }
        Text(text, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF7F1D1D))
    }
}

/** Colours of one `.updates-*-banner` variant. */
private data class BannerPalette(
    val border: Color,
    val bgTop: Color,
    val imageBgTop: Color,
    val imageBgBottom: Color,
    val title: Color,
    val titleIcon: Color,
    val text: Color,
)

private val PRESCRIPTION = BannerPalette(
    border = Color(0xFFBFDBFE), bgTop = Color(0xF2EFF6FF),
    imageBgTop = Color(0xBFDBEAFE), imageBgBottom = Color(0x80EFF6FF),
    title = Color(0xFF0053D6), titleIcon = Color(0xFF0060F8), text = Color(0xFF003788),
)
private val CONSULTATION = BannerPalette(
    border = Color(0xFFBBF7D0), bgTop = Color(0xF2F0FDF4),
    imageBgTop = Color(0xCCDCFCE7), imageBgBottom = Color(0x80F0FDF4),
    title = Color(0xFF166534), titleIcon = Color(0xFF16A34A), text = Color(0xFF14532D),
)
private val MEDICAL_DATA = BannerPalette(
    border = Color(0xFFE9D5FF), bgTop = Color(0xF2FAF5FF),
    imageBgTop = Color(0xCCF3E8FF), imageBgBottom = Color(0x80FAF5FF),
    title = Color(0xFF6D28D9), titleIcon = Color(0xFF0053D6), text = Color(0xFF581C87),
)

/** `.updates-prescription|consultation|medical-data-banner`. */
@Composable
private fun ReminderBanner(
    palette: BannerPalette,
    image: Int,
    imageDescription: String,
    icon: ImageVector,
    title: String,
    text: String,
    ctaIcon: ImageVector,
    cta: String,
    onCta: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(palette.bgTop, Color(0xFAFFFFFF))))
            .border(1.dp, palette.border, shape)
            .padding(top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BannerImage(
            image,
            imageDescription,
            height = 190.dp,
            contentScale = ContentScale.Fit,
            background = Brush.verticalGradient(listOf(palette.imageBgTop, palette.imageBgBottom)),
        )
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = palette.titleIcon)
            Text(title, fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, color = palette.title)
        }
        Text(
            text,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.text,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (onCta != null) {
            Box(Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp)) { CtaPill(ctaIcon, cta, onCta) }
        }
    }
}

@Composable
private fun BannerImage(
    image: Int,
    description: String,
    height: Dp,
    contentScale: ContentScale,
    background: Brush? = null,
) {
    Image(
        painter = painterResource(image),
        contentDescription = description,
        contentScale = contentScale,
        alignment = if (contentScale == ContentScale.Crop) Alignment.TopCenter else Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .let { if (background != null) it.background(background) else it },
    )
}

/** `.ad-banner.ad-only-free.updates-premium-banner` of `historyPage`. */
@Composable
private fun UpdatesPremiumBanner(onOpenPlans: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = Color.White,
        border = BorderStroke(1.dp, BorderColor),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BannerImage(
                R.drawable.updates_banner,
                "Tela de assinatura do aplicativo MemórIA",
                height = 260.dp,
                contentScale = ContentScale.Crop,
            )
            Text(
                "Assine o MemórIA",
                fontSize = 28.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
            Text(
                "Tenha tudo do nosso aplicativo em suas mãos, sem anúncios, medicamentos ilimitados, " +
                    "aviso aos cuidadores, consultas e médicos, e muito mais.",
                fontSize = 16.sp,
                lineHeight = 23.sp,
                color = Color(0xFF374151),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Box(Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .heightIn(min = 46.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(onClick = onOpenPlans)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Assine agora", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
