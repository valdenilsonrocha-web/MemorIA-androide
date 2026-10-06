package com.memoria.mobile

import android.app.ActivityManager
import android.app.KeyguardManager
import android.net.Uri
import android.provider.Settings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.reminders.DoseAlarm
import com.memoria.mobile.reminders.ReminderReceiver
import com.memoria.mobile.reminders.ReminderTexts
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.media.Ringtone
import androidx.core.content.ContextCompat
import com.memoria.mobile.reminders.MemoriaNotifications
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.memoria.mobile.data.local.MedicationLook
import com.memoria.mobile.ui.meds.MedicationIcon
import com.memoria.mobile.ui.theme.MemoriaTheme
import com.memoria.mobile.ui.theme.SurfaceLight
import com.memoria.mobile.ui.theme.TextPrimary
import com.memoria.mobile.ui.theme.TextSecondary
import com.memoria.mobile.ui.common.FitText
import com.memoria.mobile.ui.theme.GreenTake

/**
 * The web's `#notificationModal` in its `notification-fullscreen` form — what
 * `showMedicationModal()` shows when `alertDisplayType` is "fullscreen" (the
 * default). On a phone that means taking the whole screen, lock screen
 * included, which is what a notification's full-screen intent is for.
 *
 * "Soneca" and "Tomei" hand the dose to [ReminderReceiver] with the same
 * actions the notification buttons use, so recording and snoozing stay in one
 * place; the activity then closes.
 */
class ReminderAlertActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val stopHandler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopAlert() }

    // Fecha este ecrã quando a dose é respondida pelos botões da notificação:
    // sem isto o som em loop daqui continuava até ao fim do minuto.
    private var answeredReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()

        val dose = DoseAlarm.readFrom(intent)
        if (dose == null) {
            finish()
            return
        }
        val reminder = ReminderTexts.randomMedication(dose.time)

        // Inicia áudio e vibração urgente por até 1 minuto
        startUrgentAlert(dose)
        // Este ecrã toca o seu próprio som; a notificação por trás fica em silêncio
        // para não tocarem os dois ao mesmo tempo.
        MemoriaNotifications.silenceDose(this, dose)
        listenForAnswer(dose)

        setContent {
            val app = application as MemoriaApp
            val preset by app.graph.repository.themePresetFlow
                .collectAsStateWithLifecycle(initialValue = null)
            var medicationLook by remember { mutableStateOf<MedicationLook?>(null) }

            LaunchedEffect(dose.medicationId) {
                medicationLook = runCatching {
                    app.graph.repository.local.medicationLookFor(dose.medicationId)
                }.getOrNull()
            }

            MemoriaTheme(themePreset = preset) {
                ReminderModal(
                    title = reminder.title,
                    body = reminder.body,
                    medication = "${dose.medicationName} - ${dose.dosage}",
                    instructions = dose.instructions?.takeIf { it.isNotBlank() },
                    look = medicationLook,
                    onSnooze = { answer(dose, ReminderReceiver.ACTION_SNOOZE) },
                    onTaken = { answer(dose, ReminderReceiver.ACTION_TAKEN) },
                )
            }
        }
    }

    private fun answer(dose: DoseAlarm, action: String) {
        stopAlert()
        sendBroadcast(ReminderReceiver.intentFor(this, action, dose))
        finish()
    }

    private fun listenForAnswer(dose: DoseAlarm) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getIntExtra(ReminderReceiver.EXTRA_REQUEST_CODE, -1) == dose.requestCode) {
                    stopAlert()
                    finish()
                }
            }
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(ReminderReceiver.ACTION_DOSE_ANSWERED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        answeredReceiver = receiver
    }

    // singleTask: a second dose firing while this alert is open arrives here.
    // Rebuild for the new dose instead of leaving the old one on screen.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val next = DoseAlarm.readFrom(intent) ?: return
        val current = DoseAlarm.readFrom(getIntent())
        if (current?.requestCode == next.requestCode) return
        setIntent(intent)
        recreate()
    }

    override fun onDestroy() {
        answeredReceiver?.let { runCatching { unregisterReceiver(it) } }
        answeredReceiver = null
        stopAlert()
        super.onDestroy()
    }

    private fun startUrgentAlert(dose: DoseAlarm) {
        try {
            val soundUri = dose.sound.soundUri
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(this, soundUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                play()
            }
        } catch (_: Exception) {}

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            // Padrão urgente: pulsações repetidas
            val urgentPattern = longArrayOf(0, 400, 200, 400, 200, 600)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(urgentPattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(urgentPattern, 0)
            }
        } catch (_: Exception) {}

        // Limita o toque e vibração a 1 minuto (60.000 ms)
        stopHandler.postDelayed(stopRunnable, ReminderReceiver.ALARM_DURATION_MS)
    }

    private fun stopAlert() {
        try {
            stopHandler.removeCallbacks(stopRunnable)
            ringtone?.stop()
            ringtone = null
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {}
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
                ?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        /** The Intent a dose notification's full-screen PendingIntent opens. */
        fun intentFor(context: Context, dose: DoseAlarm): Intent =
            dose.writeTo(
                Intent(context, ReminderAlertActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            )

        /**
         * Opens the big alert right away when Android lets the app start an
         * activity from the background: while the app is on screen, or once the
         * user has allowed "Sobrepor a outros apps". Without either, the
         * notification's full-screen intent still covers the locked phone.
         */
        fun launchNow(context: Context, dose: DoseAlarm) {
            if (!isAppInForeground() && !Settings.canDrawOverlays(context)) return
            runCatching { context.startActivity(intentFor(context, dose)) }
        }

        private fun isAppInForeground(): Boolean {
            val info = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(info)
            return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        }

        /**
         * What the phone still needs for every medication reminder to open big:
         * the full-screen permission (Android 14+) and "Sobrepor a outros apps"
         * (to open it while the phone is in use). Empty when all is granted.
         */
        fun missingPermissionIntents(context: Context): List<Intent> = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val nm = context.getSystemService(android.app.NotificationManager::class.java)
                if (nm != null && !nm.canUseFullScreenIntent()) {
                    add(
                        Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
            }
            if (!Settings.canDrawOverlays(context)) {
                add(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
        }
    }
}

/**
 * `.modal.notification-fullscreen`: the page dimmed to rgba(0,0,0,.8), and the
 * white `.modal-content` (16px corners) filling it minus 12px each side —
 * header "Hora do Medicamento!", the three-column reminder layout, "Nome -
 * Dosagem", the instructions, and the "Soneca" / "Tomei" pair.
 */
@Composable
private fun ReminderModal(
    title: String,
    body: String,
    medication: String,
    instructions: String?,
    look: MedicationLook?,
    onSnooze: () -> Unit,
    onTaken: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(12.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White),
        ) {
            // .modal-header
            Row(
                Modifier.fillMaxWidth().padding(32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.Notifications, contentDescription = null, tint = colors.primary, modifier = Modifier.size(30.dp))
                FitText(
                    "Hora do Medicamento!",
                    maxLines = 2,
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
            }
            HorizontalDivider(thickness = 2.dp, color = colors.outline)

            // .modal-body — centred vertically in the fullscreen variant.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // .modal-reminder-layout — stacked under 480px, as on the web.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ModalMark(R.drawable.ic_logo_app, round = false)
                    Text(
                        title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        body,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                    if (look != null) {
                        MedicationIcon(look = look, size = 56.dp)
                    } else {
                        ModalMark(R.drawable.ic_menu_medications, round = true)
                    }
                }
                Spacer(Modifier.size(20.dp))
                // .modal-medication-dosage
                FitText(
                    medication,
                    maxLines = 3,
                    fontSize = 20.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
                if (instructions != null) {
                    Spacer(Modifier.size(20.dp))
                    // .medication-instructions
                    Text(
                        instructions,
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            HorizontalDivider(thickness = 2.dp, color = colors.outline)
            // .modal-actions: .btn-secondary.btn-large "Soneca" + .btn-primary.btn-large "Tomei"
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    border = BorderStroke(2.dp, colors.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SurfaceLight,
                        contentColor = TextPrimary,
                    ),
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    FitText("Soneca", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onTaken,
                    modifier = Modifier.weight(1f).heightIn(min = 72.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenTake, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    FitText("Tomei", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** `.modal-company-logo` / `.modal-medication-logo`: 38px. */
@Composable
private fun ModalMark(res: Int, round: Boolean) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(38.dp)
            .then(if (round) Modifier.clip(CircleShape) else Modifier),
    )
}
