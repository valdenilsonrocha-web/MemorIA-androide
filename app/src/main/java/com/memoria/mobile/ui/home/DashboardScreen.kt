package com.memoria.mobile.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.data.remote.Medication
import com.memoria.mobile.ui.common.ErrorState
import com.memoria.mobile.ui.common.MemoriaSnackbar
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.PremiumIcon
import com.memoria.mobile.ui.common.reminderScheduler
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.meds.DELETE_CONFIRM_TEXT
import com.memoria.mobile.ui.meds.MedicationInfoCard
import com.memoria.mobile.ui.meds.MedicationScheduleCard
import com.memoria.mobile.ui.theme.RedMiss
import com.memoria.mobile.ui.common.FitText
import com.memoria.mobile.ui.common.ReloadOnDoseChanges

private val StatPrimary = Color(0xFF0060F8)   // --primary-color
private val StatWarning = Color(0xFFF59E0B)   // --warning-color
private val StatSecondary = Color(0xFF66A9FF) // --secondary-color
private val TextPrimary = Color(0xFF1F2937)   // --text-primary
private val TextSecondary = Color(0xFF6B7280) // --text-secondary
private val BorderLight = Color(0xFFF3F4F6)   // --border-light

/**
 * "Início" — the web `dashboardPage`: greeting, achievements, today's counters,
 * the daily Premium card, the doses still due and the full medication list.
 *
 * Like the web there is no page bar of its own (the global blue header sits
 * above every page) and no "+" button: a medication is added from the
 * "Medicamentos" tab of the bottom bar, which opens the add form — hence the
 * empty-state hint 'Clique em "Adicionar"'. [onAdd] is kept for the caller's
 * signature but the page has no control of its own that uses it.
 */
@Composable
fun DashboardScreen(
    contentPadding: PaddingValues,
    @Suppress("UNUSED_PARAMETER") onAdd: () -> Unit,
    onOpenMedication: (String) -> Unit,
    onOpenPlans: () -> Unit,
    onOpenVoice: () -> Unit = {},
    /** "Editar" on a card: the web's `editMedication()` (the add form, pre-filled). */
    onEdit: ((String) -> Unit)? = null,
) {
    val vm = repoViewModel { DashboardViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf<Medication?>(null) }
    // Until the navigation passes [onEdit], "Editar" opens the details page,
    // which carries its own "Editar" button to the form.
    val edit: (String) -> Unit = onEdit ?: onOpenMedication

    LaunchedEffect(Unit) { vm.load() }
    // A dose confirmed in the alert must leave the list without reopening the page.
    ReloadOnDoseChanges(vm::load)
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    LaunchedEffect(state.error) {
        // Only report over existing content. Clearing the error unconditionally
        // sent a failed load to a snackbar that then vanished, leaving the page
        // reading "Nenhum medicamento cadastrado" — a medication app must never
        // show an empty schedule when it simply could not reach the server.
        if (state.medications.isNotEmpty()) {
            state.error?.let { snackbar.showSnackbar(com.memoria.mobile.ui.common.errorToast(it)); vm.clearError() }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) { MemoriaSnackbar(it) } }) { inner ->
        if (state.loading && state.medications.isEmpty()) {
            LoadingBox(Modifier.padding(inner))
            return@Scaffold
        }
        if (state.error != null && state.medications.isEmpty()) {
            ErrorState(state.error!!, onRetry = vm::load, modifier = Modifier.padding(inner))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 20.dp,
                bottom = contentPadding.calculateBottomPadding() + 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // .welcome-section: big title, subtitle, light rule underneath.
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val y = size.height - 1.dp.toPx()
                            drawLine(BorderLight, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 2.dp.toPx())
                        }
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FitText(
                        greeting(state),
                        maxLines = 3,
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        style = LocalTextStyle.current.copy(letterSpacing = (-0.5).sp),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text("Mantenha sua saúde em dia", fontSize = 20.sp, color = TextSecondary)
                }
            }

            item {
                Button(
                    onClick = onOpenVoice,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null)
                    Text("  Conversar com o MemorIA", modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            // #achievementsContainer
            item { AchievementCard(state.streak) }

            // .stats-container — one column on a phone (`grid-template-columns: 1fr`
            // under 768px), each card centred: icon, value, uppercase label.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    DashStat(
                        value = state.todayCount, label = "Hoje", icon = Icons.Filled.CalendarToday,
                        accent = MaterialTheme.colorScheme.primary, from = Color(0xFFF0FDF4), to = Color(0xFFDCFCE7),
                    )
                    DashStat(
                        value = state.pendingCount, label = "Pendentes", icon = Icons.Filled.Schedule,
                        accent = StatWarning, from = Color(0xFFFFFBEB), to = Color(0xFFFEF3C7),
                    )
                    DashStat(
                        value = state.completedCount, label = "Tomados", icon = Icons.Filled.CheckCircle,
                        accent = StatSecondary, from = Color(0xFFECF9FF), to = Color(0xFFCFFAFE),
                    )
                }
            }

            // #dailyUpsellCard — free users, until dismissed for the day.
            if (!state.isPremium && !state.upsellDismissedToday) {
                item { DailyUpsellCard(onDismiss = vm::dismissUpsell, onSubscribe = onOpenPlans) }
            }

            item { SectionTitle("Próximos Horários") }
            if (state.upcoming.isEmpty()) {
                item {
                    EmptyLine(Icons.Filled.CheckCircle, "Nenhum medicamento pendente no momento!")
                }
            } else {
                items(state.upcoming, key = { (slot, _) -> "${slot.medication.id}-${slot.time}" }) { (slot, status) ->
                    MedicationScheduleCard(
                        slot = slot,
                        status = status,
                        local = state.localFor(slot.medication),
                        onEdit = { slot.medication.id?.let(edit) },
                        onSnooze = { vm.snooze(slot, context.reminderScheduler()) },
                        onTake = { vm.take(slot) },
                    )
                }
            }

            item { SectionTitle("Todos os Medicamentos") }
            if (state.medications.isEmpty()) {
                item {
                    EmptyLine(
                        Icons.Filled.Medication,
                        "Nenhum medicamento cadastrado. Clique em \"Adicionar\" para começar.",
                    )
                }
            } else {
                // The web card is not a link: only its "Editar"/"Excluir" buttons act.
                items(state.medications, key = { it.id ?: it.name }) { med ->
                    MedicationInfoCard(
                        med = med,
                        local = state.localFor(med),
                        onEdit = { med.id?.let(edit) },
                        onDelete = { confirmDelete = med },
                    )
                }
            }

            // .ad-banner.ad-only-free
            if (!state.isPremium) {
                item { DashboardAdBanner(onOpenPlans) }
            }
        }
    }

    confirmDelete?.let { med ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text(DELETE_CONFIRM_TEXT) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; vm.delete(med) }) {
                    Text("OK", color = RedMiss)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") }
            },
        )
    }
}

/** `updateWelcomeMessage()`. */
private fun greeting(state: DashboardUiState): String {
    val firstName = state.user?.name?.trim().orEmpty().substringBefore(" ")
    return if (firstName.isBlank()) "Seus Medicamentos" else "$firstName, seus medicamentos"
}

/** `.medications-today h3` / `.all-medications h3`. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 24.sp,
        letterSpacing = (-0.3).sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
    )
}

/** `createEmptyState()`: faded icon over a centred message. */
@Composable
private fun EmptyLine(icon: ImageVector, message: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(56.dp).alpha(0.3f),
        )
        Box(Modifier.height(20.dp))
        Text(message, fontSize = 20.sp, color = TextSecondary, textAlign = TextAlign.Center)
    }
}

/** `.stat-card.stat-today|pending|completed`: gradient card, coloured icon and border. */
@Composable
private fun DashStat(
    value: Int,
    label: String,
    icon: ImageVector,
    accent: Color,
    from: Color,
    to: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(2.dp, accent),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(from, to)))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(32.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$value", fontSize = 32.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    label.uppercase(),
                    fontSize = 14.sp,
                    letterSpacing = 0.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                )
            }
        }
    }
}

/** `.ad-banner.ad-only-free` of the dashboard. */
@Composable
private fun DashboardAdBanner(onOpenPlans: () -> Unit) = AdOnlyFreeBanner(
    title = "Cuide melhor da sua saúde com o Plano Premium",
    body = "Cadastre medicamentos ilimitados, receba relatórios avançados e libere funções exclusivas.",
    buttonText = "Ver Planos",
    onOpenPlans = onOpenPlans,
)

/**
 * `.ad-banner.ad-only-free` (dashboard, receitas…): dashed amber box on cream,
 * `.ad-label` "DICA", the `h4` in #7c2d12, the text in #92400e and a
 * `.btn-primary` with the premium mark.
 */
@Composable
internal fun AdOnlyFreeBanner(title: String, body: String, buttonText: String, onOpenPlans: () -> Unit) {
    val amber = Color(0xFFF59E0B)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .drawBehind {
                val r = CornerRadius(16.dp.toPx())
                drawRoundRect(Color(0xFFFFF9E8), cornerRadius = r)
                drawRoundRect(
                    amber,
                    cornerRadius = r,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    ),
                )
            }
            .padding(20.dp),
    ) {
        Text(
            "Dica".uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFB45309),
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF7C2D12),
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF92400E),
        )
        Button(onClick = onOpenPlans, modifier = Modifier.padding(top = 10.dp)) {
            PremiumIcon()
            Text("  $buttonText")
        }
    }
}
