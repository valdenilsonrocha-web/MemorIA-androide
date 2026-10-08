package com.memoria.mobile.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memoria.mobile.ui.common.PlanLimits
import java.time.LocalDate

/** One entry of the web's `showDailyUpsellMessage()` rotation. */
data class UpsellMessage(val icon: String, val title: String, val desc: String, val features: List<String>)

/**
 * One message per [com.memoria.mobile.ui.plans.PREMIUM_FEATURES] entry, same order, titled with the same
 * text: the card may only sell what Premium really unlocks. The web's 14-message
 * rotation (`showDailyUpsellMessage()`) also promised removal of ads, family
 * profiles, backup across devices and other things the app does not have.
 */
val DAILY_UPSELL_MESSAGES: List<UpsellMessage> = listOf(
    UpsellMessage(
        "💊", "Cadastro ilimitado de medicações",
        "No plano gratuito você pode cadastrar até ${PlanLimits.FREE_MEDICATIONS} medicamentos. Com o Premium, cadastre quantos precisar.",
        listOf("Sem limite de medicamentos", "Plano gratuito: ${PlanLimits.FREE_MEDICATIONS} medicamentos"),
    ),
    UpsellMessage(
        "📊", "Relatórios avançados",
        "Veja o gráfico de adesão dos últimos 30 dias, suas sequências de dias seguidos e a tendência semanal. Exporte ou compartilhe o relatório para levar ao seu médico.",
        listOf("Adesão em 30 dias", "Sequências de dias", "Exportar e compartilhar"),
    ),
    UpsellMessage(
        "👨‍👩‍👧", "Múltiplos contatos de emergência",
        "Cadastre até ${PlanLimits.PREMIUM_CONTACTS} contatos de emergência no seu perfil. No plano gratuito é permitido apenas ${PlanLimits.FREE_CONTACTS} contato.",
        listOf("Até ${PlanLimits.PREMIUM_CONTACTS} contatos", "Plano gratuito: ${PlanLimits.FREE_CONTACTS} contato"),
    ),
    UpsellMessage(
        "🔔", "Alertas para contatos via WhatsApp e e-mail",
        "Seus contatos podem receber alertas pelo WhatsApp e por e-mail. Pelo WhatsApp, eles são avisados quando uma dose não é confirmada a tempo.",
        listOf("Alertas no WhatsApp", "Alertas por e-mail"),
    ),
    UpsellMessage(
        "📋", "Armazenamento de receitas por foto no histórico",
        "Fotografe suas receitas médicas e guarde-as no histórico do app para consultar quando precisar.",
        listOf("Foto das receitas", "Guardadas no histórico"),
    ),
    UpsellMessage(
        "☁️", "Sincronização em nuvem das receitas",
        "As receitas que você salva ficam guardadas na nuvem do MemorIA, junto com a sua conta.",
        listOf("Receitas na nuvem", "Junto com a sua conta"),
    ),
    UpsellMessage(
        "🎙️", "Integração com Alexa e assistente de voz nas Configurações",
        "Ative nas Configurações a integração com a Alexa, o Google Assistant ou um webhook e receba o lembrete de cada dose também pelo assistente de voz.",
        listOf("Alexa", "Google Assistant", "Lembrete por voz"),
    ),
)

/** Today's message: `dayOfYear % messages.length`, as the web. */
fun todaysUpsell(today: LocalDate = LocalDate.now()): UpsellMessage =
    DAILY_UPSELL_MESSAGES[today.dayOfYear % DAILY_UPSELL_MESSAGES.size]

/** `#dailyUpsellCard` — free users only, dismissible for the day. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyUpsellCard(onDismiss: () -> Unit, onSubscribe: () -> Unit, modifier: Modifier = Modifier) {
    val msg = todaysUpsell()
    // `.daily-upsell-card`: light-blue gradient, 1.5px #9ac6ff border, radius 16.
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, Color(0xFF9AC6FF)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFFEFF6FF), Color(0xFFDBEAFE)))),
        ) {
            Row(
                Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(msg.icon, fontSize = 30.sp, modifier = Modifier.padding(top = 2.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "⭐ Recurso Premium".uppercase(),
                        fontSize = 10.sp,
                        letterSpacing = 0.7.sp,
                        color = Color(0xFF0053D6),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp, end = 28.dp),
                    )
                    Text(
                        msg.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF003788),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    Text(
                        msg.desc,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFF003B9A),
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 14.dp),
                    ) {
                        msg.features.forEach { f ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xD9DBEAFE),
                                border = BorderStroke(1.dp, Color(0xFF9AC6FF)),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF0053D6),
                                        modifier = Modifier.size(10.dp),
                                    )
                                    Text(
                                        " $f",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF0053D6),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    // `.daily-upsell-card-cta`: blue gradient button with the crown.
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF2F7DFF), Color(0xFF0053D6))))
                            .clickable(onClick = onSubscribe)
                            .padding(horizontal = 20.dp, vertical = 9.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.WorkspacePremium,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                " Assinar agora",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Fechar sugestão",
                    tint = Color(0xFF9AC6FF),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** `showAchievements()`: trophy (30+ days), medal (7+) or the "start" card. */
@Composable
fun AchievementCard(days: Int, modifier: Modifier = Modifier) {
    val cfg = when {
        days >= 30 -> Cfg(
            Color(0xFFFFFBE6), Color(0xFFFFE066), Icons.Filled.EmojiEvents, Color(0xFFF7B801),
            "Parabéns!",
            " Você conquistou o troféu de regularidade por tomar seus remédios $days dias seguidos! 🏆",
            "Continue assim para manter sua saúde em dia!",
        )
        days >= 7 -> Cfg(
            Color(0xFFF0F7FF), Color(0xFFA5D8FF), Icons.Filled.MilitaryTech, Color(0xFF339AF0),
            "Ótimo trabalho!",
            " Você ganhou uma medalha por tomar seus remédios $days dias seguidos! 🥇",
            "Continue para conquistar o troféu!",
        )
        else -> Cfg(
            Color(0xFFF8F9FA), Color(0xFFDEE2E6), Icons.Filled.Star, Color(0xFFFFD43B),
            "Comece sua jornada!",
            " Tome seus remédios para manter a saúde em dia.",
            "Acompanhe seu progresso aqui.",
        )
    }
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cfg.bg),
        border = BorderStroke(2.dp, cfg.border),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(cfg.icon, contentDescription = null, tint = cfg.iconColor, modifier = Modifier.size(36.dp))
            Column {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(cfg.title) }
                        append(cfg.body)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF1F2937),
                )
                Text(cfg.hint, fontSize = 13.sp, color = Color(0xFF888888))
            }
        }
    }
}

private data class Cfg(
    val bg: Color,
    val border: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconColor: Color,
    val title: String,
    val body: String,
    val hint: String,
)
