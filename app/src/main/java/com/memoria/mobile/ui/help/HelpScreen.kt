package com.memoria.mobile.ui.help

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.SectionCard
import com.memoria.mobile.ui.theme.GreenOk
import kotlinx.coroutines.launch

private val TUTORIAL_STEPS = listOf(
    "Adicione seus Medicamentos" to
        "Toque no botão + na tela Início e preencha as informações do seu medicamento.",
    "Ative as Notificações" to
        "Vá em Mais > Otimização do App e siga os passos para receber os lembretes na hora certa.",
    "Confirme quando Tomar" to
        "Quando o lembrete chegar, toque em Tomei para registrar. Use Adiar se precisar de mais tempo.",
    "Acompanhe no Histórico" to
        "Veja todos os medicamentos tomados na aba Histórico e acompanhe sua adesão em Relatórios.",
)

private val FAQ = listOf(
    "Como adiciono múltiplos horários para um medicamento?" to
        "Na tela de cadastro do medicamento, depois de preencher o primeiro horário, toque em " +
        "Adicionar horário para incluir mais horários no mesmo dia.",
    "O que fazer se não recebo notificações?" to
        "Vá em Mais > Otimização do App e siga o passo a passo de bateria e notificações. " +
        "A maioria dos celulares pausa apps em segundo plano por padrão.",
    "Como edito um medicamento já cadastrado?" to
        "Na tela Início, toque no medicamento para abrir os detalhes e depois em Editar.",
    "Meus dados ficam guardados onde?" to
        "Medicamentos, histórico e receitas ficam no servidor MemorIA, ligados à sua conta. " +
        "Medições de saúde, rede de cuidado e consultas ficam apenas neste celular.",
    "Como faço backup dos meus dados?" to
        "Entre na sua conta em outro aparelho: medicamentos, histórico e receitas são " +
        "sincronizados automaticamente pelo servidor.",
    "O aplicativo é gratuito?" to
        "Sim, as funções essenciais são gratuitas. O plano Premium libera relatórios avançados, " +
        "receitas por foto e alertas para contatos.",
)

private val TIPS = listOf(
    "Configure os horários dos medicamentos de acordo com sua rotina diária para não esquecer.",
    "Mantenha o estoque atualizado para receber alertas quando os medicamentos estiverem acabando.",
    "Use as instruções especiais para lembrar se deve tomar com alimentos ou em jejum.",
    "Compartilhe o relatório de adesão com seu médico nas consultas.",
    "Adicione contatos de emergência no seu perfil para facilitar em caso de necessidade.",
)

private const val SUPPORT_EMAIL = "contato@brasmaster.com.br"
private const val SUPPORT_WHATSAPP = "https://wa.me/5511999999999"

/** "Ajuda e Tutorial" — the web `helpPage`. */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // An emulator or a stripped ROM may have no mail/browser app at all; without
    // this the tap would crash the activity instead of explaining itself.
    val open: (Intent) -> Unit = { intent ->
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("Nenhum aplicativo disponível para abrir este contato.") }
        }
    }

    Scaffold(
        topBar = { BackTopBar("Ajuda e Tutorial", onBack) },
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
                "Aprenda a usar o MemorIA",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard("Tutorial Rápido", icon = Icons.Filled.PlayCircle) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TUTORIAL_STEPS.forEachIndexed { index, (title, description) ->
                        Row {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    description,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            SectionCard("Perguntas Frequentes", icon = Icons.AutoMirrored.Filled.HelpOutline) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FAQ.forEach { (question, answer) -> FaqItem(question, answer) }
                }
            }

            SectionCard("Dicas de Uso", icon = Icons.Filled.Lightbulb) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TIPS.forEach { tip ->
                        Row {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = GreenOk)
                            Text(
                                "  $tip",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            SectionCard("Precisa de Mais Ajuda?", icon = Icons.Filled.Email) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Se você ainda tiver dúvidas ou precisar de suporte, entre em contato:",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OutlinedButton(
                        onClick = {
                            open(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL")))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(SUPPORT_EMAIL)
                    }
                    OutlinedButton(
                        onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_WHATSAPP))) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Falar pelo WhatsApp")
                    }
                }
            }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(question, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Recolher" else "Expandir",
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                answer,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
