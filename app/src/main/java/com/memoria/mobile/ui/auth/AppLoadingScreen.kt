package com.memoria.mobile.ui.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.memoria.mobile.ui.common.BrandGradient
import com.memoria.mobile.ui.common.MemoriaLogo
import com.memoria.mobile.ui.common.SystemBarIcons

/**
 * `.app-loading-screen` da web: o degradê azul da marca com a logomarca a
 * flutuar (`loadingFloat`, 1.6s, 6px), enquanto a sessão é restaurada.
 */
@Composable
fun AppLoadingScreen() {
    SystemBarIcons(lightIcons = true)
    val t = rememberInfiniteTransition(label = "loading")
    val lift by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "loadingFloat",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(BrandGradient)
            .semantics { contentDescription = "Carregando aplicativo" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.7f)
                .widthIn(max = 300.dp)
                .graphicsLayer { translationY = -6.dp.toPx() * lift },
            contentAlignment = Alignment.Center,
        ) {
            MemoriaLogo(height = 240.dp)
        }
    }
}
