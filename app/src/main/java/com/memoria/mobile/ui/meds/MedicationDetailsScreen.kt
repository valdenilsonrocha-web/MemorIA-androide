package com.memoria.mobile.ui.meds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.DetailRow
import com.memoria.mobile.ui.common.ErrorState
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.SectionCard
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.theme.GreenOk
import com.memoria.mobile.ui.theme.RedMiss

/** "Detalhes do Medicamento" — the web `medicationDetailsPage`. */
@Composable
fun MedicationDetailsScreen(
    medicationId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val vm = repoViewModel { MedicationDetailsViewModel(it, medicationId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(medicationId) { vm.load() }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(state.error) {
        // A load failure already owns the whole screen; only report the rest.
        if (state.medication != null) {
            state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
        }
    }

    Scaffold(
        topBar = { BackTopBar("Detalhes do Medicamento", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        val med = state.medication
        when {
            state.loading && med == null -> LoadingBox(Modifier.padding(inner))
            med == null -> ErrorState(
                state.error ?: "Medicamento não encontrado.",
                onRetry = vm::load,
                modifier = Modifier.padding(inner),
            )
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionCard("Informações Básicas", icon = Icons.Filled.Medication) {
                    Column {
                        DetailRow("Nome", med.name)
                        DetailRow("Dosagem", med.dosage)
                        DetailRow("Frequência", frequencyText(med.frequency))
                        if (med.frequency == "weekly") {
                            DetailRow("Dias", weekDaysText(med.weekDays))
                        }
                        DetailRow(
                            "Horários",
                            med.times.filter { it.isNotBlank() }
                                .joinToString(", ")
                                .ifBlank { "Não definido" },
                        )
                        DetailRow(
                            "Estoque",
                            "${med.stock} doses",
                            valueColor = if (med.stock <= 3) RedMiss else MaterialTheme.colorScheme.onSurface,
                        )
                        DetailRow("Situação", if (med.active) "Ativo" else "Inativo")
                    }
                }

                if (!med.instructions.isNullOrBlank()) {
                    SectionCard("Instruções Especiais", icon = Icons.Filled.Info) {
                        Text(med.instructions, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                med.supplier?.takeIf { it.name.isNotBlank() || it.phone.isNotBlank() }?.let { supplier ->
                    SectionCard("Fornecedor / Farmácia") {
                        Column {
                            if (supplier.name.isNotBlank()) DetailRow("Nome", supplier.name)
                            if (supplier.phone.isNotBlank()) DetailRow("Contato", supplier.phone)
                        }
                    }
                }

                SectionCard("Estatísticas", icon = Icons.AutoMirrored.Filled.ShowChart) {
                    Column {
                        DetailRow("Total de doses", "${state.total}")
                        DetailRow("Doses tomadas", "${state.taken}", valueColor = GreenOk)
                        DetailRow("Doses perdidas", "${state.missed}", valueColor = RedMiss)
                        DetailRow(
                            "Taxa de adesão",
                            "${state.adherence}%",
                            valueColor = if (state.adherence >= 80) GreenOk else RedMiss,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onEdit(medicationId) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Text("  Editar")
                    }
                    Button(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RedMiss),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text("  Excluir")
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir medicamento") },
            text = { Text("Esta ação não pode ser desfeita. Deseja continuar?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete() }) {
                    Text("Excluir", color = RedMiss)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}

private fun frequencyText(frequency: String): String = when (frequency) {
    "alternate" -> "Dias alternados"
    "weekly" -> "Dias específicos da semana"
    "daily" -> "Todos os dias"
    else -> "Não definido"
}

private fun weekDaysText(days: List<Int>?): String {
    if (days.isNullOrEmpty()) return "N/A"
    val names = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
    return days.sorted().mapNotNull { names.getOrNull(it) }.joinToString(", ").ifBlank { "N/A" }
}
