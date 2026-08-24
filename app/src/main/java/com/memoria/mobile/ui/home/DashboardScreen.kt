package com.memoria.mobile.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.data.remote.Medication
import com.memoria.mobile.ui.common.DoseSlot
import com.memoria.mobile.ui.common.ErrorState
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.StatTile
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.theme.Amber
import com.memoria.mobile.ui.theme.GreenOk
import com.memoria.mobile.ui.theme.RedMiss
import com.memoria.mobile.ui.theme.Snooze

/**
 * "Início" — the web `dashboardPage`: today's counters, the doses still due, and
 * the full medication list underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    contentPadding: PaddingValues,
    onAdd: () -> Unit,
    onOpenMedication: (String) -> Unit,
    onOpenPlans: () -> Unit,
) {
    val vm = repoViewModel { DashboardViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    LaunchedEffect(state.error) {
        // Only report over existing content. Clearing the error unconditionally
        // sent a failed load to a snackbar that then vanished, leaving the page
        // reading "Nenhum medicamento cadastrado" — a medication app must never
        // show an empty schedule when it simply could not reach the server.
        if (state.medications.isNotEmpty()) {
            state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Início") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // Lifted clear of the bottom navigation bar. This Scaffold is nested
            // inside the one that draws the tab bar, so without the offset the
            // FAB lands underneath it — invisible, untappable, and the only way
            // to add a medication.
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Adicionar medicamento")
            }
        },
    ) { inner ->
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
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(greeting(state), style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Mantenha sua saúde em dia",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("${state.todayCount}", "Hoje", Modifier.weight(1f))
                    StatTile("${state.pendingCount}", "Pendentes", Modifier.weight(1f), accent = Amber)
                    StatTile("${state.completedCount}", "Tomados", Modifier.weight(1f), accent = GreenOk)
                }
            }

            item { Text("Próximos Horários", style = MaterialTheme.typography.titleLarge) }
            if (state.upcoming.isEmpty()) {
                item {
                    Text(
                        if (state.todayCount == 0) {
                            "Sem doses programadas para hoje."
                        } else {
                            "Tudo em dia por hoje. ✅"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.upcoming, key = { "${it.medication.id}-${it.time}" }) { slot ->
                    DoseCard(slot = slot, onMark = { vm.markDose(slot, it) })
                }
            }

            item { Text("Todos os Medicamentos", style = MaterialTheme.typography.titleLarge) }
            if (state.medications.isEmpty()) {
                item {
                    Text(
                        "Nenhum medicamento cadastrado. Toque em + para adicionar o primeiro.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.medications, key = { it.id ?: it.name }) { med ->
                    MedicationSummaryCard(med) { med.id?.let(onOpenMedication) }
                }
            }

            if (state.user?.isPremium != true) {
                item { PremiumBanner(onOpenPlans) }
            }
        }
    }
}

private fun greeting(state: DashboardUiState): String {
    val firstName = state.user?.name?.trim().orEmpty().substringBefore(" ")
    return if (firstName.isBlank()) "Seus Medicamentos" else "Olá, $firstName"
}

@Composable
private fun DoseCard(slot: DoseSlot, onMark: (String) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    slot.time,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.padding(start = 16.dp)) {
                    Text(
                        slot.medication.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        slot.medication.dosage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (slot.doneStatus != null) {
                AssistChip(onClick = {}, label = { Text(statusLabel(slot.doneStatus)) })
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onMark("taken") },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenOk),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Text(" Tomei")
                    }
                    OutlinedButton(onClick = { onMark("snoozed") }) {
                        Icon(Icons.Filled.Snooze, contentDescription = "Adiar", tint = Snooze)
                    }
                    OutlinedButton(onClick = { onMark("missed") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Não tomei", tint = RedMiss)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationSummaryCard(medication: Medication, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                medication.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${medication.dosage} · ${medication.times.joinToString(", ").ifBlank { "sem horários" }}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Estoque: ${medication.stock} doses",
                style = MaterialTheme.typography.bodyLarge,
                color = if (medication.stock <= 3) RedMiss else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PremiumBanner(onOpenPlans: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Dica", style = MaterialTheme.typography.labelLarge, color = Amber)
            Text("Cuide melhor da sua saúde com o Plano Premium", style = MaterialTheme.typography.titleLarge)
            Text(
                "Remova anúncios, receba relatórios avançados e libere funções exclusivas.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenPlans) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null)
                Text("  Ver Planos")
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "taken" -> "Tomada ✅"
    "snoozed" -> "Adiada"
    "missed" -> "Não tomada"
    else -> status
}
