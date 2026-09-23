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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memoria.mobile.ui.common.PageGuide
import com.memoria.mobile.ui.common.PageTitle
import com.memoria.mobile.ui.common.PageTopGuide
import com.memoria.mobile.ui.common.SectionCard
import kotlinx.coroutines.launch

/**
 * A step of the web's "Tutorial Rápido". Text between `**` is bold, as the web's
 * `<strong>`. The illustration is the web's inline SVG: an emoji on a pastel card.
 */
private data class TutorialStep(val title: String, val text: String, val emoji: String, val background: Color)

private val TUTORIAL_STEPS = listOf(
    TutorialStep(
        "Adicione seus Medicamentos",
        "Clique no botão **Adicionar** na barra inferior e preencha as informações do seu medicamento.",
        "➕", Color(0xFFE8F4F8),
    ),
    TutorialStep(
        "Ative as Notificações",
        "Vá em **Configurações** e clique em **Ativar Notificações** para receber lembretes.",
        "🔔", Color(0xFFFFF3E0),
    ),
    TutorialStep(
        "Confirme quando Tomar",
        "Quando o alarme tocar, clique em **Tomei** para registrar. Use **Soneca** se precisar adiar.",
        "✅", Color(0xFFE8F5E9),
    ),
    TutorialStep(
        "Acompanhe no Histórico",
        "Veja todos os medicamentos tomados na aba **Histórico** e acompanhe sua adesão.",
        "📊", Color(0xFFF3E5F5),
    ),
)

// Perguntas e respostas da web, palavra por palavra (index.html `.faq-list`).
private val FAQ = listOf(
    "Como adiciono múltiplos horários para um medicamento?" to
        "No formulário de adicionar medicamento, após preencher o primeiro horário, clique em " +
        "**\"Adicionar Horário\"** para incluir mais horários no mesmo dia.",
    "O que fazer se não recebo notificações?" to
        "Verifique se você permitiu notificações nas configurações do navegador. Vá em " +
        "Configurações > Ativar Notificações e siga as instruções.",
    "Como edito um medicamento já cadastrado?" to
        "Na tela inicial, clique no medicamento que deseja editar para ver os detalhes. Depois " +
        "clique em **\"Editar\"**.",
    "Posso usar o aplicativo sem internet?" to
        "Sim! O MemorIA funciona completamente offline. Todos os dados ficam salvos no seu navegador.",
    "Como faço backup dos meus dados?" to
        "Vá em Configurações e clique em **\"Exportar Dados\"**. Isso criará um arquivo com " +
        "todas as suas informações.",
    "O aplicativo é gratuito?" to
        "Sim! O MemorIA é 100% gratuito e de código aberto. Não há custos nem taxas.",
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
        snackbarHost = { SnackbarHost(snackbar) { com.memoria.mobile.ui.common.MemoriaSnackbar(it) } },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // `showPageTopGuide()` prepends the illustration to the page, ABOVE its header.
            Column {
                PageTopGuide(PageGuide.HELP)
                PageTitle("Ajuda e Tutorial", subtitle = "Aprenda a usar o MemorIA")
            }

            SectionCard("Tutorial Rápido", icon = Icons.Filled.PlayCircle) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    TUTORIAL_STEPS.forEachIndexed { index, step ->
                        Row {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                            Column(
                                Modifier.padding(start = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(step.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    rich(step.text),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Box(
                                    Modifier
                                        .widthIn(max = 200.dp)
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(step.background),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(step.emoji, fontSize = 48.sp)
                                }
                            }
                        }
                    }
                }
            }

            SectionCard("Perguntas Frequentes", icon = Icons.AutoMirrored.Filled.Help) { // fa-question-circle
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FAQ.forEach { (question, answer) -> FaqItem(question, answer) }
                }
            }

            SectionCard("Dicas de Uso", icon = Icons.Filled.Lightbulb) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TIPS.forEach { tip ->
                        // `.tip-item`: --bg-secondary box, 16px padding and gap.
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .padding(16.dp),
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                tip,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            }

            SectionCard("Precisa de Mais Ajuda?", icon = Icons.Filled.Email) {
                // `.contact-help`: --bg-secondary box.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Se você ainda tiver dúvidas ou precisar de suporte, entre em contato:",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(
                        onClick = {
                            open(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL")))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Email, contentDescription = null)
                        Text("  $SUPPORT_EMAIL")
                    }
                    Button(
                        onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_WHATSAPP))) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                        Text("  Falar pelo WhatsApp")
                    }
                }
            }
        }
    }
}

/** `**negrito**` → bold span, standing in for the web's `<strong>`. */
private fun rich(text: String): AnnotatedString = buildAnnotatedString {
    text.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}

/** The web's `<details class="faq-item">`: plus/minus circle, question in primary. */
@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background) // --bg-secondary
            .clickable { expanded = !expanded }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (expanded) Icons.Filled.RemoveCircle else Icons.Filled.AddCircle,
                contentDescription = if (expanded) "Recolher" else "Expandir",
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                question,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                rich(answer),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, start = 36.dp),
            )
        }
    }
}
