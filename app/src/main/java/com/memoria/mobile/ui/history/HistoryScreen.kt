package com.memoria.mobile.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.data.remote.Adherence
import com.memoria.mobile.data.remote.HistoryEntry
import com.memoria.mobile.ui.common.EmptyState
import com.memoria.mobile.ui.common.ErrorState
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.theme.GreenOk
import com.memoria.mobile.ui.theme.RedMiss
import com.memoria.mobile.ui.theme.Snooze
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(contentPadding: PaddingValues) {
    val vm = repoViewModel { HistoryViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Histórico e adesão") }) },
    ) { inner ->
        when {
            state.loading && state.entries.isEmpty() -> LoadingBox(Modifier.padding(inner))
            state.error != null && state.entries.isEmpty() ->
                ErrorState(state.error!!, onRetry = vm::load, modifier = Modifier.padding(inner))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.adherence?.let { item { AdherenceCard(it) } }

                if (state.entries.isEmpty()) {
                    item {
                        EmptyState(
                            "Sem histórico ainda",
                            "Assim que registrar suas doses, elas aparecem aqui.",
                        )
                    }
                } else {
                    items(state.entries, key = { it.id ?: "${it.medicationName}-${it.scheduledFor}" }) { entry ->
                        HistoryRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdherenceCard(adherence: Adherence) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Adesão (${adherence.period})", style = MaterialTheme.typography.titleLarge)
            Text(
                adherence.adherenceRate,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Tomadas: ${adherence.taken}", color = GreenOk)
                Text("Perdidas: ${adherence.missed}", color = RedMiss)
                Text("Total: ${adherence.total}")
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.medicationName, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${entry.scheduleTime} · ${formatDate(entry.scheduledFor)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                statusLabel(entry.status),
                style = MaterialTheme.typography.labelLarge,
                color = statusColor(entry.status),
            )
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "taken" -> "Tomada"
    "missed" -> "Perdida"
    "snoozed" -> "Adiada"
    else -> status
}

private fun statusColor(status: String) = when (status) {
    "taken" -> GreenOk
    "missed" -> RedMiss
    "snoozed" -> Snooze
    else -> GreenOk
}

private fun formatDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        OffsetDateTime.parse(iso)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrDefault(iso.take(10))
}
