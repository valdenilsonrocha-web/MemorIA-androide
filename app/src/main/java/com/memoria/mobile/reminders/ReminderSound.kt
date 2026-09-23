package com.memoria.mobile.reminders

import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri
import com.memoria.mobile.BuildConfig
import com.memoria.mobile.R

/**
 * The web app's "Som de Notificação" (`#notificationSound`), expressed the only
 * way Android allows.
 *
 * Every web option is here with the same id and label. Its tone is the web's own
 * WebAudio pattern (`notificationService.playNotificationSound()`) rendered to a
 * WAV in `res/raw/memoria_sound_*.wav`, and its vibration is the web's
 * `vibratePatterns` entry (Android patterns start with a 0 ms delay).
 *
 * A channel's sound, vibration and importance are frozen the moment the channel
 * is created — `createNotificationChannel` on an existing id silently ignores
 * them. So each option is its OWN channel, and switching the setting switches
 * which channel the dose reminder is posted to. Changing an option later means a
 * new [channelId] suffix, never an edit in place.
 *
 * The web's native channel is importance 5 (max), with vibration: every audible
 * option is IMPORTANCE_HIGH so the reminder pops over whatever is on screen.
 */
enum class ReminderSound(
    val id: String,
    val label: String,
    val description: String,
    val channelId: String,
    val importance: Int,
    private val vibration: LongArray?,
    private val rawSound: Int?,
) {
    // ---- The web's options, in the web's order ----
    SYSTEM(
        id = "system",
        label = "🆕 Padrão do sistema (vibra + toque)",
        description = "Três notas ascendentes, com vibração.",
        channelId = "memoria_doses_system_v2",
        importance = NotificationManager.IMPORTANCE_HIGH,
        vibration = longArrayOf(0, 200, 100, 200),
        rawSound = R.raw.memoria_sound_system,
    ),
    CHIME(
        id = "chime",
        label = "🎵 Chime — tom suave ascendente",
        description = "Quatro notas ascendentes suaves.",
        channelId = "memoria_doses_chime_v2",
        importance = NotificationManager.IMPORTANCE_HIGH,
        vibration = longArrayOf(0, 150),
        rawSound = R.raw.memoria_sound_chime,
    ),
    BELL(
        id = "bell",
        label = "🔔 Sino — clássico",
        description = "Toque único ressonante.",
        channelId = "memoria_doses_bell_v2",
        importance = NotificationManager.IMPORTANCE_HIGH,
        vibration = longArrayOf(0, 300),
        rawSound = R.raw.memoria_sound_bell,
    ),
    PING(
        id = "ping",
        label = "🔁 Ping — curto e limpo",
        description = "Toque rápido e limpo.",
        channelId = "memoria_doses_ping_v2",
        importance = NotificationManager.IMPORTANCE_HIGH,
        vibration = longArrayOf(0, 100),
        rawSound = R.raw.memoria_sound_ping,
    ),
    DING(
        id = "ding",
        label = "🔦 Ding — duplo positivo",
        description = "Duas notas, estilo mensagem.",
        channelId = "memoria_doses_ding_v2",
        importance = NotificationManager.IMPORTANCE_HIGH,
        vibration = longArrayOf(0, 100, 80, 100),
        rawSound = R.raw.memoria_sound_ding,
    ),
    ALERT(
        id = "alert",
        label = "🟠 Alerta — triplo urgente",
        description = "Três bipes urgentes.",
        channelId = "memoria_doses_alert_v2",
        importance = NotificationManager.IMPORTANCE_HIGH,
        vibration = longArrayOf(0, 100, 60, 100, 60, 200),
        rawSound = R.raw.memoria_sound_alert,
    ),
    GENTLE(
        id = "gentle",
        label = "🌞 Suave — tom baixo",
        description = "Tom baixo e discreto.",
        channelId = "memoria_doses_gentle_v2",
        importance = NotificationManager.IMPORTANCE_HIGH,
        vibration = longArrayOf(0, 150),
        rawSound = R.raw.memoria_sound_gentle,
    ),
    VIBRATE(
        id = "vibrate",
        label = "📷 Só vibração",
        description = "Sem som, só vibração.",
        channelId = "memoria_doses_vibrate_v2",
        importance = NotificationManager.IMPORTANCE_HIGH,
        vibration = longArrayOf(0, 200, 100, 200, 100, 200),
        rawSound = null,
    ),
    SILENT(
        id = "silent",
        label = "🔇 Silencioso",
        description = "Sem som e sem vibração.",
        channelId = "memoria_doses_silent_v2",
        importance = NotificationManager.IMPORTANCE_HIGH,
        vibration = null,
        rawSound = null,
    ),

    // ---- Ids the onboarding stores ("default" / "gentle" / "loud") ----
    // On the web these fall through `playNotificationSound()`'s `default:` case:
    // the system tone with a single 200 ms buzz. "soft" is the phone's older id
    // for the onboarding's "Suave" and plays the gentle tone.
    PADRAO(
        id = "default",
        label = "Padrão",
        description = "Toque padrão do MemorIA, com vibração.",
        channelId = "memoria_doses_default_v2",
        importance = NotificationManager.IMPORTANCE_HIGH,
        vibration = longArrayOf(0, 200),
        rawSound = R.raw.memoria_sound_system,
    ),
    SUAVE(
        id = "soft",
        label = "Suave",
        description = "Tom baixo e discreto.",
        channelId = "memoria_doses_soft_v2",
        importance = NotificationManager.IMPORTANCE_HIGH,
        vibration = longArrayOf(0, 150),
        rawSound = R.raw.memoria_sound_gentle,
    ),
    ALTO(
        id = "loud",
        label = "Alto",
        description = "Toque padrão do MemorIA, com vibração.",
        channelId = "memoria_doses_loud_v2",
        importance = NotificationManager.IMPORTANCE_HIGH,
        vibration = longArrayOf(0, 200),
        rawSound = R.raw.memoria_sound_system,
    );

    /** Null for "Só vibração" and "Silencioso". */
    val soundUri: Uri?
        get() = rawSound?.let { Uri.parse("android.resource://${BuildConfig.APPLICATION_ID}/$it") }

    val audioAttributes: AudioAttributes
        get() = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    val vibrationPattern: LongArray? get() = vibration?.copyOf()

    companion object {
        /** The nine options of the web's `#notificationSound` select, in order. */
        val webOptions: List<ReminderSound> =
            listOf(SYSTEM, CHIME, BELL, PING, DING, ALERT, GENTLE, VIBRATE, SILENT)

        /** Falls back to [SYSTEM] — the web's default — for an unknown id. */
        fun from(id: String?): ReminderSound =
            entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}
