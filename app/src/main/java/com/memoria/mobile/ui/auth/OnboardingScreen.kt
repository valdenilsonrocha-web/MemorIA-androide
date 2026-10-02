package com.memoria.mobile.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.R
import com.memoria.mobile.ui.common.SystemBarIcons
import com.memoria.mobile.ui.common.systemViewModel

// Paleta do `.ob-overlay` no main.css.
private val ObBgTop = Color(0xFF0F172A)
private val ObBgMid = Color(0xFF1E293B)
private val ObBgBottom = Color(0xFF0C1F3F)
private val ObAccent = Color(0xFF66A9FF)
private val ObBlue = Color(0xFF2F7DFF)
private val ObBlueDeep = Color(0xFF0053D6)
private val ObGreen = Color(0xFF34D399)
private val White = Color.White

/**
 * A configuração inicial da web (`#onboardingOverlay` no index.html): cinco
 * passos — boas-vindas com a logomarca animada, nome, ano de nascimento, tipo de
 * alerta e som — com os pontos de progresso e os botões Voltar / Continuar.
 *
 * Aparece uma vez, depois do primeiro login neste aparelho, como na web aparece
 * uma vez por navegador.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val vm = systemViewModel { repo, app, scheduler -> OnboardingViewModel(repo, app, scheduler) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SystemBarIcons(lightIcons = true)

    LaunchedEffect(state.toast) {
        state.toast?.let {
            com.memoria.mobile.ui.common.MemoriaToast.show(it)
            vm.consumeToast()
        }
    }
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    BackHandler(enabled = state.step > 0) { vm.back() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(0f to ObBgTop, 0.45f to ObBgMid, 1f to ObBgBottom)),
    ) {
        BackgroundBlobs()
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
        ) {
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(420)) { w -> if (forward) w / 6 else -w / 6 } + fadeIn(tween(420)))
                        .togetherWith(
                            slideOutHorizontally(tween(320)) { w -> if (forward) -w / 6 else w / 6 } + fadeOut(tween(320)),
                        )
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "onboardingStep",
            ) { step ->
                when (step) {
                    0 -> WelcomeStep()
                    1 -> InputStep(
                        illustration = { NameIllustration() },
                        title = "Como podemos te chamar?",
                        subtitle = "Personalizamos seus lembretes com o seu nome.",
                        value = state.name,
                        onValue = vm::onName,
                        placeholder = "Digite seu nome",
                        keyboardType = KeyboardType.Text,
                    )
                    2 -> InputStep(
                        illustration = { BirthIllustration() },
                        title = "Qual seu ano de nascimento?",
                        subtitle = "Ajustamos os alertas de saúde para seu perfil de idade.",
                        value = state.birthYear,
                        onValue = vm::onBirthYear,
                        placeholder = "Ex: 1978",
                        keyboardType = KeyboardType.Number,
                    )
                    3 -> AlertTypeStep(state.alertType, vm::onAlertType)
                    else -> SoundStep(state.sound, vm::onSound)
                }
            }

            Dots(current = state.step, total = OnboardingViewModel.LAST_STEP + 1)

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.step > 0) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, White.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClick = vm::back)
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.ChevronLeft, null, tint = White.copy(alpha = 0.78f), modifier = Modifier.size(20.dp))
                        Text("Voltar", color = White.copy(alpha = 0.78f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                val last = state.step == OnboardingViewModel.LAST_STEP
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(ObBlue, ObBlueDeep)))
                        .clickable(enabled = !state.saving, role = Role.Button, onClick = vm::next)
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (last) "Finalizar" else "Continuar",
                        color = White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        if (last) Icons.Filled.Check else Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────── Fundo ───────────────────────────────

/** `.ob-blob-1..3`: manchas desfocadas que derivam devagar. */
@Composable
private fun BackgroundBlobs() {
    val t = rememberInfiniteTransition(label = "blobs")
    val drift by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift",
    )
    Canvas(Modifier.fillMaxSize()) {
        fun blob(center: Offset, radius: Float, color: Color) {
            drawCircle(
                brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
                radius = radius,
                center = center,
            )
        }
        val d = 30.dp.toPx() * drift
        blob(Offset(80.dp.toPx() + d, 60.dp.toPx() + d / 2), 220.dp.toPx(), Color(0x2E6366F1))
        blob(Offset(size.width - 70.dp.toPx() - d, size.height - 60.dp.toPx() - d), 190.dp.toPx(), Color(0x240EA5E9))
        blob(Offset(size.width * 0.66f + d / 2, size.height * 0.45f - d), 150.dp.toPx(), Color(0x1AEC4899))
    }
}

@Composable
private fun Dots(current: Int, total: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        repeat(total) { i ->
            val width by animateDpAsState(if (i == current) 24.dp else 8.dp, tween(300), label = "dot$i")
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(RoundedCornerShape(4.dp))
                    .background(White.copy(alpha = if (i == current) 0.9f else 0.28f)),
            )
        }
    }
}

// ─────────────────────────── Passo 0 — boas-vindas ───────────────────────────

@Composable
private fun WelcomeStep() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            WelcomeScene()
        }
        Column(
            Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                buildAnnotatedString {
                    append("Bem-vindo ao ")
                    withStyle(SpanStyle(color = ObAccent)) { append("MemórIA") }
                },
                color = White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 34.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                buildAnnotatedString {
                    append("Seu assistente pessoal para fortalecer sua ")
                    withStyle(SpanStyle(color = White, fontWeight = FontWeight.ExtraBold)) { append("MemórIA") }
                    append(" e nunca mais esquecer seus medicamentos.")
                },
                color = White.copy(alpha = 0.68f),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 340.dp),
            )
            Spacer(Modifier.height(14.dp))
            FeatureChips()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeatureChips() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeatureChip(Icons.Filled.Notifications, "Lembretes inteligentes")
        FeatureChip(Icons.Filled.Medication, "Controle completo")
        FeatureChip(Icons.Filled.Shield, "Saúde protegida")
    }
}

@Composable
private fun FeatureChip(icon: ImageVector, label: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(White.copy(alpha = 0.1f))
            .border(1.dp, White.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = ObAccent, modifier = Modifier.size(14.dp))
        Text(label, color = White.copy(alpha = 0.85f), fontSize = 14.sp)
    }
}

/**
 * `.ob-welcome-scene`: um painel de vidro com um brilho azul, e dentro dele o
 * "palco" da logomarca que entra a flutuar (`obLogoFloat`), fica a derivar
 * (`obLogoDrift`) e é varrido por um reflexo (`obLogoShine`).
 */
@Composable
private fun WelcomeScene() {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(1050, easing = FastOutSlowInEasing)) }

    val t = rememberInfiniteTransition(label = "welcome")
    val drift by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift",
    )
    val glow by t.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val shine by t.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(6500, delayMillis = 1100, easing = LinearEasing), RepeatMode.Restart),
        label = "shine",
    )

    BoxWithConstraints(
        Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth(0.9f)
            .aspectRatio(320f / 240f)
            .graphicsLayer {
                alpha = enter.value.coerceIn(0f, 1f)
                val s = 0.97f + 0.03f * enter.value
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(White.copy(alpha = 0.11f), White.copy(alpha = 0.03f))))
            .border(1.dp, Color(0x59BFDBFE), RoundedCornerShape(30.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to ObBlue.copy(alpha = 0.42f * glow),
                    0.4f to ObAccent.copy(alpha = 0.2f * glow),
                    0.72f to Color.Transparent,
                    center = Offset(size.width / 2, size.height * 0.45f),
                    radius = size.maxDimension * 0.7f,
                ),
                radius = size.maxDimension,
            )
        }
        val stageHeight: Dp = maxHeight * 0.86f
        Box(
            Modifier
                .height(stageHeight)
                .aspectRatio(1f)
                .graphicsLayer {
                    val e = enter.value
                    translationY = (1f - e) * 14.dp.toPx() - drift * 4.dp.toPx()
                    val s = (0.94f + 0.06f * e) * (1f + 0.008f * drift)
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(White.copy(alpha = 0.24f), White.copy(alpha = 0.09f))))
                .border(1.dp, White.copy(alpha = 0.3f), RoundedCornerShape(26.dp))
                .padding(8.dp)
                .drawWithContent {
                    drawContent()
                    // O reflexo inclinado que atravessa a logomarca.
                    val w = size.width * 0.26f
                    val x = size.width * shine
                    val a = when {
                        shine < -0.24f -> 0f
                        shine > 1.2f -> 0f
                        else -> 0.42f
                    }
                    if (a > 0f) {
                        val path = Path().apply {
                            val skew = size.height * 0.33f
                            moveTo(x + skew, -size.height * 0.2f)
                            lineTo(x + skew + w, -size.height * 0.2f)
                            lineTo(x + w - skew, size.height * 1.2f)
                            lineTo(x - skew, size.height * 1.2f)
                            close()
                        }
                        drawPath(
                            path,
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, White.copy(alpha = a), Color.Transparent),
                                startX = x - skewPad(size.height),
                                endX = x + w + skewPad(size.height),
                            ),
                        )
                    }
                },
        ) {
            Image(
                painter = painterResource(R.drawable.logo_memoria),
                contentDescription = "MemórIA",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)),
            )
            // `.ob-ws-logo-stage-light`: luz vinda do canto superior esquerdo.
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.radialGradient(
                        listOf(White.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.14f),
                        radius = size.maxDimension * 0.46f,
                    ),
                )
            }
        }
    }
}

private fun skewPad(height: Float) = height * 0.33f

// ─────────────────────────── Passos 1 e 2 — campos ───────────────────────────

@Composable
private fun InputStep(
    illustration: @Composable () -> Unit,
    title: String,
    subtitle: String,
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            Modifier.fillMaxWidth().height(240.dp).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) { illustration() }
        Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
            StepTitle(title)
            StepSubtitle(subtitle)
            OutlinedTextField(
                value = value,
                onValueChange = onValue,
                placeholder = { Text(placeholder, fontSize = 18.sp) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, color = White),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = White,
                    unfocusedTextColor = White,
                    cursorColor = White,
                    focusedBorderColor = ObBlue.copy(alpha = 0.75f),
                    unfocusedBorderColor = White.copy(alpha = 0.2f),
                    focusedContainerColor = White.copy(alpha = 0.16f),
                    unfocusedContainerColor = White.copy(alpha = 0.1f),
                    focusedPlaceholderColor = White.copy(alpha = 0.42f),
                    unfocusedPlaceholderColor = White.copy(alpha = 0.42f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text,
        color = White,
        fontSize = 26.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 32.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun StepSubtitle(text: String) {
    Text(
        text,
        color = White.copy(alpha = 0.68f),
        fontSize = 16.sp,
        lineHeight = 24.sp,
        modifier = Modifier.padding(bottom = 14.dp),
    )
}

/** `.ob-name-svg`: busto estilizado com um aceno e brilhos. */
@Composable
private fun NameIllustration() {
    val sparkle = rememberInfiniteTransition(label = "sparkle").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "sparkleAlpha",
    )
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 220f
            drawCircle(White.copy(alpha = 0.18f), 44 * u, Offset(110 * u, 78 * u))
            drawCircle(White.copy(alpha = 0.55f), 44 * u, Offset(110 * u, 78 * u), style = Stroke(3 * u))
            drawCircle(White.copy(alpha = 0.28f), 22 * u, Offset(110 * u, 68 * u))
            drawCircle(White.copy(alpha = 0.6f), 22 * u, Offset(110 * u, 68 * u), style = Stroke(2.5f * u))
            val body = Path().apply {
                moveTo(52 * u, 170 * u)
                quadraticTo(52 * u, 128 * u, 110 * u, 122 * u)
                quadraticTo(168 * u, 128 * u, 168 * u, 170 * u)
            }
            drawPath(body, White.copy(alpha = 0.18f))
            drawPath(body, White.copy(alpha = 0.55f), style = Stroke(2.5f * u))
            val a = sparkle.value
            drawCircle(White.copy(alpha = 0.6f * a), 5 * u, Offset(40 * u, 55 * u))
            drawCircle(White.copy(alpha = 0.5f * (1.35f - a)), 6 * u, Offset(182 * u, 48 * u))
            drawCircle(White.copy(alpha = 0.6f * a), 4 * u, Offset(170 * u, 145 * u))
            drawCircle(White.copy(alpha = 0.45f * (1.35f - a)), 5 * u, Offset(45 * u, 148 * u))
        }
        Text("👋", fontSize = 36.sp, modifier = Modifier.offset(x = 52.dp, y = 0.dp))
    }
}

/** `.ob-birth-svg`: calendário com o dia a pulsar e um bolo. */
@Composable
private fun BirthIllustration() {
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.8f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulseScale",
    )
    Box(Modifier.size(200.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 220f
            drawRoundRect(White.copy(alpha = 0.15f), Offset(30 * u, 58 * u), Size(160 * u, 130 * u), CornerRadius(16 * u))
            drawRoundRect(White.copy(alpha = 0.5f), Offset(30 * u, 58 * u), Size(160 * u, 130 * u), CornerRadius(16 * u), style = Stroke(2.5f * u))
            drawRoundRect(White.copy(alpha = 0.28f), Offset(30 * u, 58 * u), Size(160 * u, 44 * u), CornerRadius(16 * u))
            drawRect(White.copy(alpha = 0.28f), Offset(30 * u, 82 * u), Size(160 * u, 20 * u))
            drawRoundRect(White.copy(alpha = 0.85f), Offset(78 * u, 44 * u), Size(14 * u, 26 * u), CornerRadius(6 * u))
            drawRoundRect(White.copy(alpha = 0.85f), Offset(128 * u, 44 * u), Size(14 * u, 26 * u), CornerRadius(6 * u))
            listOf(62f, 90f, 118f).forEach { x ->
                drawCircle(White.copy(alpha = 0.45f), 8 * u, Offset(x * u, 128 * u))
                drawCircle(White.copy(alpha = 0.45f), 8 * u, Offset(x * u, 158 * u))
            }
            drawCircle(White.copy(alpha = 0.15f), 14 * u * pulse.value, Offset(146 * u, 128 * u))
            drawCircle(White.copy(alpha = 0.85f), 8 * u, Offset(146 * u, 128 * u))
        }
        Text(
            "Seu ano",
            color = White.copy(alpha = 0.95f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 60.dp),
        )
        Text("🎂", fontSize = 24.sp, modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 22.dp))
    }
}

// ─────────────────────────── Passo 3 — tipo de alerta ───────────────────────────

@Composable
private fun AlertTypeStep(selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp)) {
            StepTitle("Como prefere ser lembrado?")
            StepSubtitle("Veja como cada tipo de alerta aparece no celular:")
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            AlertCard(
                selected = selected == "normal",
                label = "Notificação",
                description = "Banner discreto no topo",
                onClick = { onSelect("normal") },
                modifier = Modifier.weight(1f),
            ) { NormalPhone() }
            AlertCard(
                selected = selected == "fullscreen",
                label = "Tela inteira",
                description = "Alerta que não passa despercebido",
                recommended = true,
                onClick = { onSelect("fullscreen") },
                modifier = Modifier.weight(1f),
            ) { FullscreenPhone() }
        }
    }
}

@Composable
private fun AlertCard(
    selected: Boolean,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    recommended: Boolean = false,
    phone: @Composable () -> Unit,
) {
    Box(modifier.widthIn(max = 170.dp).padding(top = 10.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) ObBlue.copy(alpha = 0.12f) else White.copy(alpha = 0.06f))
                .border(2.5.dp, if (selected) ObAccent else White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                .clickable(role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            phone()
            Text(label, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                description,
                color = White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )
            if (selected) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Selecionado", tint = ObAccent, modifier = Modifier.size(20.dp))
            }
        }
        if (recommended && selected) {
            Text(
                "★ Recomendado",
                color = White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-10).dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ObAccent)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PhoneFrame(screen: Color, content: @Composable () -> Unit) {
    Box(
        Modifier
            .width(92.dp)
            .height(162.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E293B))
            .border(2.5.dp, White.copy(alpha = 0.22f), RoundedCornerShape(16.dp)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = 7.dp)
                .background(screen),
        ) { content() }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .width(28.dp)
                .height(7.dp)
                .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                .background(White.copy(alpha = 0.18f)),
        )
    }
}

@Composable
private fun NormalPhone() {
    PhoneFrame(screen = ObBgTop) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(0.75f, 0.55f, 0.4f, 0.6f, 0.35f).forEach { w ->
                Box(Modifier.fillMaxWidth(w).height(6.dp).clip(RoundedCornerShape(3.dp)).background(White.copy(alpha = 0.09f)))
            }
        }
        Row(
            Modifier
                .padding(horizontal = 4.dp, vertical = 5.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xF71E293B))
                .border(1.dp, ObBlue.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF0060F8)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Medication, null, tint = White, modifier = Modifier.size(11.dp)) }
            Column {
                Text("MemorIA", color = White.copy(alpha = 0.9f), fontSize = 7.sp, fontWeight = FontWeight.Bold, lineHeight = 8.sp)
                Text("Losartana 50mg", color = White.copy(alpha = 0.58f), fontSize = 6.sp, lineHeight = 7.sp)
            }
        }
    }
}

@Composable
private fun FullscreenPhone() {
    PhoneFrame(screen = Color(0xFF003B9A)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFF003788), ObBlueDeep)))
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            Icon(Icons.Filled.Medication, null, tint = White, modifier = Modifier.size(24.dp))
            Text("Losartana 50mg", color = White.copy(alpha = 0.92f), fontSize = 7.sp, fontWeight = FontWeight.Bold, lineHeight = 8.sp)
            Text("21:00", color = White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 19.sp)
            Text(
                "Tomei!",
                color = Color(0xFF003788),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 8.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(White.copy(alpha = 0.88f)).padding(vertical = 3.dp),
            )
            Text(
                "Soneca",
                color = White,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 8.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(White.copy(alpha = 0.2f))
                    .border(1.dp, White.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(vertical = 3.dp),
            )
        }
    }
}

// ─────────────────────────── Passo 4 — som ───────────────────────────

@Composable
private fun SoundStep(selected: OnboardingSound, onSelect: (OnboardingSound) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(220.dp).padding(16.dp), contentAlignment = Alignment.Center) {
            SoundIllustration()
        }
        Column(Modifier.padding(horizontal = 22.dp, vertical = 16.dp)) {
            StepTitle("Som do lembrete")
            StepSubtitle("Escolha um som fácil de ouvir no seu ambiente.")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OnboardingSound.entries.forEach { sound ->
                    SoundOption(sound, sound == selected) { onSelect(sound) }
                }
            }
        }
    }
}

@Composable
private fun SoundOption(sound: OnboardingSound, active: Boolean, onClick: () -> Unit) {
    val icon = when (sound) {
        OnboardingSound.DEFAULT -> Icons.AutoMirrored.Filled.VolumeUp
        OnboardingSound.GENTLE -> Icons.AutoMirrored.Filled.VolumeDown
        OnboardingSound.LOUD -> Icons.Filled.NotificationsActive
    }
    val bars = when (sound) {
        OnboardingSound.GENTLE -> listOf(4, 7, 5, 8, 4)
        OnboardingSound.DEFAULT -> listOf(8, 12, 9, 14, 8)
        OnboardingSound.LOUD -> listOf(10, 16, 12, 18, 10)
    }
    val accent = if (active) ObGreen else White.copy(alpha = 0.65f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) ObGreen.copy(alpha = 0.12f) else White.copy(alpha = 0.07f))
            .border(2.dp, if (active) ObGreen else White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Text(sound.label, color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        SoundBars(bars, if (active) ObGreen else White.copy(alpha = 0.3f))
    }
}

@Composable
private fun SoundBars(heights: List<Int>, color: Color) {
    val t = rememberInfiniteTransition(label = "bars")
    val wave by t.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "wave",
    )
    Row(
        Modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.forEachIndexed { i, h ->
            val factor = if (i % 2 == 0) wave else 1.6f - wave
            Box(
                Modifier
                    .width(4.dp)
                    .height((h * factor.coerceIn(0.5f, 1f)).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

/** `.ob-sound-svg`: alto-falante com ondas que pulsam e notas a flutuar. */
@Composable
private fun SoundIllustration() {
    val t = rememberInfiniteTransition(label = "sound")
    val wave by t.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "soundWave",
    )
    val float by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "noteFloat",
    )
    Box(Modifier.size(190.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 220f
            val back = Path().apply {
                moveTo(88 * u, 72 * u); lineTo(88 * u, 148 * u); lineTo(66 * u, 136 * u); lineTo(66 * u, 84 * u); close()
            }
            val cone = Path().apply {
                moveTo(88 * u, 72 * u); lineTo(124 * u, 52 * u); lineTo(124 * u, 168 * u); lineTo(88 * u, 148 * u); close()
            }
            drawPath(back, White.copy(alpha = 0.22f))
            drawPath(back, White.copy(alpha = 0.65f), style = Stroke(2.5f * u))
            drawPath(cone, White.copy(alpha = 0.15f))
            drawPath(cone, White.copy(alpha = 0.65f), style = Stroke(2.5f * u))
            fun arc(x0: Float, xc: Float, y0: Float, y1: Float, alpha: Float, w: Float) {
                val p = Path().apply {
                    moveTo(x0 * u, y0 * u)
                    quadraticTo(xc * u, 110 * u, x0 * u, y1 * u)
                }
                drawPath(p, White.copy(alpha = alpha), style = Stroke(w * u, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            arc(132f, 150f, 90f, 130f, 0.8f * wave, 3.5f)
            arc(142f, 168f, 76f, 144f, 0.55f * (1.4f - wave), 3f)
            arc(152f, 186f, 62f, 158f, 0.3f * wave, 2.5f)
        }
        Text("♪", color = White.copy(alpha = 0.75f), fontSize = 22.sp, modifier = Modifier.offset(x = 24.dp, y = (44 - 6 * float).dp))
        Text("♫", color = White.copy(alpha = 0.6f), fontSize = 16.sp, modifier = Modifier.offset(x = 134.dp, y = (34 + 6 * float).dp))
        Text("♪", color = White.copy(alpha = 0.7f), fontSize = 20.sp, modifier = Modifier.offset(x = 130.dp, y = (140 - 6 * float).dp))
        Text("♫", color = White.copy(alpha = 0.5f), fontSize = 14.sp, modifier = Modifier.offset(x = 30.dp, y = (130 + 6 * float).dp))
    }
}
