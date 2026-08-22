package com.memoria.mobile.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.nav.Routes

private data class MoreEntry(
    val route: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
    val adminOnly: Boolean = false,
)

private val ENTRIES = listOf(
    MoreEntry(Routes.PLANS, "Planos", "Premium, benefícios e assinatura", Icons.Filled.WorkspacePremium),
    MoreEntry(Routes.CALENDAR, "Calendário", "Seus medicamentos dia a dia", Icons.Filled.CalendarMonth),
    MoreEntry(Routes.REPORTS, "Relatórios", "Adesão, sequências e histórico", Icons.Filled.Assessment),
    MoreEntry(Routes.REPLENISHMENT, "Reposição", "Quando comprar cada medicamento", Icons.Filled.Inventory),
    MoreEntry(Routes.DOCTORS, "Meus Médicos", "Rede de cuidado e consultas", Icons.Filled.MedicalServices),
    MoreEntry(Routes.PRESCRIPTIONS, "Minhas Receitas", "Fotos das receitas médicas", Icons.Filled.Description),
    MoreEntry(Routes.PROFILE, "Meu Perfil", "Dados pessoais e contatos de emergência", Icons.Filled.AccountCircle),
    MoreEntry(Routes.WHATSAPP, "WhatsApp", "Conexão e eventos de mensagens", Icons.AutoMirrored.Filled.Chat),
    MoreEntry(Routes.SETTINGS, "Configurações", "Conta, servidor e sessão", Icons.Filled.Settings),
    MoreEntry(Routes.OPTIMIZATION, "Otimização do App", "Bateria, alertas e tela bloqueada", Icons.Filled.BatteryChargingFull),
    MoreEntry(Routes.PRIVACY, "Privacidade e dados", "Exportar; consentimento e apagar conta (LGPD)", Icons.Filled.Policy),
    MoreEntry(Routes.HELP, "Ajuda e Tutorial", "Como usar o MemorIA", Icons.AutoMirrored.Filled.HelpOutline),
    MoreEntry(Routes.ADMIN, "Painel do Proprietário", "Métricas do MemorIA", Icons.Filled.Insights, adminOnly = true),
)

/**
 * "Mais" — the web `moreMenu`. The admin entry only appears for an account the
 * server marks as admin, matching `admin-only-menu-item` on the web.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(contentPadding: PaddingValues, onNavigate: (String) -> Unit) {
    val vm = repoViewModel { MoreViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(topBar = { TopAppBar(title = { Text("Mais") }) }) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.userName?.takeIf { it.isNotBlank() }?.let { name ->
                Text(name, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (state.isPremium) "Plano Premium" else "Plano Gratuito",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            ENTRIES.filter { !it.adminOnly || state.isAdmin }.forEach { entry ->
                MoreRow(entry) { onNavigate(entry.route) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreRow(entry: MoreEntry, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(entry.label, style = MaterialTheme.typography.titleLarge)
                Text(
                    entry.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
