package com.memoria.mobile.ui.doctors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.local.CareContact
import com.memoria.mobile.data.local.MedicalConsultation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** The contact kinds offered by the web form, in the same order. */
val CARE_CONTACT_TYPES = listOf(
    "medico" to "Médico",
    "hospital" to "Hospital",
    "clinica" to "Clínica",
    "farmacia" to "Farmácia",
    "emergencia" to "Contato de Emergência",
)

fun careContactTypeLabel(type: String): String =
    CARE_CONTACT_TYPES.firstOrNull { it.first == type }?.second ?: "Contato"

data class MyDoctorsUiState(
    val loading: Boolean = true,
    val message: String? = null,
    val error: String? = null,
    val contacts: List<CareContact> = emptyList(),
    val consultations: List<MedicalConsultation> = emptyList(),
    // Contact form
    val contactType: String = "medico",
    val contactName: String = "",
    val contactInfo: String = "",
    val contactNotes: String = "",
    val editingContactId: String? = null,
    // Consultation form
    val consultationDateTime: String = "",
    val consultationProfessional: String = "",
    val consultationLocation: String = "",
    val consultationNotes: String = "",
    val editingConsultationId: String? = null,
)

class MyDoctorsViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(MyDoctorsUiState())
    val state: StateFlow<MyDoctorsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = false,
                contacts = repo.local.careContacts(),
                consultations = repo.local.consultations().sortedBy { it.dateTime },
            )
        }
    }

    // ---- Contact form ----

    fun onContactType(v: String) { _state.value = _state.value.copy(contactType = v) }
    fun onContactName(v: String) { _state.value = _state.value.copy(contactName = v) }
    fun onContactInfo(v: String) { _state.value = _state.value.copy(contactInfo = v) }
    fun onContactNotes(v: String) { _state.value = _state.value.copy(contactNotes = v) }

    fun clearContactForm() {
        _state.value = _state.value.copy(
            contactType = "medico",
            contactName = "",
            contactInfo = "",
            contactNotes = "",
            editingContactId = null,
        )
    }

    fun editContact(contact: CareContact) {
        _state.value = _state.value.copy(
            contactType = contact.type,
            contactName = contact.name,
            contactInfo = contact.info,
            contactNotes = contact.notes,
            editingContactId = contact.id,
        )
    }

    fun saveContact() {
        val s = _state.value
        if (s.contactName.isBlank() || s.contactInfo.isBlank()) {
            _state.value = s.copy(error = "Preencha nome e contato.")
            return
        }
        val contact = CareContact(
            id = s.editingContactId ?: UUID.randomUUID().toString(),
            type = s.contactType,
            name = s.contactName.trim(),
            info = s.contactInfo.trim(),
            notes = s.contactNotes.trim(),
        )
        val updated = if (s.editingContactId != null) {
            s.contacts.map { if (it.id == contact.id) contact else it }
        } else {
            s.contacts + contact
        }
        viewModelScope.launch {
            repo.local.saveCareContacts(updated)
            _state.value = _state.value.copy(
                contacts = updated,
                contactType = "medico",
                contactName = "",
                contactInfo = "",
                contactNotes = "",
                editingContactId = null,
                message = if (s.editingContactId != null) "Contato atualizado." else "Contato salvo.",
            )
        }
    }

    fun deleteContact(contact: CareContact) {
        val updated = _state.value.contacts.filterNot { it.id == contact.id }
        viewModelScope.launch {
            repo.local.saveCareContacts(updated)
            _state.value = _state.value.copy(contacts = updated, message = "Contato removido.")
        }
    }

    // ---- Consultation form ----

    fun onConsultationDateTime(v: String) { _state.value = _state.value.copy(consultationDateTime = v) }
    fun onConsultationProfessional(v: String) { _state.value = _state.value.copy(consultationProfessional = v) }
    fun onConsultationLocation(v: String) { _state.value = _state.value.copy(consultationLocation = v) }
    fun onConsultationNotes(v: String) { _state.value = _state.value.copy(consultationNotes = v) }

    fun clearConsultationForm() {
        _state.value = _state.value.copy(
            consultationDateTime = "",
            consultationProfessional = "",
            consultationLocation = "",
            consultationNotes = "",
            editingConsultationId = null,
        )
    }

    fun editConsultation(consultation: MedicalConsultation) {
        _state.value = _state.value.copy(
            consultationDateTime = consultation.dateTime,
            consultationProfessional = consultation.professional,
            consultationLocation = consultation.location,
            consultationNotes = consultation.notes,
            editingConsultationId = consultation.id,
        )
    }

    fun saveConsultation() {
        val s = _state.value
        if (s.consultationDateTime.isBlank() || s.consultationProfessional.isBlank()) {
            _state.value = s.copy(error = "Informe a data/hora e o profissional.")
            return
        }
        val consultation = MedicalConsultation(
            id = s.editingConsultationId ?: UUID.randomUUID().toString(),
            dateTime = s.consultationDateTime,
            professional = s.consultationProfessional.trim(),
            location = s.consultationLocation.trim(),
            notes = s.consultationNotes.trim(),
        )
        val updated = (
            if (s.editingConsultationId != null) {
                s.consultations.map { if (it.id == consultation.id) consultation else it }
            } else {
                s.consultations + consultation
            }
            ).sortedBy { it.dateTime }
        viewModelScope.launch {
            repo.local.saveConsultations(updated)
            _state.value = _state.value.copy(
                consultations = updated,
                consultationDateTime = "",
                consultationProfessional = "",
                consultationLocation = "",
                consultationNotes = "",
                editingConsultationId = null,
                message = if (s.editingConsultationId != null) "Consulta atualizada." else "Consulta salva.",
            )
        }
    }

    fun deleteConsultation(consultation: MedicalConsultation) {
        val updated = _state.value.consultations.filterNot { it.id == consultation.id }
        viewModelScope.launch {
            repo.local.saveConsultations(updated)
            _state.value = _state.value.copy(consultations = updated, message = "Consulta removida.")
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
    fun clearError() { _state.value = _state.value.copy(error = null) }
}
