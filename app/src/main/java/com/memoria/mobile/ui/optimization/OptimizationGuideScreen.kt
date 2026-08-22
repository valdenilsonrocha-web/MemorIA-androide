package com.memoria.mobile.ui.optimization

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.SectionCard
import kotlinx.coroutines.launch

private val PURPOSE = listOf(
    "Evitar que o celular pause os lembretes do MemorIA em segundo plano.",
    "Permitir notificações em pop-up para chamar atenção na hora certa.",
    "Exibir alertas mesmo com a tela bloqueada.",
)

private val ANDROID_STEPS = listOf(
    "Abra as Configurações do celular e procure por Apps ou Aplicativos.",
    "Entre em MemorIA e toque em Bateria.",
    "Selecione Sem restrição ou Não otimizar (o nome muda conforme a marca).",
    "Em Notificações do app, ative: Mostrar notificações, Pop-up e Sons.",
    "Em Tela de bloqueio, ative: Mostrar conteúdo completo das notificações.",
    "Se existir permissão de Iniciar em segundo plano / Autostart, mantenha ativada.",
)

private val IOS_STEPS = listOf(
    "Abra Ajustes e toque em Notificações.",
    "Entre em MemorIA e ative Permitir Notificações.",
    "Ative: Tela Bloqueada, Central de Notificações e Tiras (banners).",
    "Ative Sons e em Mostrar Prévias selecione Sempre.",
    "Em Ajustes > Geral > Atualização em 2º Plano, mantenha ativo para o app.",
    "Desative modos de foco que possam silenciar os alertas nos horários críticos.",
)

private val TEST_TIPS = listOf(
    "Configure um medicamento para daqui a 2 minutos.",
    "Bloqueie a tela do celular e aguarde o lembrete.",
    "Se não aparecer, revise os passos acima de bateria e tela bloqueada.",
)

/**
 * "Otimização do App" — the web `optimizationGuidePage`, plus buttons that jump
 * straight to the two system screens the steps describe (the web page can only
 * describe them).
 */
@Composable
fun OptimizationGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val openSettings: (Intent) -> Unit = { intent ->
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            scope.launch {
                snackbar.showSnackbar("Esta tela do sistema não está disponível neste aparelho.")
            }
        }
    }

    Scaffold(
        topBar = { BackTopBar("Otimização do App", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Melhore alertas, economia de bateria e notificações na tela bloqueada.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard("Atalhos do sistema", icon = Icons.Filled.Notifications) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            openSettings(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Notifications, contentDescription = null)
                        Text("  Abrir notificações do MemorIA")
                    }
                    OutlinedButton(
                        onClick = {
                            openSettings(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.BatteryChargingFull, contentDescription = null)
                        Text("  Abrir detalhes do app (bateria)")
                    }
                }
            }

            SectionCard("O que esta página resolve", icon = Icons.Filled.RocketLaunch) {
                BulletList(PURPOSE)
            }

            SectionCard("Android — passo a passo", icon = Icons.Filled.Android) {
                NumberedList(ANDROID_STEPS)
            }

            SectionCard("iPhone (iOS) — passo a passo", icon = Icons.Filled.PhoneIphone) {
                NumberedList(IOS_STEPS)
            }

            SectionCard("Dica rápida para testar", icon = Icons.Filled.Lightbulb) {
                BulletList(TEST_TIPS)
            }
        }
    }
}

@Composable
private fun BulletList(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row {
                Text("•  ", style = MaterialTheme.typography.bodyLarge)
                Text(item, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun NumberedList(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { index, item ->
            Row {
                Text(
                    "${index + 1}.  ",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(item, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
