package com.memoria.mobile.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.media.AudioDeviceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/** Full duplex PCM16 mono, matching EVA: 16 kHz capture / 24 kHz playback. */
class AndroidVoiceAudio(context: Context) : VoiceAudio {
    private val app = context.applicationContext
    private val manager = app.getSystemService(AudioManager::class.java)
    private val trackLock = Any()
    private val generation = AtomicLong()
    private var scope: CoroutineScope? = null
    private var capture: Job? = null
    private var playback: Job? = null
    private var recorder: AudioRecord? = null
    private var track: AudioTrack? = null
    private var echo: AcousticEchoCanceler? = null
    private var noise: NoiseSuppressor? = null
    private var focus: AudioFocusRequest? = null
    private var previousMode: Int? = null
    private var previousSpeaker: Boolean? = null
    private var selectedCommunicationDevice = false
    private var chunks: Channel<Chunk>? = null
    private var onFailure: (String) -> Unit = {}
    private data class Chunk(val bytes: ByteArray, val generation: Long)

    @SuppressLint("MissingPermission")
    override suspend fun start(onAudio: (ByteArray) -> Unit, onError: (String) -> Unit) = withContext(Dispatchers.IO) {
        check(scope == null)
        check(ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED) { "Microfone sem permissão" }
        onFailure = onError
        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS ||
                        change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        onError("A conversa foi encerrada porque outro aplicativo precisa do áudio.")
                    }
                }.build()
            focus = focusRequest
            check(manager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            previousMode = manager.mode
            manager.mode = AudioManager.MODE_IN_COMMUNICATION
            routeAudio()
            val inputMin = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val outputMin = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(inputMin > 0 && outputMin > 0)
            val input = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(inputMin, 6400),
            )
            recorder = input
            check(input.state == AudioRecord.STATE_INITIALIZED)
            if (AcousticEchoCanceler.isAvailable()) {
                echo = runCatching { AcousticEchoCanceler.create(input.audioSessionId)?.apply { enabled = true } }.getOrNull()
            }
            if (NoiseSuppressor.isAvailable()) {
                noise = runCatching { NoiseSuppressor.create(input.audioSessionId)?.apply { enabled = true } }.getOrNull()
            }
            val output = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(24000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(maxOf(outputMin, 9600))
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            track = output
            check(output.state == AudioTrack.STATE_INITIALIZED)
            output.play()
            input.startRecording()
            check(input.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            val queue = Channel<Chunk>(128)
            chunks = queue
            val workers = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = workers
            capture = workers.launch {
                val buffer = ByteArray(3200) // 100 ms of mono PCM16.
                try {
                    while (isActive) {
                        val count = input.read(buffer, 0, buffer.size)
                        if (count < 0) error("AudioRecord failed")
                        if (count > 0 && isActive) onAudio(buffer.copyOf(count))
                    }
                } catch (_: Exception) {
                    if (isActive) onError("Não foi possível capturar o microfone. Inicie outra conversa.")
                }
            }
            playback = workers.launch {
                try {
                    for (chunk in queue) {
                        var offset = 0
                        while (offset < chunk.bytes.size && currentCoroutineContext().isActive) {
                            val written = synchronized(trackLock) {
                                if (chunk.generation != generation.get()) -1
                                else output.write(chunk.bytes, offset, chunk.bytes.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                            }
                            if (chunk.generation != generation.get()) break
                            check(written >= 0)
                            offset += written
                            if (written == 0) delay(10)
                        }
                    }
                } catch (_: Exception) {
                    if (isActive) onError("Não foi possível reproduzir a voz. Inicie outra conversa.")
                }
            }
        } catch (e: Exception) {
            stop()
            throw e
        }
    }

    override fun play(base64: String) {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        check(bytes.size % 2 == 0) { "Invalid PCM16" }
        val queue = chunks ?: return
        if (queue.trySend(Chunk(bytes, generation.get())).isFailure) {
            onFailure("A resposta de voz ficou atrasada. Inicie uma nova conversa.")
        }
    }

    override fun interrupt() {
        synchronized(trackLock) {
            generation.incrementAndGet()
            chunks?.let { queue -> while (queue.tryReceive().isSuccess) { /* drop queued speech */ } }
            track?.let { output ->
                runCatching { output.pause(); output.flush(); output.play() }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun routeAudio() {
        if (Build.VERSION.SDK_INT >= 31) {
            val devices = manager.availableCommunicationDevices
            // Prefer a connected headset/hearing aid; otherwise speak through the loudspeaker.
            val external = devices.firstOrNull { it.type !in listOf(
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            ) }
            val target = external ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (target != null) selectedCommunicationDevice = manager.setCommunicationDevice(target)
        } else {
            previousSpeaker = manager.isSpeakerphoneOn
            if (!manager.isWiredHeadsetOn && !manager.isBluetoothScoOn) manager.isSpeakerphoneOn = true
        }
    }

    @Suppress("DEPRECATION")
    private fun restoreRoute() {
        if (Build.VERSION.SDK_INT >= 31 && selectedCommunicationDevice) manager.clearCommunicationDevice()
        selectedCommunicationDevice = false
        if (Build.VERSION.SDK_INT < 31) previousSpeaker?.let { manager.isSpeakerphoneOn = it }
        previousSpeaker = null
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        capture?.cancel()
        playback?.cancel()
        runCatching { recorder?.stop() } // Unblocks a pending AudioRecord.read.
        capture?.cancelAndJoin()
        playback?.cancelAndJoin()
        scope?.cancel()
        scope = null
        capture = null
        playback = null
        chunks?.close()
        chunks = null
        echo?.release()
        noise?.release()
        echo = null
        noise = null
        recorder?.release()
        recorder = null
        synchronized(trackLock) {
            runCatching { track?.pause(); track?.flush(); track?.stop() }
            track?.release()
            track = null
        }
        restoreRoute()
        previousMode?.let { manager.mode = it }
        previousMode = null
        focus?.let { manager.abandonAudioFocusRequest(it) }
        focus = null
    }
}
