package com.memoria.mobile.ui.whatsapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.Caregiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WhatsAppUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val patientPhone: String = "",
    val caregivers: List<Caregiver> = emptyList(),
    val isPremium: Boolean = false,
) {
    val maxCaregivers: Int get() = if (isPremium) 3 else 1
    val canAddCaregiver: Boolean get() = caregivers.size < maxCaregivers
}

class WhatsAppViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(WhatsAppUiState())
    val state: StateFlow<WhatsAppUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val r = repo.me()) {
                is ApiResult.Ok -> _state.value = WhatsAppUiState(
                    loading = false,
                    patientPhone = r.value.phone,
                    caregivers = r.value.caregivers,
                    isPremium = r.value.isPremium,
                )
                is ApiResult.Err -> _state.value = _state.value.copy(loading = false, error = r.message)
            }
        }
    }

    fun onPatientPhone(v: String) { _state.value = _state.value.copy(patientPhone = v) }

    fun addCaregiver(name: String, phone: String, relation: String) {
        val s = _state.value
        val digits = phone.filter { it.isDigit() }
        when {
            !s.canAddCaregiver -> { _state.value = s.copy(error = caregiverLimitMessage(s.maxCaregivers)); return }
            name.isBlank() -> { _state.value = s.copy(error = "Informe o nome do cuidador."); return }
            digits.length < 10 -> { _state.value = s.copy(error = "Telefone do cuidador inválido (DDD + número)."); return }
        }
        val caregiver = Caregiver(name = name.trim(), phone = digits, relation = relation.trim())
        _state.value = s.copy(caregivers = s.caregivers + caregiver, error = null)
    }

    fun removeCaregiver(caregiver: Caregiver) {
        _state.value = _state.value.copy(caregivers = _state.value.caregivers - caregiver)
    }

    fun save() {
        val s = _state.value
        _state.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val r = repo.setCaregivers(s.caregivers, s.patientPhone)) {
                is ApiResult.Ok -> _state.value = _state.value.copy(
                    saving = false,
                    patientPhone = r.value.phone,
                    caregivers = r.value.caregivers,
                    isPremium = r.value.isPremium,
                    message = "Configuração salva. Os avisos irão pelo WhatsApp.",
                )
                is ApiResult.Err -> _state.value = _state.value.copy(saving = false, error = r.message)
            }
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
    fun clearError() { _state.value = _state.value.copy(error = null) }

    private fun caregiverLimitMessage(max: Int): String =
        if (max == 1) "Plano gratuito permite 1 cuidador. Assine o Premium para até 3."
        else "Limite de $max cuidadores atingido."
}
