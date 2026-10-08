package com.memoria.mobile.ui.reports

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.data.local.VitalSignsRecord
import com.memoria.mobile.data.remote.HistoryEntry
import com.memoria.mobile.ui.common.MemoriaSnackbar
import com.memoria.mobile.ui.home.InlinePageTitle
import com.memoria.mobile.ui.common.ErrorState
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.PageGuide
import com.memoria.mobile.ui.common.PageTopGuide
import com.memoria.mobile.ui.common.PremiumIcon
import com.memoria.mobile.ui.common.PremiumIconSize
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.health.SelectField
import com.memoria.mobile.ui.health.VitalSignsPeriod
import com.memoria.mobile.ui.health.formatFullDateTime
import com.memoria.mobile.ui.health.glucoseContextReportLabel
import com.memoria.mobile.ui.health.jsNumber
import com.memoria.mobile.ui.meds.MedicationIcon
import java.time.LocalDate

/**
 * `var(--secondary-color)`, o âmbar `#d97706` e `var(--danger-color)` das barras
 * de adesão. O "bom" segue o tema escolhido, como a variável CSS na web (cada
 * paleta de `getThemePalettes()` redefine `--secondary-color`).
 */
private val Good: Color
    @Composable get() = MaterialTheme.colorScheme.secondary
private val Warn = Color(0xFFD97706)
private val Bad = Color(0xFFEF4444)

/** As cores do gráfico de 30 dias e das barras semanais, como no canvas da web. */
private val ChartLine = Color(0xFF0060F8)
private val ChartGood = Color(0xFF16A34A)
private val ChartWarn = Color(0xFFD97706)
private val ChartBad = Color(0xFFDC2626)
private val ChartGrid = Color(0xFFE5E7EB)
private val ChartEmpty = Color(0xFFE5E7EB)

@Composable
private fun colorFor(pct: Int): Color = when {
    pct >= 80 -> Good
    pct >= 50 -> Warn
    else -> Bad
}

private fun chartColorFor(pct: Int): Color = when {
    pct >= 80 -> ChartGood
    pct >= 50 -> ChartWarn
    else -> ChartBad
}

/** "Relatórios" — the web `reportsPage`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit, onOpenPlans: () -> Unit) {
    val vm = repoViewModel { ReportsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // Storage Access Framework: the user picks the destination, so the app needs
    // no storage permission and the file lands somewhere they can find again.
    val saveExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) vm.writeExportTo(uri, context.contentResolver) else vm.discardExport()
    }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.exporting) {
        if (state.exporting) saveExport.launch("memoria-relatorio-${LocalDate.now()}.txt")
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) { MemoriaSnackbar(it) } },
    ) { inner ->
        when {
            state.loading && state.history.isEmpty() -> LoadingBox(Modifier.padding(inner))
            state.error != null && state.history.isEmpty() ->
                ErrorState(state.error!!, onRetry = vm::load, modifier = Modifier.padding(inner))
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PageTopGuide(PageGuide.REPORTS)
                InlinePageTitle("Relatórios", subtitle = "Acompanhe sua adesão ao tratamento", topTrim = 16.dp)

                ReportCard("Adesão Geral", Icons.AutoMirrored.Filled.ShowChart) {
                    // `.stats-grid` (auto-fit, mínimo 200px, gap 16px): na largura
                    // de um telemóvel cabe uma caixa por linha.
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatBox("${state.overallRate}%", "Adesão Geral", colorFor(state.overallRate), Modifier.fillMaxWidth())
                        StatBox("${state.last7Rate}%", "Últimos 7 Dias", colorFor(state.last7Rate), Modifier.fillMaxWidth())
                        StatBox("${state.taken}", "Total Tomadas", Good, Modifier.fillMaxWidth())
                        StatBox("${state.missed}", "Total Perdidas", Bad, Modifier.fillMaxWidth())
                    }
                }

                ReportCard("Por Medicamento", Icons.Filled.Medication) {
                    if (state.medications.isEmpty()) {
                        Text(
                            "Nenhum medicamento cadastrado ainda.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            state.perMedication.forEach { MedicationStatRow(it) }
                        }
                    }
                }

                PremiumReportCard(
                    title = "Adesão — Últimos 30 Dias",
                    icon = Icons.Filled.EventAvailable,
                    unlocked = state.isPremium,
                    lockMessage = "Gráfico de adesão dos últimos 30 dias disponível no Premium.",
                    onOpenPlans = onOpenPlans,
                ) {
                    ThirtyDayChart(state.last30Days)
                }

                PremiumReportCard(
                    title = "Sequências e Tendência Semanal",
                    icon = Icons.Filled.LocalFireDepartment,
                    unlocked = state.isPremium,
                    lockMessage = "Sequências de adesão e tendência semanal disponíveis no Premium.",
                    onOpenPlans = onOpenPlans,
                ) {
                    AdvancedStats(state)
                }

                TakenHistoryCard(state, vm)

                VitalSignsReportCard(state, vm)

                // Premium na web: sem plano os dois botões ficam desactivados.
                // `.report-actions` no telemóvel: um por linha, largura total. Com
                // Premium o `textContent` da web apaga o ícone — só fica o texto.
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = vm::requestExport,
                        enabled = state.isPremium && !state.exporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // O arquivo é texto (.txt), não PDF: o rótulo da web ("Exportar PDF") não vale aqui.
                        if (state.isPremium) {
                            Text("Exportar relatório")
                        } else {
                            PremiumIcon(size = 20.dp)
                            Text(" Exportar relatório - Premium", textAlign = TextAlign.Center)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Meu Relatório MemorIA")
                                putExtra(Intent.EXTRA_TITLE, "Meu Relatório MemorIA")
                                putExtra(Intent.EXTRA_TEXT, vm.shareText())
                            }
                            context.startActivity(Intent.createChooser(send, "Meu Relatório MemorIA"))
                        },
                        enabled = state.isPremium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isPremium) {
                            Text("Compartilhar")
                        } else {
                            PremiumIcon(size = 20.dp)
                            Text(" Compartilhar - Premium", textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

/** `.report-card`: título com ícone e o conteúdo por baixo. */
@Composable
private fun ReportCard(
    title: String,
    icon: ImageVector,
    badge: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // `.report-card`: fundo `--bg-primary`, raio 16px, sombra suave; o `h3`
    // (ícone + título) em `--primary-color`, tamanho `--font-size-large`.
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                badge?.invoke()
            }
            content()
        }
    }
}

/** `.stat-box`: número grande e colorido sobre a legenda. */
@Composable
private fun StatBox(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    // `.stat-box`: fundo `--bg-secondary`, raio 16px; valor `--font-size-xxlarge`.
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 20.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = color)
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** `.medication-stat-item`: nome, barra e percentagem com a cor da adesão. */
@Composable
private fun MedicationStatRow(med: MedicationAdherence) {
    val color = colorFor(med.rate)
    // `.medication-stat-item`: fundo `--bg-secondary`, raio 16px, grelha
    // "nome | barra | %"; o nome leva o ícone do medicamento (28px) à frente.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MedicationIcon(med.look, size = 28.dp)
            Text(
                med.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        // `.medication-stat-bar-wrap` é 70px em ecrãs até 480px.
        Box(
            Modifier
                .width(70.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ChartGrid),
        ) {
            if (med.rate > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(med.rate / 100f)
                        .height(8.dp)
                        .background(color),
                )
            }
        }
        Text(
            "${med.rate}%",
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 40.dp),
            textAlign = TextAlign.End,
        )
    }
}

/**
 * Um cartão Premium: o selo "⭐ Premium" / "✓ Premium" no cabeçalho e, sem
 * plano, o `.report-premium-lock` com o ícone, a mensagem e "Ativar Premium".
 */
@Composable
private fun PremiumReportCard(
    title: String,
    icon: ImageVector,
    unlocked: Boolean,
    lockMessage: String,
    onOpenPlans: () -> Unit,
    content: @Composable () -> Unit,
) {
    ReportCard(title, icon, badge = { PremiumBadge(unlocked) }) {
        if (unlocked) {
            content()
        } else {
            // `.report-premium-lock`: degradê #f9fafb → #f3f4f6, borda tracejada
            // de 2px #d1d5db, raio 16px; `.report-lock-content` com 24px e gap 12.
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFF9FAFB), Color(0xFFF3F4F6))))
                    .drawBehind {
                        val stroke = 2.dp.toPx()
                        drawRoundRect(
                            color = Color(0xFFD1D5DB),
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(16.dp.toPx() - stroke / 2),
                            style = Stroke(
                                width = stroke,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                            ),
                        )
                    }
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                PremiumIcon(size = PremiumIconSize.Lock)
                val parts = lockMessage.split("Premium")
                Text(
                    buildAnnotatedString {
                        append(parts.firstOrNull().orEmpty())
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Premium") }
                        append(parts.drop(1).joinToString("Premium"))
                    },
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 280.dp),
                )
                Button(onClick = onOpenPlans) {
                    PremiumIcon(size = 20.dp)
                    Text(" Ativar Premium")
                }
            }
        }
    }
}

@Composable
private fun PremiumBadge(active: Boolean) {
    val (bg, fg, border) = if (active) {
        Triple(Color(0xFFFEF3C7), Color(0xFF92400E), Color(0xFFFBBF24))
    } else {
        Triple(Color(0xFFF3F4F6), Color(0xFF6B7280), Color(0xFFD1D5DB))
    }
    Text(
        if (active) "✓ Premium" else "⭐ Premium",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/**
 * O gráfico de linha do canvas da web: grelha 0–100%, linha azul que salta os
 * dias sem registo, pontos coloridos pela adesão e a data a cada 5 dias.
 */
@Composable
private fun ThirtyDayChart(days: List<DayAdherence>) {
    val gridLabel = Color(0xFF9CA3AF).toArgb()
    val axisLabel = Color(0xFF6B7280).toArgb()
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val padL = 30.dp.toPx()
        val padR = 10.dp.toPx()
        val padT = 10.dp.toPx()
        val padB = 30.dp.toPx()
        val chartW = size.width - padL - padR
        val chartH = size.height - padT - padB
        val step = if (days.size > 1) chartW / (days.size - 1) else 0f
        val text = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 10.sp.toPx()
        }

        listOf(0, 25, 50, 75, 100).forEach { v ->
            val y = padT + chartH - (v / 100f) * chartH
            drawLine(ChartGrid, Offset(padL, y), Offset(size.width - padR, y), strokeWidth = 1.dp.toPx())
            text.color = gridLabel
            drawContext.canvas.nativeCanvas.drawText("$v%", 0f, y + 4.dp.toPx(), text)
        }

        val path = Path()
        var started = false
        days.forEachIndexed { i, day ->
            val v = day.rate ?: return@forEachIndexed
            val x = padL + i * step
            val y = padT + chartH - (v / 100f) * chartH
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
        drawPath(path, ChartLine, style = Stroke(width = 2.dp.toPx()))

        days.forEachIndexed { i, day ->
            val v = day.rate ?: return@forEachIndexed
            val x = padL + i * step
            val y = padT + chartH - (v / 100f) * chartH
            drawCircle(chartColorFor(v), radius = 3.dp.toPx(), center = Offset(x, y))
        }

        text.color = axisLabel
        days.forEachIndexed { i, day ->
            if (i % 5 != 0) return@forEachIndexed
            val x = padL + i * step
            drawContext.canvas.nativeCanvas.drawText(
                "${day.date.dayOfMonth}/${day.date.monthValue}",
                x - 12.dp.toPx(),
                size.height - 6.dp.toPx(),
                text,
            )
        }
    }
}

@Composable
private fun AdvancedStats(state: ReportsUiState) {
    // `.advanced-stats-grid`: 2 colunas, gap 12px.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AdvancedStatBox("🔥", "${state.streak}", "Dias consecutivos com 100% de adesão", Modifier.weight(1f).fillMaxHeight())
            AdvancedStatBox("🏆", state.bestWeekday, "Melhor dia da semana", Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AdvancedStatBox("⚠️", state.worstWeekday, "Dia com mais esquecimentos", Modifier.weight(1f).fillMaxHeight())
            AdvancedStatBox("📊", state.trend, "Tendência (últimas 4 semanas)", Modifier.weight(1f).fillMaxHeight(), small = true)
        }

        Text(
            "Adesão por semana (últimas 4 semanas):",
            fontSize = 13.sp,
            color = Color(0xFF6B7280),
            modifier = Modifier.padding(top = 4.dp),
        )
        // `.weekly-trend-bars`: 100px de altura, gap 12px.
        Row(
            Modifier.fillMaxWidth().height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.weeklyRates.forEachIndexed { i, pct ->
                val color = if (pct == null) ChartEmpty else chartColorFor(pct)
                val height = if (pct != null) maxOf(6, pct) else 6
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // A web pinta o valor com a cor da barra — "Sem dados" em #e5e7eb.
                    Text(
                        if (pct != null) "$pct%" else "Sem dados",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFF3F4F6)),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(height / 100f)
                                .heightIn(min = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color),
                        )
                    }
                    Text(
                        "Sem. ${i + 1}",
                        fontSize = 11.sp,
                        color = Color(0xFF9CA3AF),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedStatBox(icon: String, value: String, label: String, modifier: Modifier, small: Boolean = false) {
    // `.advanced-stat-box`: fundo `--bg-secondary`, raio 16px; valor 22px
    // (18px na tendência), rótulo 12px.
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(icon, fontSize = 24.sp)
        Text(
            value,
            fontSize = if (small) 18.sp else 22.sp,
            lineHeight = if (small) 22.sp else 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TakenHistoryCard(state: ReportsUiState, vm: ReportsViewModel) {
    ReportCard("Histórico Completo de Tomadas", Icons.Filled.Archive) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val base = state.allTaken.size
            val filtered = state.filteredHistory
            Text(
                if (base > 0) {
                    "${filtered.size} de $base tomada(s) exibida(s). Use os filtros para consulta posterior."
                } else {
                    "Nenhuma tomada registrada até o momento."
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectField(
                HistoryPeriod.entries.map { it.name to it.label },
                state.period.name,
                { vm.setPeriod(HistoryPeriod.valueOf(it)) },
                accessibilityLabel = "Filtro por período",
            )
            OutlinedTextField(
                value = state.search,
                onValueChange = vm::setSearch,
                placeholder = { Text("Buscar por nome do medicamento") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = vm::clearFilters, modifier = Modifier.fillMaxWidth()) {
                Text("Limpar filtros")
            }

            if (filtered.isEmpty()) {
                Text(
                    "Nenhum resultado para os filtros atuais. Ajuste o período ou a busca por medicamento.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                HistoryList { filtered.forEach { TakenHistoryItem(it) } }
            }
        }
    }
}

@Composable
private fun TakenHistoryItem(entry: HistoryEntry) {
    val date = formatFullDateTime(entryInstant(entry)?.toString()) ?: "Data não disponível"
    val schedule = entry.scheduleTime.trim()
    ReportListItem(
        title = entry.medicationName.ifBlank { "Medicamento não informado" },
        meta = entry.dosage.ifBlank { "Dose não informada" },
        time = if (schedule.isNotEmpty()) "$date • horário $schedule" else date,
    )
}

@Composable
private fun VitalSignsReportCard(state: ReportsUiState, vm: ReportsViewModel) {
    ReportCard("Relatório de Sinais Vitais", Icons.Filled.MonitorHeart) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                state.vitalSignsSummary,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectField(
                VitalSignsPeriod.entries.map { it.value to it.label },
                state.vitalPeriod.value,
                { v -> vm.setVitalPeriod(VitalSignsPeriod.entries.first { it.value == v }) },
                accessibilityLabel = "Filtro por período dos sinais vitais",
            )
            OutlinedButton(onClick = vm::clearVitalFilter, modifier = Modifier.fillMaxWidth()) {
                Text("Limpar filtro")
            }

            val filtered = state.filteredVitalSigns
            if (filtered.isEmpty()) {
                Text(
                    "Nenhum registro de sinais vitais para o período selecionado.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                HistoryList { filtered.take(100).forEach { VitalSignsReportItem(it) } }
            }
        }
    }
}

@Composable
private fun VitalSignsReportItem(entry: VitalSignsRecord) {
    val parts = buildList {
        if ((entry.systolic ?: 0) != 0 && (entry.diastolic ?: 0) != 0) {
            add("PA ${entry.systolic}/${entry.diastolic} mmHg")
        }
        entry.heartRate?.takeIf { it != 0 }?.let { add("FC $it bpm") }
        entry.spo2?.takeIf { it != 0.0 }?.let { add("SpO₂ ${jsNumber(it)}%") }
        entry.glucose?.takeIf { it != 0.0 }?.let {
            add("Glicose ${jsNumber(it)} mg/dL (${glucoseContextReportLabel(entry.glucoseContext)})")
        }
        entry.hba1c?.takeIf { it != 0.0 }?.let { add("HbA1c ${jsNumber(it)}%") }
        entry.temperature?.takeIf { it != 0.0 }?.let { add("Temp ${jsNumber(it)}°C") }
    }
    ReportListItem(
        title = "Sinais vitais",
        meta = parts.joinToString(" • ").ifEmpty { "Sem indicadores preenchidos" },
        time = formatFullDateTime(entry.date) ?: "Data inválida",
    )
}

/**
 * `.taken-history-report-list`: gap 10px e no máximo 460px de altura, com
 * rolagem própria — a lista longa não empurra o resto da página.
 */
@Composable
private fun HistoryList(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

/**
 * `.taken-history-item` em ecrã estreito (`max-width: 700px`): nome e detalhe
 * por cima, a data por baixo, alinhada à esquerda. Fundo `--bg-secondary`,
 * borda 1px `--border-color`, raio 12px.
 */
@Composable
private fun ReportListItem(title: String, meta: String, time: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(meta, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
