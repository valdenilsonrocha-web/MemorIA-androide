package com.memoria.mobile.ui.doctors

import kotlinx.coroutines.flow.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.local.CareContact
import com.memoria.mobile.data.local.MedicalConsultation
import com.memoria.mobile.reminders.ReminderScheduler
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

class MyDoctorsViewModel(
    private val repo: MemoriaRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(MyDoctorsUiState())
    val state: StateFlow<MyDoctorsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(
                loading = false,
                contacts = repo.local.careContacts(),
                consultations = repo.local.consultations(),
            ) }
        }
    }

    // ---- Contact form ----

    fun onContactType(v: String) { _state.update { it.copy(contactType = v) } }
    fun onContactName(v: String) { _state.update { it.copy(contactName = v) } }
    fun onContactInfo(v: String) { _state.update { it.copy(contactInfo = v) } }
    fun onContactNotes(v: String) { _state.update { it.copy(contactNotes = v) } }

    fun clearContactForm() {
        _state.update { it.copy(
            contactType = "medico",
            contactName = "",
            contactInfo = "",
            contactNotes = "",
            editingContactId = null,
        ) }
    }

    fun editContact(contact: CareContact) {
        _state.update { it.copy(
            contactType = contact.type,
            contactName = contact.name,
            contactInfo = contact.info,
            contactNotes = contact.notes,
            editingContactId = contact.id,
        ) }
    }

    fun saveContact() {
        val s = _state.value
        if (s.contactName.isBlank() || s.contactInfo.isBlank()) {
            _state.value = s.copy(error = "❌ Preencha tipo, nome e contato.")
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
            _state.update { it.copy(
                contacts = updated,
                contactType = "medico",
                contactName = "",
                contactInfo = "",
                contactNotes = "",
                editingContactId = null,
                message = "✅ Contato salvo com sucesso.",
            ) }
        }
    }

    fun deleteContact(contact: CareContact) {
        val updated = _state.value.contacts.filterNot { it.id == contact.id }
        viewModelScope.launch {
            repo.local.saveCareContacts(updated)
            _state.update { it.copy(
                contacts = updated,
                contactType = "medico",
                contactName = "",
                contactInfo = "",
                contactNotes = "",
                editingContactId = null,
                message = "🗑️ Contato removido.",
            ) }
        }
    }

    /**
     * O botão "Agenda": preenche nome e contato com o que o utilizador escolheu
     * na agenda do telefone (telefone primeiro, e-mail na falta dele).
     */
    fun fillFromAgenda(name: String?, info: String?) {
        val s = _state.value
        if (name == null && info == null) {
            _state.value = s.copy(error = "⚠️ Não foi possível acessar a agenda.")
            return
        }
        _state.value = s.copy(
            contactName = name?.takeIf { it.isNotBlank() } ?: s.contactName,
            contactInfo = info ?: "",
        )
    }

    // ---- Consultation form ----

    fun onConsultationDateTime(v: String) { _state.update { it.copy(consultationDateTime = v) } }
    fun onConsultationProfessional(v: String) { _state.update { it.copy(consultationProfessional = v) } }
    fun onConsultationLocation(v: String) { _state.update { it.copy(consultationLocation = v) } }
    fun onConsultationNotes(v: String) { _state.update { it.copy(consultationNotes = v) } }

    fun clearConsultationForm() {
        _state.update { it.copy(
            consultationDateTime = "",
            consultationProfessional = "",
            consultationLocation = "",
            consultationNotes = "",
            editingConsultationId = null,
        ) }
    }

    fun editConsultation(consultation: MedicalConsultation) {
        _state.update { it.copy(
            consultationDateTime = consultation.dateTime,
            consultationProfessional = consultation.professional,
            consultationLocation = consultation.location,
            consultationNotes = consultation.notes,
            editingConsultationId = consultation.id,
        ) }
    }

    fun saveConsultation() {
        val s = _state.value
        if (s.consultationDateTime.isBlank() || s.consultationProfessional.isBlank()) {
            _state.value = s.copy(error = "❌ Preencha data/hora e profissional.")
            return
        }
        val consultation = MedicalConsultation(
            id = s.editingConsultationId ?: UUID.randomUUID().toString(),
            dateTime = s.consultationDateTime,
            professional = s.consultationProfessional.trim(),
            location = s.consultationLocation.trim(),
            notes = s.consultationNotes.trim(),
        )
        // Como na web (`list.unshift`): a consulta nova entra no topo da lista;
        // a editada fica no mesmo lugar.
        val updated = if (s.editingConsultationId != null) {
            s.consultations.map { if (it.id == consultation.id) consultation else it }
        } else {
            listOf(consultation) + s.consultations
        }
        viewModelScope.launch {
            repo.local.saveConsultations(updated)
            pushConsultations(updated)
            _state.update { it.copy(
                consultations = updated,
                consultationDateTime = "",
                consultationProfessional = "",
                consultationLocation = "",
                consultationNotes = "",
                editingConsultationId = null,
                message = "✅ Consulta salva com sucesso.",
            ) }
        }
    }

    fun deleteConsultation(consultation: MedicalConsultation) {
        val updated = _state.value.consultations.filterNot { it.id == consultation.id }
        viewModelScope.launch {
            repo.local.saveConsultations(updated)
            pushConsultations(updated)
            _state.update { it.copy(
                consultations = updated,
                consultationDateTime = "",
                consultationProfessional = "",
                consultationLocation = "",
                consultationNotes = "",
                editingConsultationId = null,
                message = "🗑️ Consulta removida.",
            ) }
        }
    }

    /**
     * Mirrors the consultations to the server, which feeds them into the
     * automated e-mail report. Silent on failure: the phone already has them and
     * this screen must keep working offline. The server replaces the whole list,
     * so the next save repairs whatever a dropped request missed.
     */
    private suspend fun pushConsultations(items: List<MedicalConsultation>) {
        // Arm the phone's own alarms FIRST. The reminder is what the user asked
        // for when they entered the appointment, and it must not depend on the
        // network call below succeeding.
        scheduler.scheduleConsultations(items)
        repo.syncHealthRecords(
            vitalSigns = repo.local.vitalSigns().map {
                com.memoria.mobile.data.remote.VitalSignsPayload(
                    date = it.date,
                    systolic = it.systolic,
                    diastolic = it.diastolic,
                    heartRate = it.heartRate,
                    spo2 = it.spo2,
                    glucose = it.glucose,
                    glucoseContext = it.glucoseContext,
                    hba1c = it.hba1c,
                    temperature = it.temperature,
                )
            },
            consultations = items.map {
                com.memoria.mobile.data.remote.ConsultationPayload(
                    id = it.id,
                    dateTime = isoFromLocal(it.dateTime),
                    professional = it.professional,
                    location = it.location,
                    notes = it.notes,
                )
            },
        )
    }

    /** `datetime-local` has no zone; the server wants a real instant. */
    private fun isoFromLocal(raw: String): String = runCatching {
        java.time.LocalDateTime.parse(raw).atZone(java.time.ZoneId.systemDefault())
            .toOffsetDateTime()
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }.getOrDefault(raw)

    fun consumeMessage() { _state.update { it.copy(message = null) } }
    fun clearError() { _state.update { it.copy(error = null) } }
}
