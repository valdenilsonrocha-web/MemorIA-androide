package com.memoria.mobile.ui.admin

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.AreaChart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.data.remote.CountBucket
import com.memoria.mobile.data.remote.OwnerStats
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.BarRow
import com.memoria.mobile.ui.common.ErrorState
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.SectionCard
import com.memoria.mobile.ui.common.StatTile
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.theme.TextPrimary
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val DATE_TIME_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm:ss")

// Cores das barras e dos cartões de KPI, de `.admin-bar-fill.*` / `.admin-stat-*` no main.css.
private val BAR_BLUE = Color(0xFF2F7DFF)
private val BAR_GREEN = Color(0xFF16A34A)
private val BAR_PINK = Color(0xFFDB2777)
private val BAR_PURPLE = Color(0xFF0053D6)
private val BAR_ORANGE = Color(0xFFEA580C)
private val KPI_GREEN = Color(0xFF16A34A)
private val KPI_RED = Color(0xFFDC2626)
private val KPI_BLUE = Color(0xFF0060F8)

/** Mesma ordem de cores que a web aplica às faixas etárias. */
private val AGE_COLORS = listOf(BAR_BLUE, BAR_GREEN, BAR_BLUE, BAR_ORANGE, BAR_PURPLE, BAR_PINK)

/** "Ferramentas externas" — os mesmos seis atalhos da web. */
private data class ExternalTool(val name: String, val caption: String, val url: String, val icon: ImageVector)

private val EXTERNAL_TOOLS = listOf(
    ExternalTool("Stripe", "Pagamentos", "https://dashboard.stripe.com", Icons.Filled.CreditCard),
    ExternalTool("Google Play", "Downloads Android", "https://play.google.com/console", Icons.Filled.PlayArrow),
    ExternalTool("App Store", "Downloads iOS", "https://appstoreconnect.apple.com", Icons.Filled.PhoneIphone),
    ExternalTool("Neon DB", "Banco de dados", "https://console.neon.tech", Icons.Filled.Storage),
    ExternalTool("Resend", "E-mails", "https://app.resend.com", Icons.Filled.Email),
    ExternalTool("Render", "Servidor", "https://render.com/dashboard", Icons.Filled.Dns),
)

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
                "Não foi possível carregar os dados. Verifique se o backend está ativo e se " +
                    "você está logado como administrador.",
                onRetry = vm::load,
                modifier = Modifier.padding(inner),
            )
            else -> AdminContent(stats, onRefresh = vm::load, modifier = Modifier.padding(inner))
        }
    }
}

@Composable
private fun AdminContent(stats: OwnerStats, onRefresh: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    Column(
        modifier
            .fillMaxSize()
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
                StatTile(raw(overview?.totalUsers), "Usuários cadastrados", Modifier.weight(1f), icon = Icons.Filled.Group)
                StatTile(raw(overview?.newToday), "Novos hoje", Modifier.weight(1f), icon = Icons.Filled.PersonAdd)
                StatTile(raw(overview?.newThisWeek), "Novos esta semana", Modifier.weight(1f), icon = Icons.Filled.CalendarViewWeek)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(raw(overview?.newThisMonth), "Novos este mês", Modifier.weight(1f), icon = Icons.Filled.CalendarMonth)
                StatTile(raw(overview?.activeUsers), "Ativos (7 dias)", Modifier.weight(1f), icon = Icons.Filled.Wifi)
                StatTile(raw(overview?.totalMedications), "Medicamentos ativos", Modifier.weight(1f), icon = Icons.Filled.Medication)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    kpi(overview?.totalTaken), "Doses tomadas (total)", Modifier.weight(1f),
                    accent = KPI_GREEN, icon = Icons.Filled.CheckCircle,
                )
                StatTile(
                    kpi(overview?.totalMissed), "Doses perdidas (total)", Modifier.weight(1f),
                    accent = KPI_RED, icon = Icons.Filled.Cancel,
                )
                StatTile(
                    overview?.adherenceRate?.let { "$it%" } ?: "—", "Taxa de adesão geral", Modifier.weight(1f),
                    accent = KPI_BLUE, icon = Icons.Filled.Percent,
                )
            }
        }

        BarsCard(
            "Usuários por Sexo",
            Icons.Filled.Wc,
            stats.byGender.sortedByDescending { it.count }.map {
                Triple(genderLabel(it.gender), it.count, genderColor(it.gender))
            },
        )

        BarsCard(
            "Usuários por Faixa Etária",
            Icons.Filled.Cake,
            stats.byAge.mapIndexed { i, bucket ->
                Triple(bucket.group, bucket.count, AGE_COLORS.getOrElse(i) { BAR_BLUE })
            },
        )

        SectionCard(titleColor = TextPrimary, title = "Cadastros — Últimos 30 Dias", icon = Icons.Filled.AreaChart) {
            TrendBars(stats)
        }

        BarsCard(
            "Usuários por Estado (top 10)",
            Icons.Filled.Map,
            stats.byState.map { Triple(it.state?.ifBlank { null } ?: "?", it.count, BAR_GREEN) },
        )
        BarsCard(
            "Usuários por Cidade (top 10)",
            Icons.Filled.LocationCity,
            stats.byCity.map { Triple(cityLabel(it), it.count, BAR_ORANGE) },
        )
        BarsCard(
            "Medicamentos mais usados",
            Icons.Filled.EmojiEvents,
            stats.topMedications.map { Triple(it.name.orEmpty(), it.count, BAR_PURPLE) },
        )

        SectionCard(titleColor = TextPrimary, title = "Cadastros recentes", icon = Icons.Filled.PersonAdd) {
            if (stats.recentSignups.isEmpty()) {
                MutedLine("Nenhum cadastro ainda.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    stats.recentSignups.forEach { signup ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    (signup.name.firstOrNull() ?: '?').uppercase(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(
                                    signup.name.ifBlank { "Usuário" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                val location = listOf(signup.city, signup.state)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" / ")
                                val sex = genderLabelOrBlank(signup.gender)
                                Text(
                                    listOf(signup.email, location, sex)
                                        .filter { it.isNotBlank() }
                                        .joinToString("  ·  ")
                                        .ifBlank { "—" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                formatDate(signup.createdAt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        SectionCard(titleColor = TextPrimary, title = "Ferramentas externas", icon = Icons.AutoMirrored.Filled.OpenInNew) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EXTERNAL_TOOLS.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { tool ->
                            Card(
                                Modifier
                                    .weight(1f)
                                    .clickable {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tool.url)))
                                        } catch (_: ActivityNotFoundException) {
                                            // Sem navegador no aparelho: nada a abrir.
                                        }
                                    },
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(tool.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        tool.caption,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            formatDateTime(stats.generatedAt)?.let {
                MutedLine("Atualizado em: $it")
            }
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Sync, contentDescription = null)
                Text("  Atualizar dados")
            }
        }
    }
}

@Composable
private fun BarsCard(title: String, icon: ImageVector, items: List<Triple<String, Int, Color>>) {
    SectionCard(title, icon = icon, titleColor = TextPrimary) {
        val max = items.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1
        if (items.isEmpty()) {
            MutedLine("Sem dados.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { (label, count, color) ->
                    BarRow(label, "$count", count.toFloat() / max, color = color)
                }
            }
        }
    }
}

/** The web's sparkline: one column per day, height relative to the busiest day. */
@Composable
private fun TrendBars(stats: OwnerStats) {
    val points = stats.signupTrend
    if (points.isEmpty()) return
    val max = points.maxOf { it.count }.takeIf { it > 0 } ?: 1
    Row(
        Modifier.fillMaxWidth().height(72.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        points.forEach { point ->
            val h = maxOf(4, (point.count.toFloat() / max * 64).toInt())
            Box(
                Modifier
                    .weight(1f)
                    .height(h.dp)
                    .background(BAR_BLUE, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
            )
        }
    }
}

@Composable
private fun MutedLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun kpi(value: Int?): String = value?.let { String.format(java.util.Locale.forLanguageTag("pt-BR"), "%,d", it) } ?: "—"

private fun cityLabel(bucket: CountBucket): String {
    val city = bucket.city?.ifBlank { null } ?: "?"
    val state = bucket.state?.ifBlank { null }
    return if (state != null) "$city/$state" else city
}

private fun genderColor(code: String?): Color = when (code) {
    "female" -> BAR_PINK
    "other" -> BAR_PURPLE
    else -> BAR_BLUE
}

private fun parseInstant(raw: String?): OffsetDateTime? =
    raw?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

private fun formatDate(raw: String?): String =
    parseInstant(raw)?.atZoneSameInstant(ZoneId.systemDefault())?.format(DATE_BR) ?: ""

private fun formatDateTime(raw: String?): String? =
    parseInstant(raw)?.atZoneSameInstant(ZoneId.systemDefault())?.format(DATE_TIME_BR)

/** The first six web KPIs are written as-is (no `toLocaleString`). */
private fun raw(value: Int?): String = value?.toString() ?: "—"
