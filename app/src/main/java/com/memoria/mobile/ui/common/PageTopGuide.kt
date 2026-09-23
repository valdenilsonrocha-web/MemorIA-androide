package com.memoria.mobile.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.memoria.mobile.R

/**
 * A ilustração do topo das páginas, como na versão web.
 *
 * A web monta isto em `app.js` (`imageGuideMap`) e injecta um `#pageTopGuide`
 * no topo de nove páginas; a Mobile não tinha nada equivalente. Esta enum é a
 * mesma tabela, entrada a entrada, para as duas versões mostrarem a mesma
 * ilustração na mesma página.
 *
 * Repare-se que [DOCTORS] serve duas páginas — "Meus Médicos" e "Saúde" —
 * exactamente como na web, onde `myDoctorsPage` e `healthPage` apontam ambas
 * para `guide-top-doctors.png`.
 */
enum class PageGuide(
    @param:DrawableRes val image: Int,
    val description: String,
) {
    MEDICATIONS(R.drawable.guide_top_medications, "Ilustração da página de medicamentos"),
    REPLENISHMENT(R.drawable.guide_top_replenishment, "Ilustração da página de reposição de estoque"),
    CALENDAR(R.drawable.guide_top_calendar, "Ilustração da página de calendário de medicamentos"),
    REPORTS(R.drawable.guide_top_reports, "Ilustração da página de relatórios de tratamento"),
    DOCTORS(R.drawable.guide_top_doctors, "Ilustração da página meus médicos"),
    PRESCRIPTIONS(R.drawable.guide_top_prescriptions, "Ilustração da página minhas receitas"),
    SETTINGS(R.drawable.guide_top_settings, "Ilustração da página de configurações"),
    HELP(R.drawable.guide_help, "Ilustração da página de ajuda"),
}

/**
 * Largura máxima da ilustração.
 *
 * A web usa `min(92vw, var(--top-image-max-mobile))` e `--top-image-max-mobile`
 * é 360px, por isso a imagem nunca enche o ecrã todo — [fillMaxWidth] dá o
 * "92vw" e este limite dá o resto.
 */
private val MAX_WIDTH = 360.dp

/** Raio, borda e sombra vêm de `.page-top-guide-image` no `main.css`. */
private val CORNER = 14.dp
private val BORDER = Color(0x1F0F172A) // rgba(15, 23, 42, 0.12)

@Composable
fun PageTopGuide(guide: PageGuide, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(
            painter = androidx.compose.ui.res.painterResource(guide.image),
            contentDescription = guide.description,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MAX_WIDTH)
                .padding(bottom = 14.dp) // margin-bottom da web
                .shadow(8.dp, RoundedCornerShape(CORNER))
                .clip(RoundedCornerShape(CORNER))
                .border(1.dp, BORDER, RoundedCornerShape(CORNER)),
        )
    }
}

/**
 * The web's text flavour of `#pageTopGuide` (`animatedDescriptions` in
 * `showPageTopGuide()`, used only by Planos): a mint-to-blue card that fades and
 * slides in, stays 3.2s and fades out again. Like the web element, it keeps its
 * place in the layout while hidden (`opacity: 0`).
 */
@Composable
fun PageTopMessage(text: String, modifier: Modifier = Modifier, visibleMillis: Long = 3200) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(text) {
        visible = true
        delay(visibleMillis)
        visible = false
    }
    // `transition: opacity .28s ease, transform .28s ease`; hidden = translateY(-10px).
    val progress by animateFloatAsState(if (visible) 1f else 0f, tween(280), label = "pageTopMessage")
    val shift = with(LocalDensity.current) { 10.dp.toPx() }
    val shape = RoundedCornerShape(CORNER)
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp,
        color = Color(0xFF0F172A),
        modifier = modifier
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                translationY = -shift * (1f - progress)
            }
            .shadow(10.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFECFDF5), Color(0xFFEFF6FF))))
            .border(1.dp, BORDER, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}