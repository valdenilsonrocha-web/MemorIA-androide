package com.memoria.mobile.ui.prescriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.Prescription
import com.memoria.mobile.data.remote.PrescriptionRequest
import com.memoria.mobile.data.remote.User
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
) {
    val isPremium: Boolean get() = user?.isPremium == true
}

class PrescriptionsViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(PrescriptionsUiState())
    val state: StateFlow<PrescriptionsUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val user = (repo.me() as? ApiResult.Ok)?.value
            when (val r = repo.prescriptions()) {
                is ApiResult.Ok -> _state.value = _state.value.copy(
                    loading = false,
                    user = user,
                    prescriptions = r.value,
                )
                is ApiResult.Err -> _state.value = _state.value.copy(
                    loading = false,
                    user = user,
                    error = r.message,
                )
            }
        }
    }

    /** [imageData] is the `data:image/jpeg;base64,...` URL produced by the encoder. */
    fun upload(imageData: String?) {
        if (imageData == null) {
            _state.value = _state.value.copy(
                error = "Não foi possível preparar a imagem. Tente outra foto.",
            )
            return
        }
        _state.value = _state.value.copy(uploading = true)
        val request = PrescriptionRequest(
            fileName = "receita-${System.currentTimeMillis()}.jpg",
            imageData = imageData,
            capturedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
        viewModelScope.launch {
            when (val r = repo.createPrescription(request)) {
                is ApiResult.Ok -> {
                    _state.value = _state.value.copy(
                        uploading = false,
                        prescriptions = listOf(r.value) + _state.value.prescriptions,
                        message = "Receita salva.",
                    )
                }
                is ApiResult.Err ->
                    _state.value = _state.value.copy(uploading = false, error = r.message)
            }
        }
    }

    fun delete(prescription: Prescription) {
        val id = prescription.id ?: return
        viewModelScope.launch {
            when (val r = repo.deletePrescription(id)) {
                is ApiResult.Ok -> _state.value = _state.value.copy(
                    prescriptions = _state.value.prescriptions.filterNot { it.id == id },
                    message = "Receita removida.",
                )
                is ApiResult.Err -> _state.value = _state.value.copy(error = r.message)
            }
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
    fun clearError() { _state.value = _state.value.copy(error = null) }
}
