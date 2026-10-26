package com.memoria.mobile.voice

import com.squareup.moshi.Moshi

/** MemorIA's small relay protocol, shared with backend/src/services/geminiLive.js. */
object MemoriaVoiceProtocol {
    private val json = Moshi.Builder().build().adapter(Map::class.java)

    fun audio(base64: String): String = json.toJson(mapOf("type" to "audio", "data" to base64))
    fun confirmation(draftId: String, accepted: Boolean): String =
        json.toJson(mapOf("type" to "confirm_medication", "draftId" to draftId, "accepted" to accepted))
    fun audioStreamEnd(): String = """{"type":"audio_end"}"""

    fun parse(frame: String): LiveEvent? {
        val root = json.fromJson(frame) ?: return null
        return when (root["type"]) {
            "status" -> when (root["text"]) {
                "ready" -> LiveEvent.Ready
                "interrupted" -> LiveEvent.Interrupted
                "turn_complete" -> LiveEvent.TurnComplete
                else -> null
            }
            "audio" -> (root["data"] as? String)?.takeIf { it.isNotBlank() }?.let(LiveEvent::Audio)
            "text" -> {
                val role = root["role"] as? String ?: return null
                val text = root["text"] as? String ?: return null
                if (role in listOf("user", "assistant") && text.isNotBlank()) LiveEvent.Transcript(role, text) else null
            }
            "medication_draft" -> {
                val id = root["draftId"] as? String ?: return null
                val medication = root["medication"] as? Map<*, *> ?: return null
                val name = medication["name"] as? String ?: return null
                val dosage = medication["dosage"] as? String ?: return null
                val times = (medication["times"] as? List<*>)?.filterIsInstance<String>() ?: return null
                val warnings = (root["warnings"] as? List<*>)?.mapNotNull { warning ->
                    val value = warning as? Map<*, *> ?: return@mapNotNull null
                    listOf("title", "description", "source").mapNotNull { value[it] as? String }.joinToString("\n")
                } ?: emptyList()
                LiveEvent.Draft(VoiceDraft(id, name, dosage, medication["frequency"] as? String ?: "daily",
                    times, (medication["weekDays"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList(),
                    (medication["stock"] as? Number)?.toInt() ?: 0, medication["instructions"] as? String ?: "",
                    warnings, root["limitation"] as? String ?: "Confirme as combinações com seu médico ou farmacêutico."))
            }
            "medication_result" -> LiveEvent.MedicationResult(root["draftId"] as? String ?: "",
                root["success"] == true, root["message"] as? String ?: "Confira o cadastro na lista de medicamentos.")
            "navigation" -> (root["destination"] as? String)?.takeIf {
                it in listOf("home", "meds", "health", "history", "calendar", "doctors", "prescriptions", "reports", "settings", "help")
            }?.let(LiveEvent::Navigation)
            // Do not display arbitrary server text: it could contain internal request details.
            "error" -> LiveEvent.Error("O serviço de voz encerrou a sessão. Tente iniciar outra conversa.")
            else -> null
        }
    }
}

data class VoiceDraft(
    val id: String, val name: String, val dosage: String, val frequency: String,
    val times: List<String>, val weekDays: List<Int>, val stock: Int, val instructions: String,
    val warnings: List<String>, val limitation: String,
)

sealed interface LiveEvent {
    data class Draft(val value: VoiceDraft) : LiveEvent
    data class MedicationResult(val draftId: String, val success: Boolean, val message: String) : LiveEvent
    data class Navigation(val destination: String) : LiveEvent
    data object Ready : LiveEvent
    data object Interrupted : LiveEvent
    data object TurnComplete : LiveEvent
    data class Audio(val base64: String) : LiveEvent
    data class Transcript(val role: String, val text: String) : LiveEvent
    data class Error(val message: String) : LiveEvent
}

interface LiveVoiceConnection {
    fun connect(onEvent: (LiveEvent) -> Unit)
    fun sendAudio(pcm: ByteArray)
    fun confirmMedication(draftId: String, accepted: Boolean): Boolean = false
    fun endAudio()
    fun close()
}

interface VoiceAudio {
    suspend fun start(onAudio: (ByteArray) -> Unit, onError: (String) -> Unit)
    fun play(base64: String)
    fun interrupt()
    suspend fun stop()
}
