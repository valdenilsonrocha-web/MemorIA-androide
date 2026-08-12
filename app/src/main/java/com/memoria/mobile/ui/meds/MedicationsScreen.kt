package com.memoria.mobile.ui.meds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.data.remote.Medication
import com.memoria.mobile.ui.common.EmptyState
import com.memoria.mobile.ui.common.ErrorState
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.theme.GreenOk
import com.memoria.mobile.ui.theme.RedMiss
import com.memoria.mobile.ui.theme.Snooze

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationsScreen(
    contentPadding: PaddingValues,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val vm = repoViewModel { MedicationsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Meus remédios") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Adicionar medicamento")
            }
        },
    ) { inner ->
        val merged = PaddingValues(
            start = inner.calculateStartPadding(LayoutDirection.Ltr),
            end = inner.calculateEndPadding(LayoutDirection.Ltr),
            top = inner.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        )

        when {
            state.loading && state.medications.isEmpty() -> LoadingBox(Modifier.padding(inner))
            state.error != null && state.medications.isEmpty() ->
                ErrorState(state.error!!, onRetry = vm::load, modifier = Modifier.padding(inner))
            state.medications.isEmpty() ->
                EmptyState(
                    "Nenhum medicamento",
                    "Toque em + para adicionar o primeiro remédio e receber lembretes pelo WhatsApp.",
                    Modifier.padding(inner),
                )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = merged,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "Agenda de hoje",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (state.todaySlots.isEmpty()) {
                    item {
                        Text(
                            "Sem doses programadas para hoje.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                } else {
                    items(state.todaySlots, key = { "${it.medication.id}-${it.time}" }) { slot ->
                        DoseCard(
                            slot = slot,
                            onMark = { status -> vm.markDose(slot, status) },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                item {
                    Text(
                        "Todos os remédios",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                items(state.medications, key = { it.id ?: it.name }) { med ->
                    MedicationCard(
                        medication = med,
                        onEdit = { med.id?.let(onEdit) },
                        onDelete = { vm.delete(med) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DoseCard(
    slot: DoseSlot,
    onMark: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    slot.time,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.padding(start = 16.dp)) {
                    // O servidor aceita nomes até 255 caracteres. Sem limite de
                    // linhas, um nome longo empurra os botões "Tomei"/adiar/pular
                    // para fora do ecrã e o cartão ocupa-o por inteiro.
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
                AssistChip(
                    onClick = {},
                    label = { Text(statusLabel(slot.doneStatus)) },
                )
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

@Composable
private fun MedicationCard(
    medication: Medication,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    medication.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${medication.dosage} · ${frequencyLabel(medication.frequency)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (medication.times.isNotEmpty()) {
                    Text(
                        "Horários: ${medication.times.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Text(
                    "Estoque: ${medication.stock}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (medication.stock <= 3) RedMiss else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remover", tint = RedMiss)
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

private fun frequencyLabel(freq: String): String = when (freq) {
    "weekly" -> "semanal"
    "alternate" -> "dias alternados"
    else -> "diário"
}
