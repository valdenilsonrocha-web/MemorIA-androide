package com.memoria.mobile.ui.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.memoria.mobile.R
import java.io.File
import com.memoria.mobile.ui.theme.BluePrimary
import com.memoria.mobile.ui.theme.BrandBlueBright
import com.memoria.mobile.ui.theme.BrandBlueDeep
import com.memoria.mobile.ui.theme.LocalThemePreset
import com.memoria.mobile.ui.theme.RedMiss
import androidx.compose.ui.text.style.TextAlign

/**
 * O degradê da marca — `linear-gradient(160deg, #0053d6 0%, #0060f8 52%, #2f7dff 100%)`
 * do `.app-loading-screen` no `main.css`.
 */
val BrandGradient: Brush
    get() = Brush.linearGradient(
        0f to BrandBlueDeep,
        0.52f to BluePrimary,
        1f to BrandBlueBright,
    )

/**
 * Picks the status-bar icon colour for whatever is drawn under it. The app is
 * edge-to-edge, so the blue header, the loading screen and the onboarding need
 * light icons, and the white login form needs dark ones.
 */
@Composable
fun SystemBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val activity = view.context.findActivity() ?: return@SideEffect
        WindowCompat.getInsetsController(activity.window, view)
            .isAppearanceLightStatusBars = !lightIcons
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * A logomarca MemórIA (`logo-memoria.png`): a cápsula com o nome por baixo,
 * sobre o azul da marca.
 */
@Composable
fun MemoriaLogo(modifier: Modifier = Modifier, height: Dp = 54.dp, complete: Boolean = false) {
    Image(
        // `logo-memoria-completa.png`: ícone + nome em fundo transparente — a do cabeçalho.
        painter = androidx.compose.ui.res.painterResource(
            if (complete) R.drawable.logo_memoria_completa else R.drawable.logo_memoria,
        ),
        contentDescription = "MemórIA",
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height),
    )
}

/**
 * O cabeçalho azul da web (`<header class="header">` no `index.html`), que fica
 * por cima de TODAS as páginas: avatar do perfil à esquerda, a logomarca ao
 * centro e, à direita, o sino das pendências e o botão de partilhar.
 *
 * Os tamanhos são os do `@media (max-width: 480px)` do `main.css` — é essa a
 * regra que vale num telemóvel: logo de 96px (cabeçalho de 104px), avatar de 50px, botões de 44px.
 *
 * [pendingCount] é o `getPendingMedicationCount()` da web (doses de hoje por
 * tomar + medicamentos com estoque baixo); acima de zero o sino ganha o badge
 * vermelho, como no `.header-alert-badge`.
 */
@Composable
fun MemoriaHeader(
    pendingCount: Int,
    onProfile: () -> Unit,
    onPendingAlerts: () -> Unit,
    modifier: Modifier = Modifier,
    shareUrl: String? = null,
    /**
     * A foto do perfil (`profile_photo_path`), como o `#headerUserPhoto` da web
     * (`syncProfileAvatar`): com foto, o círculo mostra-a; sem foto, o ícone.
     */
    photoPath: String? = null,
) {
    val preset = LocalThemePreset.current
    // `.header { background: var(--primary-color) }` — a cor crua da paleta.
    val background = preset.primary
    SystemBarIcons(lightIcons = true)

    Box(
        modifier
            .fillMaxWidth()
            // `box-shadow: var(--shadow-md)`
            .shadow(4.dp)
            .background(background)
            .windowInsetsPadding(WindowInsets.statusBars)
            // `.header { padding: var(--spacing-md) var(--spacing-lg) }` = 20px 32px.
            .padding(horizontal = 32.dp, vertical = 20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 104.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeaderAvatar(photoPath = photoPath, onClick = onProfile)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                MemoriaLogo(Modifier.widthIn(max = 230.dp), height = 96.dp, complete = true)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PendingBell(pendingCount, onPendingAlerts)
                ShareButton(shareUrl)
            }
        }
    }
}

/**
 * `#profileQuickAccessBtn` + `.header-user-avatar`: 50px, a foto a preencher o
 * círculo quando existe (`object-fit: cover`), senão o `fa-user` branco.
 */
@Composable
private fun HeaderAvatar(photoPath: String?, onClick: () -> Unit) {
    // A chave inclui a data do ficheiro: o perfil regrava a foto no mesmo caminho.
    val file = photoPath?.let { File(it) }?.takeIf { it.exists() }
    val bitmap = remember(file?.path, file?.lastModified()) {
        file?.let { decodeAvatar(it.path) }
    }
    if (bitmap == null) {
        HeaderCircleButton(
            icon = Icons.Filled.Person,
            description = "Abrir cadastro do usuário",
            size = 50.dp,
            iconSize = 26.dp,
            onClick = onClick,
        )
        return
    }
    Box(
        Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Abrir cadastro do usuário" },
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().clip(CircleShape),
        )
    }
}

/** Decodes a small copy — the avatar is 50dp, a full camera photo is not needed. */
private fun decodeAvatar(path: String): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 256) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

@Composable
private fun HeaderCircleButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    // `.btn-icon`: rgba(255,255,255,.15) de fundo e borda de 2px a 30%.
    // `.pending-alerts-btn.has-pending`: fundo avermelhado e borda a 75%.
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (highlighted) RedMiss.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.15f))
            .border(
                2.dp,
                Color.White.copy(alpha = if (highlighted) 0.75f else 0.3f),
                CircleShape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun PendingBell(count: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val description = if (count > 0) {
        "Ver pendências de medicamentos ($count)"
    } else {
        "Ver pendências de medicamentos"
    }
    Box {
        HeaderCircleButton(
            icon = Icons.Filled.Notifications,
            description = description,
            size = 44.dp,
            iconSize = 22.dp,
            highlighted = count > 0,
            onClick = {
                onClick()
                val message = if (count > 0) {
                    "🔔 Você possui $count pendência(s) para hoje."
                } else {
                    "✅ Nenhuma pendência no momento."
                }
                MemoriaToast.show(message)
            },
        )
        if (count > 0) {
            // `.header-alert-badge`
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = (-6).dp)
                    .heightIn(min = 20.dp)
                    .widthIn(min = 20.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(RedMiss)
                    .border(2.dp, Color.White, RoundedCornerShape(999.dp))
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (count > 99) "99+" else count.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ShareButton(shareUrl: String?) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val text = "Estou usando o MemorIA para organizar meus lembretes de medicamentos."
    // `buildHeaderShareMessage()` (WhatsApp) vs `shareAppToOtherNetworks()`.
    val full = if (shareUrl.isNullOrBlank()) text else "$text Conheça: $shareUrl"
    val other = if (shareUrl.isNullOrBlank()) text else "$text $shareUrl"

    Box {
        HeaderCircleButton(
            icon = Icons.Filled.IosShare,
            description = "Compartilhar no WhatsApp e outras redes",
            size = 44.dp,
            iconSize = 22.dp,
            onClick = { open = !open },
        )
        // `.header-share-menu`: WhatsApp / Outras redes.
        // `.header-share-menu`: white, 14px corners, 1px rgba(15,23,42,.12) border.
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1F0F172A)),
        ) {
            DropdownMenuItem(
                text = { Text("WhatsApp", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF0F172A)) },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = {
                    open = false
                    val uri = Uri.parse("https://wa.me/?text=" + Uri.encode(full))
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: ActivityNotFoundException) {
                        MemoriaToast.show("❌ Não foi possível abrir o WhatsApp.")
                    }
                },
            )
            DropdownMenuItem(
                text = { Text("Outras redes", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF0F172A)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = {
                    open = false
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "MemorIA")
                        putExtra(Intent.EXTRA_TEXT, other)
                    }
                    try {
                        context.startActivity(Intent.createChooser(send, "Compartilhar MemorIA"))
                    } catch (_: ActivityNotFoundException) {
                        // `shareAppToOtherNetworks()` falls back to the clipboard.
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("MemorIA", other))
                        MemoriaToast.show("✅ Link do MemorIA copiado para compartilhar!")
                    }
                },
            )
        }
    }
}

/**
 * `#trialExpiryBanner` (`.trial-expiry-banner`): amber card at the top of the
 * page while the server's free Premium access has under a day left — hourglass,
 * the countdown text and a "Ver planos" button that opens Planos. Nothing is
 * charged when it ends: Premium only continues if the user subscribes.
 */
@Composable
fun TrialExpiryBanner(hoursLeft: Int, onManage: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFFFFBEB), Color(0xFFFEF3C7))))
            .border(1.dp, Color(0xFFF59E0B), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.HourglassBottom,
            contentDescription = null,
            tint = Color(0xFFB45309),
            modifier = Modifier.size(22.dp),
        )
        Text(
            "Seu acesso Premium gratuito termina em ${hoursLeft}h. Para continuar com o Premium, assine em Planos.",
            fontSize = 14.sp,
            color = Color(0xFF1F2937),
            modifier = Modifier.weight(1f),
        )
        // `.btn-secondary.trial-expiry-banner-btn`
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable(onClick = onManage)
                .widthIn(max = 128.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // Até 128dp: quebra entre as duas palavras em vez de esmagar o aviso ao lado.
            FitText(
                "Ver planos",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1F2937),
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}
