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
import java.time.LocalDate

/** One entry of the web's `showDailyUpsellMessage()` rotation. */
data class UpsellMessage(val icon: String, val title: String, val desc: String, val features: List<String>)

/** Same 14 messages, same order, as `showDailyUpsellMessage()` in app.js. */
val DAILY_UPSELL_MESSAGES: List<UpsellMessage> = listOf(
    UpsellMessage(
        "💊", "Medicamentos ilimitados para toda a família",
        "No plano gratuito você pode cadastrar até 2 medicamentos. Com o Premium, cadastre quantos precisar — para você e para toda a família, sem limites.",
        listOf("Sem limite de medicamentos", "Perfis familiares", "Lembretes para todos"),
    ),
    UpsellMessage(
        "🔔", "Alertas para cuidadores via WhatsApp",
        "Receba uma mensagem no WhatsApp sempre que um medicamento não for tomado no horário. Ideal para acompanhar idosos e familiares à distância.",
        listOf("Notificação no WhatsApp", "Múltiplos cuidadores", "Alertas em tempo real"),
    ),
    UpsellMessage(
        "📊", "Histórico e relatórios de adesão ao tratamento",
        "Acompanhe com gráficos detalhados quantos dias seguidos você tomou seus remédios corretamente. Compartilhe o relatório com seu médico.",
        listOf("Gráfico de adesão", "Sequência de dias", "Exportar para médico"),
    ),
    UpsellMessage(
        "📋", "Salve e organize suas receitas médicas",
        "Fotografe suas receitas e guarde tudo organizado no app. Acesse quando precisar, mesmo sem internet, e nunca volte a perder uma receita.",
        listOf("Foto das receitas", "Organizado por médico", "Acesso offline"),
    ),
    UpsellMessage(
        "☁️", "Backup automático e sincronização na nuvem",
        "Seus medicamentos e histórico ficam salvos na nuvem e sincronizados em todos os seus dispositivos. Nunca perca suas informações.",
        listOf("Backup automático", "Multi-dispositivo", "Restauração fácil"),
    ),
    UpsellMessage(
        "🎙️", "Assistente de voz — integração com a Alexa",
        "Pergunte para a Alexa quais remédios você precisa tomar hoje. Perfeito para quem tem dificuldade de olhar para a tela com frequência.",
        listOf("Integração Alexa", "Comando por voz", "Lembrete falado"),
    ),
    UpsellMessage(
        "🆘", "Modo alerta de emergência",
        "Em caso de emergência, compartilhe instantaneamente sua lista de medicamentos atuais com socorristas ou médicos com um único toque.",
        listOf("Compartilhamento rápido", "Dados atualizados", "Toque único"),
    ),
    UpsellMessage(
        "👨‍👩‍👧", "Até 3 contatos de emergência",
        "Cadastre até 3 contatos que serão notificados automaticamente em caso de emergência. No plano gratuito é permitido apenas 1 contato.",
        listOf("Até 3 contatos", "Notificação automática", "Plano gratuito: 1 contato"),
    ),
    UpsellMessage(
        "📅", "Agenda de consultas e exames",
        "Além dos medicamentos, registre suas consultas médicas e exames com lembretes automáticos. Nunca mais esqueça um compromisso de saúde.",
        listOf("Agenda de consultas", "Lembretes de exames", "Histórico completo"),
    ),
    UpsellMessage(
        "❤️", "Gestão completa da saúde familiar",
        "Registre pressão arterial, glicemia, peso e muito mais, tudo em um único app para toda a família. Saúde em dia, na palma da mão.",
        listOf("Pressão arterial", "Glicemia", "Peso e IMC", "Toda a família"),
    ),
    UpsellMessage(
        "🏥", "Acompanhamento de doenças crônicas",
        "Gerencie tratamentos contínuos de diabetes, hipertensão, colesterol e outras condições com relatórios específicos para cada condição.",
        listOf("Diabetes", "Hipertensão", "Colesterol", "Relatórios específicos"),
    ),
    UpsellMessage(
        "🔒", "Privacidade total — sem anúncios",
        "Assine o Premium e remova todos os anúncios do app. Uma experiência limpa e focada, sem distrações na hora de cuidar da sua saúde.",
        listOf("Sem anúncios", "Experiência limpa", "Dados protegidos"),
    ),
    UpsellMessage(
        "💬", "Notificações personalizadas por medicamento",
        "Configure mensagens e sons personalizados para cada medicamento. Nunca mais confunda um lembrete com o outro.",
        listOf("Mensagem personalizada", "Som específico", "Por medicamento"),
    ),
    UpsellMessage(
        "🧬", "Histórico médico completo",
        "Registre alergias, condições de saúde, tipo sanguíneo e histórico de internações. Tenha tudo pronto para uma emergência ou nova consulta.",
        listOf("Alergias registradas", "Condições de saúde", "Tipo sanguíneo"),
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
