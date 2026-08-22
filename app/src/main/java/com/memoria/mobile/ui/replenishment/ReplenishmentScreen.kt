package com.memoria.mobile.ui.replenishment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.DetailRow
import com.memoria.mobile.ui.common.EmptyState
import com.memoria.mobile.ui.common.ErrorState
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.theme.Amber
import com.memoria.mobile.ui.theme.GreenOk
import com.memoria.mobile.ui.theme.RedMiss
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

/** "Reposição" — the web `replenishmentPage`: when to buy each medication again. */
@Composable
fun ReplenishmentScreen(onBack: () -> Unit) {
    val vm = repoViewModel { ReplenishmentViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(topBar = { BackTopBar("Reposição", onBack) }) { inner ->
        when {
            state.loading && state.items.isEmpty() -> LoadingBox(Modifier.padding(inner))
            state.error != null && state.items.isEmpty() ->
                ErrorState(state.error!!, onRetry = vm::load, modifier = Modifier.padding(inner))
            state.items.isEmpty() -> EmptyState(
                "Nada a repor",
                "Nenhum medicamento ativo para calcular reposição.",
                Modifier.padding(inner),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "Veja quando comprar novamente cada medicamento com base no estoque atual.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.items, key = { it.medication.id ?: it.medication.name }) { item ->
                    ReplenishmentCard(item)
                }
            }
        }
    }
}

@Composable
private fun ReplenishmentCard(item: ReplenishmentItem) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.medication.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val tint = urgencyColor(item.urgency)
                AssistChip(
                    onClick = {},
                    label = { Text("Urgência ${item.urgency.label}") },
                    colors = AssistChipDefaults.assistChipColors(labelColor = tint),
                )
            }
            Column {
                DetailRow(
                    "Estoque atual",
                    "${item.stock} doses",
                    valueColor = if (item.stock <= 3) RedMiss else Color.Unspecified,
                )
                DetailRow("Consumo estimado", "${format2(item.dailyDoses)} dose/dia")
                DetailRow("Duração do estoque", "${format1(item.daysRemaining)} dias")
                DetailRow("Fim do estoque", item.stockEndDate.format(DATE_BR))
                DetailRow(
                    "Data sugerida de compra",
                    if (item.buyToday) "Comprar hoje" else "Comprar até ${item.purchaseDate.format(DATE_BR)}",
                    valueColor = urgencyColor(item.urgency),
                )
            }
        }
    }
}

private fun urgencyColor(urgency: Urgency): Color = when (urgency) {
    Urgency.HIGH -> RedMiss
    Urgency.MEDIUM -> Amber
    Urgency.LOW -> GreenOk
}

private fun format1(value: Double): String = String.format(PT_BR, "%.1f", value)

private fun format2(value: Double): String = String.format(PT_BR, "%.2f", value)
