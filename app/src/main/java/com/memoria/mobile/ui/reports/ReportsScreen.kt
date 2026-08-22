package com.memoria.mobile.ui.reports

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.BarRow
import com.memoria.mobile.ui.common.ErrorState
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.Schedule
import com.memoria.mobile.ui.common.SectionCard
import com.memoria.mobile.ui.common.StatTile
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.theme.Amber
import com.memoria.mobile.ui.theme.GreenOk
import com.memoria.mobile.ui.theme.RedMiss
import java.time.format.DateTimeFormatter

private val DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM")

/** "Relatórios" — the web `reportsPage`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit, onOpenPlans: () -> Unit) {
    val vm = repoViewModel { ReportsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(topBar = { BackTopBar("Relatórios", onBack) }) { inner ->
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
                Text(
                    "Acompanhe sua adesão ao tratamento",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionCard("Adesão Geral", icon = Icons.AutoMirrored.Filled.ShowChart) {
                    val adherence = state.adherence
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile(adherence?.adherenceRate ?: "—", "Adesão (30 dias)", Modifier.weight(1f))
                            StatTile("${adherence?.taken ?: 0}", "Tomadas", Modifier.weight(1f), accent = GreenOk)
                            StatTile("${adherence?.missed ?: 0}", "Perdidas", Modifier.weight(1f), accent = RedMiss)
                        }
                        Text(
                            "Total registrado: ${adherence?.total ?: 0} doses",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SectionCard("Por Medicamento", icon = Icons.Filled.Medication) {
                    if (state.perMedication.isEmpty()) {
                        Text(
                            "Ainda não há doses registradas.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            state.perMedication.forEach { med ->
                                BarRow(
                                    label = med.name,
                                    value = "${med.rate}% (${med.taken}/${med.total})",
                                    fraction = med.rate / 100f,
                                    color = if (med.rate >= 80) GreenOk else Amber,
                                )
                            }
                        }
                    }
                }

                PremiumSection(
                    title = "Adesão — Últimos 30 Dias",
                    icon = Icons.Filled.CalendarMonth,
                    unlocked = state.isPremium,
                    onOpenPlans = onOpenPlans,
                ) {
                    ThirtyDayChart(state.last30Days)
                }

                PremiumSection(
                    title = "Sequências e Tendência Semanal",
                    icon = Icons.Filled.LocalFireDepartment,
                    unlocked = state.isPremium,
                    onOpenPlans = onOpenPlans,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("${state.currentStreak}", "Sequência atual (dias)", Modifier.weight(1f))
                            StatTile("${state.bestStreak}", "Melhor sequência", Modifier.weight(1f), accent = GreenOk)
                        }
                        state.weeklyTrend.forEach { day ->
                            BarRow(
                                label = day.date.format(DAY_MONTH),
                                value = if (day.total == 0) "sem doses" else "${day.taken}/${day.total}",
                                fraction = day.rate,
                                color = if (day.total > 0 && day.taken == day.total) GreenOk else Amber,
                            )
                        }
                    }
                }

                SectionCard(
                    "Histórico Completo de Tomadas",
                    icon = Icons.Filled.Archive,
                    subtitle = "${state.filteredHistory.size} registro(s) no filtro atual.",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            HistoryPeriod.entries.forEach { period ->
                                FilterChip(
                                    selected = state.period == period,
                                    onClick = { vm.setPeriod(period) },
                                    label = { Text(period.label) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = state.search,
                            onValueChange = vm::setSearch,
                            label = { Text("Buscar por nome do medicamento") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(onClick = vm::clearFilters, modifier = Modifier.fillMaxWidth()) {
                            Text("Limpar filtros")
                        }

                        if (state.filteredHistory.isEmpty()) {
                            Text(
                                "Nenhum registro para este filtro.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // Bounded on purpose: the card lives inside a scrolling
                            // column, so an unbounded list would defeat recycling.
                            state.filteredHistory.take(100).forEach { entry ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.medicationName, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            listOfNotNull(
                                                Schedule.localDateOf(entry.scheduledFor ?: entry.createdAt)
                                                    ?.format(DAY_MONTH),
                                                entry.scheduleTime.takeIf { it.isNotBlank() },
                                                entry.dosage.takeIf { it.isNotBlank() },
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        statusLabel(entry.status),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = when (entry.status) {
                                            "taken" -> GreenOk
                                            "missed" -> RedMiss
                                            else -> Amber
                                        },
                                    )
                                }
                            }
                            if (state.filteredHistory.size > 100) {
                                Text(
                                    "Mostrando os 100 registros mais recentes de ${state.filteredHistory.size}.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "MemorIA — Relatório de adesão")
                            putExtra(Intent.EXTRA_TEXT, vm.shareText())
                        }
                        context.startActivity(Intent.createChooser(send, "Compartilhar relatório"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text("  Compartilhar relatório")
                }
            }
        }
    }
}

/**
 * A card whose body is replaced by an upgrade prompt for a free account — the
 * mobile form of the web's `.report-premium-lock` overlay.
 */
@Composable
private fun PremiumSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    unlocked: Boolean,
    onOpenPlans: () -> Unit,
    content: @Composable () -> Unit,
) {
    SectionCard(title, icon = icon) {
        if (unlocked) {
            content()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Disponível no plano Premium.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onOpenPlans) { Text("Ver planos") }
            }
        }
    }
}

/** 30 vertical bars, scrollable sideways so each day keeps a readable width. */
@Composable
private fun ThirtyDayChart(days: List<DayAdherence>) {
    if (days.isEmpty()) {
        Text("Sem dados nos últimos 30 dias.", style = MaterialTheme.typography.bodyLarge)
        return
    }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .width(14.dp)
                        .height(100.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val filled = if (day.total == 0) 0f else day.rate.coerceIn(0f, 1f)
                    if (filled > 0f) {
                        Box(
                            Modifier
                                .fillMaxHeight(filled)
                                .width(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (filled >= 1f) GreenOk else Amber),
                        )
                    }
                }
                Text(
                    "${day.date.dayOfMonth}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "taken" -> "Tomada"
    "missed" -> "Perdida"
    "snoozed" -> "Adiada"
    else -> status
}
