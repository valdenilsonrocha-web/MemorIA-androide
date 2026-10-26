package com.memoria.mobile.voice

import android.util.Base64
import com.memoria.mobile.data.remote.WildcardDnsFallback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * Sent only after the voice screen showed the notice that the conversation text is kept (v2):
 * the backend stores transcripts only for clients that send it, never for older app versions.
 */
const val TRANSCRIPT_CONSENT_HEADER = "X-MemorIA-Transcript-Consent"

/** No Google key on the device; only the MemorIA JWT goes to the configured backend. */
class MemoriaVoiceClient(
    private val rootUrl: String,
    private val token: String?,
    private val onUnauthorized: () -> Unit,
) : LiveVoiceConnection {
    private val client = OkHttpClient.Builder()
        .dns(WildcardDnsFallback())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var ready = false
    @Volatile private var closed = false
    private var emit: (LiveEvent) -> Unit = {}

    override fun connect(onEvent: (LiveEvent) -> Unit) {
        check(socket == null && !closed)
        emit = onEvent
        val bearer = token?.takeIf { it.isNotBlank() }
        if (bearer == null) {
            fail("Entre na sua conta do MemorIA para conversar por voz.")
            return
        }
        val url = socketUrl(rootUrl)
        socket = client.newWebSocket(
            Request.Builder().url(url).header("Authorization", "Bearer $bearer")
                .header(TRANSCRIPT_CONSENT_HEADER, "2").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (closed) webSocket.cancel()
                    // Wait for backend's ready message before recording or sending anything.
                }
                override fun onMessage(webSocket: WebSocket, text: String) = receive(text)
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) = receive(bytes.utf8())
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (closed) return
                    val message = when (response?.code) {
                        401 -> {
                            onUnauthorized()
                            "Sua sessão expirou. Aguarde a atualização e tente novamente."
                        }
                        429 -> "Já há uma conversa ativa ou muitas tentativas. Aguarde um minuto."
                        503 -> "A voz ainda não está configurada no servidor do MemorIA."
                        404 -> "O servidor do MemorIA ainda não tem a atualização de voz."
                        else -> "Não foi possível conectar à voz. Confira a internet e tente novamente."
                    }
                    fail(message)
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                    if (!closed) fail("A conexão de voz foi encerrada. Inicie outra conversa.")
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!closed) fail("A conexão de voz foi encerrada. Inicie outra conversa.")
                }
            },
        )
    }

    private fun receive(text: String) {
        if (closed) return
        try {
            val event = MemoriaVoiceProtocol.parse(text) ?: return
            if (event == LiveEvent.Ready) ready = true
            if (event is LiveEvent.Error) fail(event.message) else emit(event)
        } catch (_: Exception) {
            fail("O servidor enviou uma resposta de voz inválida. Inicie outra conversa.")
        }
    }

    private fun fail(message: String) {
        if (closed) return
        ready = false
        emit(LiveEvent.Error(message))
        close()
    }

    override fun sendAudio(pcm: ByteArray) {
        if (!ready || closed) return
        val ws = socket ?: return
        if (ws.queueSize() > 256_000 ||
            !ws.send(MemoriaVoiceProtocol.audio(Base64.encodeToString(pcm, Base64.NO_WRAP)))) {
            fail("A internet está lenta para a conversa de voz. Tente conectar novamente.")
        }
    }

    override fun confirmMedication(draftId: String, accepted: Boolean): Boolean =
        if (ready && !closed) socket?.send(MemoriaVoiceProtocol.confirmation(draftId, accepted)) == true else false

    override fun endAudio() {
        if (ready && !closed) socket?.send(MemoriaVoiceProtocol.audioStreamEnd())
    }

    override fun close() {
        closed = true
        ready = false
        socket?.cancel()
        socket = null
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    companion object {
        fun socketUrl(root: String): okhttp3.HttpUrl {
            val base = root.trim().toHttpUrl()
            require(base.scheme == "https" || base.host in listOf("localhost", "127.0.0.1", "10.0.2.2"))
            return base.newBuilder().encodedPath(base.encodedPath.trimEnd('/').removeSuffix("/api") + "/api/voice/live")
                .query(null).fragment(null).build()
        }
    }
}
