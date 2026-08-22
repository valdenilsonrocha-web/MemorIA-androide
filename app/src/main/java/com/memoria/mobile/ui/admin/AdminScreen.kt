package com.memoria.mobile.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.data.remote.CountBucket
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.BarRow
import com.memoria.mobile.ui.common.DetailRow
import com.memoria.mobile.ui.common.ErrorState
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.Schedule
import com.memoria.mobile.ui.common.SectionCard
import com.memoria.mobile.ui.common.StatTile
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.theme.GreenOk
import com.memoria.mobile.ui.theme.RedMiss
import java.time.format.DateTimeFormatter

private val DATE_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * "Painel do Proprietário" — the web `adminPage`. The endpoint is admin-only, so
 * a non-admin account simply sees the server's refusal rather than a blank page.
 */
@Composable
fun AdminScreen(onBack: () -> Unit) {
    val vm = repoViewModel { AdminViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(topBar = { BackTopBar("Painel do Proprietário", onBack) }) { inner ->
        val stats = state.stats
        when {
            state.loading && stats == null -> LoadingBox(Modifier.padding(inner))
            stats == null -> ErrorState(
                state.error ?: "Não foi possível carregar as métricas.",
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
                Text(
                    "Métricas exclusivas do MemorIA",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val overview = stats.overview
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("${overview?.totalUsers ?: 0}", "Usuários cadastrados", Modifier.weight(1f))
                        StatTile("${overview?.newToday ?: 0}", "Novos hoje", Modifier.weight(1f))
                        StatTile("${overview?.newThisWeek ?: 0}", "Novos na semana", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("${overview?.newThisMonth ?: 0}", "Novos no mês", Modifier.weight(1f))
                        StatTile("${overview?.activeUsers ?: 0}", "Ativos (7 dias)", Modifier.weight(1f))
                        StatTile("${overview?.totalMedications ?: 0}", "Medicamentos ativos", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("${overview?.totalTaken ?: 0}", "Doses tomadas", Modifier.weight(1f), accent = GreenOk)
                        StatTile("${overview?.totalMissed ?: 0}", "Doses perdidas", Modifier.weight(1f), accent = RedMiss)
                        StatTile(overview?.adherenceRate?.let { "$it%" } ?: "—", "Adesão geral", Modifier.weight(1f))
                    }
                }

                BucketCard("Usuários por Sexo", Icons.Filled.Wc, stats.byGender) { genderLabel(it.gender) }

                SectionCard("Usuários por Faixa Etária", icon = Icons.Filled.Cake) {
                    val max = stats.byAge.maxOfOrNull { it.count } ?: 0
                    if (stats.byAge.isEmpty() || max == 0) {
                        EmptyLine()
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            stats.byAge.forEach { bucket ->
                                BarRow(bucket.group, "${bucket.count}", bucket.count.toFloat() / max)
                            }
                        }
                    }
                }

                SectionCard("Cadastros — Últimos 30 Dias", icon = Icons.AutoMirrored.Filled.ShowChart) {
                    val max = stats.signupTrend.maxOfOrNull { it.count } ?: 0
                    if (stats.signupTrend.isEmpty() || max == 0) {
                        EmptyLine()
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            stats.signupTrend.forEach { point ->
                                BarRow(point.date, "${point.count}", point.count.toFloat() / max)
                            }
                        }
                    }
                }

                BucketCard("Usuários por Estado (top 10)", Icons.Filled.Map, stats.byState) { it.label }
                BucketCard("Usuários por Cidade (top 10)", Icons.Filled.LocationCity, stats.byCity) { it.label }
                BucketCard("Medicamentos mais usados", Icons.Filled.EmojiEvents, stats.topMedications) { it.label }

                SectionCard("Cadastros Recentes", icon = Icons.Filled.PersonAdd) {
                    if (stats.recentSignups.isEmpty()) {
                        EmptyLine()
                    } else {
                        Column {
                            stats.recentSignups.forEach { signup ->
                                DetailRow(
                                    signup.name.ifBlank { "Sem nome" },
                                    listOfNotNull(
                                        listOfNotNull(
                                            signup.city.takeIf { it.isNotBlank() },
                                            signup.state.takeIf { it.isNotBlank() },
                                        ).joinToString("/").takeIf { it.isNotBlank() },
                                        Schedule.localDateOf(signup.createdAt)?.format(DATE_BR),
                                    ).joinToString(" · "),
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Schedule.localDateOf(stats.generatedAt)?.let {
                        Text(
                            "Gerado em ${it.format(DATE_BR)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = vm::load, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Text("  Atualizar métricas")
                    }
                }
            }
        }
    }
}

@Composable
private fun BucketCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    buckets: List<CountBucket>,
    label: (CountBucket) -> String,
) {
    SectionCard(title, icon = icon) {
        val max = buckets.maxOfOrNull { it.count } ?: 0
        if (buckets.isEmpty() || max == 0) {
            EmptyLine()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                buckets.forEach { bucket ->
                    BarRow(label(bucket), "${bucket.count}", bucket.count.toFloat() / max)
                }
            }
        }
    }
}

@Composable
private fun EmptyLine() {
    Text(
        "Sem dados disponíveis.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
