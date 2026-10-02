package com.memoria.mobile.ui.common

import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.memoria.mobile.ReminderAlertActivity

/**
 * Asks for what Android needs before EVERY medication reminder can open as the
 * big "Hora do Medicamento!" alert: the full-screen permission (Android 14+) and
 * "Sobrepor a outros apps" (to open it while the phone is in use). Without them
 * the reminder shrinks to a small notification.
 *
 * Re-checked every time the app comes back to the front, so the dialog goes
 * away by itself once the user has granted what was missing. "Agora não" hides
 * it for the rest of this session only.
 */
@Composable
fun BigAlertPermissionDialog() {
    val context = LocalContext.current
    var missing by remember { mutableStateOf<List<Intent>>(emptyList()) }
    var dismissed by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        missing = ReminderAlertActivity.missingPermissionIntents(context)
        onPauseOrDispose { }
    }

    if (dismissed || missing.isEmpty()) return

    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text("Lembretes em tela cheia") },
        text = {
            Text(
                "Para o lembrete de remédio aparecer grande na tela, mesmo com o " +
                    "celular em uso, permita que a MemorIA mostre alertas em tela " +
                    "cheia e sobre outros apps.",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(missing.first().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("Permitir") }
        },
        dismissButton = {
            TextButton(onClick = { dismissed = true }) { Text("Agora não") }
        },
    )
}
