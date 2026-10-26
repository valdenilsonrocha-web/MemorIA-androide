package com.memoria.mobile.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.voice.LiveEvent
import com.memoria.mobile.voice.LiveVoiceConnection
import com.memoria.mobile.voice.VoiceAudio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class VoiceStatus { IDLE, CONNECTING, LISTENING, SPEAKING }
data class VoiceLine(val role: String, val text: String)
data class VoiceUiState(
    val status: VoiceStatus = VoiceStatus.IDLE,
    val muted: Boolean = false,
    val error: String? = null,
    val lines: List<VoiceLine> = emptyList(),
    val userText: String = "",
    val assistantText: String = "",
    val draft: com.memoria.mobile.voice.VoiceDraft? = null,
    val confirming: Boolean = false,
    val notice: String? = null,
    val destination: String? = null,
) {
    val active: Boolean get() = status != VoiceStatus.IDLE
}

class VoiceViewModel(
    private val audio: VoiceAudio,
    private val onMedicationSaved: suspend () -> Unit = {},
    private val createConnection: () -> LiveVoiceConnection,
) : ViewModel() {
    private val mutableState = MutableStateFlow(VoiceUiState())
    val state = mutableState.asStateFlow()
    private val lifecycle = Mutex()
    private var connection: LiveVoiceConnection? = null
    private var timeout: Job? = null
    private var confirmationTimeout: Job? = null
    private var generation = 0L
    @Volatile private var muted = false

    init {
        // This finally runs even when the navigation entry is removed and viewModelScope is cancelled.
        viewModelScope.launch {
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                withContext(NonCancellable) { lifecycle.withLock { release() } }
            }
        }
    }

    fun start() = viewModelScope.launch {
        lifecycle.withLock {
            if (mutableState.value.active) return@withLock
            release()
            muted = false
            mutableState.value = VoiceUiState(status = VoiceStatus.CONNECTING)
            val session = generation
            try {
                val live = createConnection()
                connection = live
                live.connect { event ->
                    viewModelScope.launch {
                        lifecycle.withLock {
                            if (session == generation && connection === live) handle(event, session)
                        }
                    }
                }
                timeout = viewModelScope.launch {
                    delay(25_000)
                    lifecycle.withLock {
                        if (session == generation && mutableState.value.status == VoiceStatus.CONNECTING) {
                            timeout = null // Do not cancel this cleanup coroutine from release().
                            fail("A conexão de voz demorou a responder. Tente novamente.")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                fail("Não foi possível iniciar a conversa. Tente novamente.")
            }
        }
    }

    private suspend fun handle(event: LiveEvent, session: Long) {
        when (event) {
            is LiveEvent.Draft -> mutableState.update { it.copy(draft = event.value, confirming = false, notice = null) }
            is LiveEvent.Navigation -> mutableState.update { it.copy(destination = event.destination) }
            is LiveEvent.MedicationResult -> {
                if (event.draftId == mutableState.value.draft?.id) {
                    confirmationTimeout?.cancel()
                    mutableState.update { it.copy(draft = if (event.success) null else it.draft,
                        confirming = false, notice = event.message) }
                    if (event.success) runCatching { onMedicationSaved() }
                }
            }
            LiveEvent.Ready -> {
                if (mutableState.value.status != VoiceStatus.CONNECTING) return
                timeout?.cancel()
                try {
                    val live = connection ?: return
                    audio.start(
                        onAudio = { bytes -> if (!muted) live.sendAudio(bytes) },
                        onError = { message ->
                            viewModelScope.launch {
                                lifecycle.withLock { if (session == generation) fail(message) }
                            }
                        },
                    )
                    mutableState.update { it.copy(status = VoiceStatus.LISTENING) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    fail("Não foi possível abrir o áudio. Confira a permissão do microfone e tente novamente.")
                }
            }
            is LiveEvent.Audio -> {
                try {
                    audio.play(event.base64)
                    mutableState.update { it.copy(status = VoiceStatus.SPEAKING) }
                } catch (_: Exception) {
                    fail("A resposta de áudio não pôde ser reproduzida. Inicie outra conversa.")
                }
            }
            is LiveEvent.Transcript -> mutableState.update {
                if (event.role == "user") it.copy(userText = (it.userText + event.text).takeLast(8000))
                else it.copy(assistantText = (it.assistantText + event.text).takeLast(8000))
            }
            LiveEvent.Interrupted -> {
                audio.interrupt()
                finishTurn()
            }
            LiveEvent.TurnComplete -> finishTurn()
            is LiveEvent.Error -> fail(event.message)
        }
    }

    private fun finishTurn() {
        mutableState.update { state ->
            val lines = state.lines.toMutableList()
            if (state.userText.isNotBlank()) lines += VoiceLine("user", state.userText.trim())
            if (state.assistantText.isNotBlank()) lines += VoiceLine("assistant", state.assistantText.trim())
            state.copy(status = VoiceStatus.LISTENING, lines = lines.takeLast(40), userText = "", assistantText = "")
        }
    }

    fun toggleMute() = viewModelScope.launch {
        lifecycle.withLock {
            if (!mutableState.value.active || mutableState.value.status == VoiceStatus.CONNECTING) return@withLock
            muted = !muted
            if (muted) connection?.endAudio()
            mutableState.update { it.copy(muted = muted) }
        }
    }

    fun confirmMedication(accepted: Boolean) = viewModelScope.launch {
        lifecycle.withLock {
            val draft = mutableState.value.draft ?: return@withLock
            if (mutableState.value.confirming) return@withLock
            if (connection?.confirmMedication(draft.id, accepted) != true) {
                mutableState.update { it.copy(error = "A conversa precisa estar conectada para confirmar o cadastro.") }
                return@withLock
            }
            mutableState.update { it.copy(confirming = true, notice = null) }
            confirmationTimeout?.cancel()
            confirmationTimeout = viewModelScope.launch {
                delay(15_000)
                mutableState.update { if (it.draft?.id == draft.id && it.confirming)
                    it.copy(confirming = false, notice = "A confirmação demorou. Confira a lista de medicamentos antes de tentar novamente.") else it }
            }
            if (!accepted) mutableState.update { it.copy(draft = null, confirming = false) }
        }
    }

    fun permissionDenied() {
        mutableState.update { it.copy(error = "Permita o microfone nas configurações do Android para conversar por voz.") }
    }

    fun stop() = viewModelScope.launch {
        lifecycle.withLock {
            finishTurn()
            release()
            mutableState.update { it.copy(status = VoiceStatus.IDLE, muted = false) }
        }
    }

    private suspend fun fail(message: String) {
        finishTurn()
        release()
        mutableState.update { it.copy(status = VoiceStatus.IDLE, muted = false, error = message) }
    }

    private suspend fun release() {
        generation++
        confirmationTimeout?.cancel()
        mutableState.update { it.copy(draft = null, confirming = false) }
        muted = true
        timeout?.cancel()
        timeout = null
        connection?.close()
        connection = null
        withContext(NonCancellable) { audio.stop() }
    }

    override fun onCleared() {
        // Close networking immediately; the finally block above releases native audio.
        connection?.close()
        super.onCleared()
    }
}
