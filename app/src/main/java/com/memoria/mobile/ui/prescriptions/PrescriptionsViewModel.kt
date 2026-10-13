package com.memoria.mobile.ui.prescriptions

import kotlinx.coroutines.flow.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.Prescription
import com.memoria.mobile.data.remote.PrescriptionRequest
import com.memoria.mobile.data.remote.User
import com.memoria.mobile.ui.common.PlanLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class PrescriptionsUiState(
    val loading: Boolean = true,
    val uploading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val user: User? = null,
    val prescriptions: List<Prescription> = emptyList(),
    val readings: Map<String, String> = emptyMap(),
) {
    val isPremium: Boolean get() = PlanLimits.hasFullAccess(user)
}

class PrescriptionsViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(PrescriptionsUiState())
    val state: StateFlow<PrescriptionsUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val user = (repo.me() as? ApiResult.Ok)?.value
            val readings = repo.local.prescriptionReadings().associate { it.prescriptionId to it.text }
            when (val r = repo.prescriptions()) {
                is ApiResult.Ok -> _state.update { it.copy(
                    loading = false,
                    user = user,
                    prescriptions = r.value,
                    readings = readings,
                ) }
                is ApiResult.Err -> _state.update { it.copy(
                    loading = false,
                    user = user,
                    error = r.message,
                ) }
            }
        }
    }

    /**
     * Decodes/re-encodes the picture off the main thread (a full-resolution
     * camera photo takes a noticeable moment), then uploads it.
     */
    fun uploadEncoded(fileName: String? = null, encode: () -> String?) {
        if (!PlanLimits.canStorePrescriptions(_state.value.user)) {
            _state.update { it.copy(error = "⭐ Recurso premium: ative o Premium para salvar receitas.") }
            return
        }
        _state.update { it.copy(uploading = true, error = null) }
        viewModelScope.launch {
            val data = withContext(Dispatchers.Default) { runCatching(encode).getOrNull() }
            if (data == null) {
                _state.update { it.copy(
                    uploading = false,
                    error = "❌ Não foi possível processar a imagem da receita.",
                ) }
                return@launch
            }
            val text = withContext(Dispatchers.Default) {
                val bitmap = decodeDataUrl(data) ?: return@withContext ""
                try {
                    PrescriptionOcr.read(bitmap)
                } finally {
                    bitmap.recycle()
                }
            }
            upload(data, fileName, text)
        }
    }

    /** [imageData] is the `data:image/jpeg;base64,...` URL produced by the encoder. */
    fun upload(imageData: String?, fileName: String? = null, ocrText: String = "") {
        // Paid feature, same as on the website, and the backend does not gate it
        // — without this check the phone gave away what the site charges for.
        if (!PlanLimits.canStorePrescriptions(_state.value.user)) {
            _state.update { it.copy(error = "⭐ Recurso premium: ative o Premium para salvar receitas.") }
            return
        }
        if (imageData == null) {
            _state.update { it.copy(
                error = "❌ Não foi possível processar a imagem da receita.",
            ) }
            return
        }
        _state.update { it.copy(uploading = true) }
        val request = PrescriptionRequest(
            // `file.name || 'receita.jpg'` na web: o nome do ficheiro escolhido.
            fileName = fileName?.takeIf { it.isNotBlank() } ?: "receita-${System.currentTimeMillis()}.jpg",
            imageData = imageData,
            ocrText = ocrText.ifBlank { null },
            capturedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
        viewModelScope.launch {
            when (val r = repo.createPrescription(request)) {
                is ApiResult.Ok -> {
                    val saved = r.value
                    val id = saved.id
                    if (id != null && ocrText.isNotBlank()) repo.local.savePrescriptionReading(id, ocrText)
                    val shown = ocrText.ifBlank { saved.ocrText }
                    _state.update { it.copy(
                        uploading = false,
                        prescriptions = listOf(saved) + _state.value.prescriptions,
                        readings = if (id != null && shown.isNotBlank()) {
                            _state.value.readings + (id to shown)
                        } else {
                            _state.value.readings
                        },
                        message = if (shown.isNotBlank()) "✅ Receita salva e lida." else "✅ Receita salva. O texto não foi reconhecido.",
                    ) }
                }
                is ApiResult.Err ->
                    _state.update { it.copy(uploading = false, error = r.message) }
            }
        }
    }

    fun delete(prescription: Prescription) {
        val id = prescription.id ?: return
        viewModelScope.launch {
            when (val r = repo.deletePrescription(id)) {
            is ApiResult.Ok -> {
                repo.local.deletePrescriptionReading(id)
                _state.update { it.copy(
                    prescriptions = _state.value.prescriptions.filterNot { it.id == id },
                    readings = _state.value.readings - id,
                    message = "🗑️ Receita removida do histórico.",
                ) }
            }
            is ApiResult.Err -> _state.update { it.copy(error = r.message) }
            }
        }
    }

    fun consumeMessage() { _state.update { it.copy(message = null) } }
    fun clearError() { _state.update { it.copy(error = null) } }
}
