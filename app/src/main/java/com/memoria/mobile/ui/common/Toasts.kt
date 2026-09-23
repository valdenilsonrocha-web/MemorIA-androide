package com.memoria.mobile.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memoria.mobile.R
import com.memoria.mobile.ui.theme.BlueSecondary
import com.memoria.mobile.ui.theme.GreenOk
import com.memoria.mobile.ui.theme.RedMiss
import com.memoria.mobile.ui.theme.Snooze
import com.memoria.mobile.ui.theme.TextPrimary
import com.memoria.mobile.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The web's `showToast()` (app.js): a white card over the bottom of the page
 * with the MemorIA mark on the left, the emoji as a bold title, the message
 * (emoji removed) under it, the medications mark on the right and a "×" — and a
 * 5px left rule coloured by type (success / error / warning / info).
 *
 * Any screen can call [MemoriaToast.show] with the same string the web passes
 * to `showToast`; [MemoriaToastHost] (mounted once, in `MainActivity`) draws it.
 */
object MemoriaToast {
    internal data class Event(val message: String, val type: ToastType?, val durationMs: Long?)

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 16)
    internal val events = _events.asSharedFlow()

    /** [type] null = inferred from the emoji, exactly like the web. */
    fun show(message: String, type: ToastType? = null, durationMs: Long? = null) {
        if (message.isBlank()) return
        _events.tryEmit(Event(message.trim(), type, durationMs))
    }
}

enum class ToastType(val accent: Color) {
    SUCCESS(GreenOk),
    ERROR(RedMiss),
    WARNING(Snooze),
    INFO(BlueSecondary),
}

/** The web's icon/type inference, in the same order. */
internal fun classifyToast(message: String, explicit: ToastType?): Pair<String, ToastType> {
    if (explicit == ToastType.ERROR || message.contains("❌")) return "❌" to ToastType.ERROR
    if (explicit == ToastType.WARNING || message.contains("⚠️")) return "⚠️" to ToastType.WARNING
    if (explicit == ToastType.INFO || message.contains("ℹ️")) return "ℹ️" to ToastType.INFO
    val rules = listOf(
        "✅" to ToastType.SUCCESS,
        "🎉" to ToastType.SUCCESS,
        "⏰" to ToastType.INFO,
        "🚨" to ToastType.ERROR,
        "⭐" to ToastType.INFO,
        "🗑️" to ToastType.SUCCESS,
        "📲" to ToastType.INFO,
        "⏳" to ToastType.INFO,
    )
    rules.firstOrNull { message.contains(it.first) }?.let { return it }
    return "✅" to (explicit ?: ToastType.SUCCESS)
}

private val EMOJI_STRIP = listOf("✅", "❌", "⚠️", "⚠", "ℹ️", "ℹ", "🎉", "⏰", "🚨", "⭐", "🗑️", "🗑", "📲", "⏳", "🏥", "💊", "️")

internal fun cleanToastMessage(message: String): String =
    EMOJI_STRIP.fold(message) { acc, e -> acc.replace(e, "") }.trim()

private data class LiveToast(val id: Long, val icon: String, val type: ToastType, val text: String, val durationMs: Long)

/**
 * `.toast-container`: fixed 100px above the bottom, 12px from the sides on a
 * phone. Toasts stack; each leaves after 3s (the web always passes 3000).
 */
@Composable
fun MemoriaToastHost(modifier: Modifier = Modifier) {
    val toasts = remember { mutableStateListOf<LiveToast>() }
    LaunchedEffect(Unit) {
        var next = 0L
        MemoriaToast.events.collect { e ->
            val (icon, type) = classifyToast(e.message, e.type)
            val duration = e.durationMs ?: 3000L
            toasts.add(LiveToast(next++, icon, type, cleanToastMessage(e.message), duration))
            // The web keeps several at once; four is plenty on a phone.
            while (toasts.size > 4) toasts.removeAt(0)
        }
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            toasts.forEach { toast ->
                androidx.compose.runtime.key(toast.id) {
                    LaunchedEffect(toast.id) {
                        delay(toast.durationMs)
                        toasts.remove(toast)
                    }
                    ToastCard(toast.icon, toast.type, toast.text, onClose = { toasts.remove(toast) })
                }
            }
        }
    }
}

/** One `.toast`. Also usable as the look of a `SnackbarHost`, see [MemoriaSnackbar]. */
@Composable
fun ToastCard(icon: String, type: ToastType, text: String, onClose: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = GreenOk.copy(alpha = 0.2f), spotColor = GreenOk.copy(alpha = 0.2f))
            .clip(shape)
            .background(Color.White)
            .heightIn(min = 56.dp)
            .semantics {
                liveRegion = if (type == ToastType.ERROR) LiveRegionMode.Assertive else LiveRegionMode.Polite
                contentDescription = text
            },
    ) {
        // `border-left: 5px solid <type colour>`
        Box(Modifier.matchParentSize()) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(type.accent))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp + 5.dp, end = 14.dp, top = 12.dp, bottom = 12.dp).heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ToastMark(R.drawable.ic_logo_app)
            Column(Modifier.weight(1f)) {
                Text(icon, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary, lineHeight = 16.sp)
                Text(
                    text,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ToastMark(R.drawable.ic_menu_medications)
        }
        if (onClose != null) {
            Text(
                "×",
                fontSize = 16.sp,
                color = TextSecondary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 6.dp)
                    .size(18.dp)
                    .clickable(onClick = onClose)
                    .semantics { contentDescription = "Fechar notificação" },
            )
        }
    }
}

@Composable
private fun ToastMark(res: Int) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(28.dp).clip(CircleShape),
    )
}


/**
 * Drop-in look for a screen's own `SnackbarHost`:
 * `SnackbarHost(state) { MemoriaSnackbar(it) }` renders the web toast card.
 */
@Composable
fun MemoriaSnackbar(data: SnackbarData) {
    val message = data.visuals.message
    val (icon, type) = classifyToast(message, null)
    Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        ToastCard(icon, type, cleanToastMessage(message), onClose = { data.dismiss() })
    }
}
