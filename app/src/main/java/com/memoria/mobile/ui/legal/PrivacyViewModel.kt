package com.memoria.mobile.ui.legal

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PrivacyUiState(
    val loading: Boolean = true,
    val working: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val user: User? = null,
    /** Bytes waiting for the user to choose a destination file. */
    val pendingExport: ByteArray? = null,
    val accountDeleted: Boolean = false,
) {
    val consentGranted: Boolean get() = user?.consentDate != null

    // Data class equality on a ByteArray compares references, which would make
    // two different exports look equal and stop the UI from recomposing.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PrivacyUiState) return false
        return loading == other.loading &&
            working == other.working &&
            error == other.error &&
            message == other.message &&
            user == other.user &&
            accountDeleted == other.accountDeleted &&
            pendingExport.contentEqualsOrBothNull(other.pendingExport)
    }

    override fun hashCode(): Int {
        var result = loading.hashCode()
        result = 31 * result + working.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + (message?.hashCode() ?: 0)
        result = 31 * result + (user?.hashCode() ?: 0)
        result = 31 * result + accountDeleted.hashCode()
        result = 31 * result + (pendingExport?.contentHashCode() ?: 0)
        return result
    }
}

private fun ByteArray?.contentEqualsOrBothNull(other: ByteArray?): Boolean =
    if (this == null || other == null) this === other else this.contentEquals(other)

/**
 * The three LGPD rights the backend already supports: access/portability
 * (`GET /auth/export-data`), revoking consent (`PUT /auth/consent`) and erasure
 * (`DELETE /auth/account`).
 */
class PrivacyViewModel(
    private val repo: MemoriaRepository,
    private val app: Application,
) : ViewModel() {

    private val _state = MutableStateFlow(PrivacyUiState())
    val state: StateFlow<PrivacyUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val r = repo.me()) {
                is ApiResult.Ok -> _state.value = _state.value.copy(loading = false, user = r.value)
                is ApiResult.Err -> _state.value = _state.value.copy(loading = false, error = r.message)
            }
        }
    }

    /** Fetches the export; the screen then asks the user where to save it. */
    fun requestExport() {
        _state.value = _state.value.copy(working = true, error = null)
        viewModelScope.launch {
            when (val r = repo.exportData()) {
                is ApiResult.Ok -> _state.value = _state.value.copy(
                    working = false,
                    pendingExport = r.value,
                )
                is ApiResult.Err -> _state.value = _state.value.copy(working = false, error = r.message)
            }
        }
    }

    /** Writes the fetched export to the document the user picked. */
    fun writeExportTo(destination: Uri) {
        val bytes = _state.value.pendingExport ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openOutputStream(destination)?.use { it.write(bytes) }
                        ?: error("sem acesso de escrita")
                }.isSuccess
            }
            _state.value = _state.value.copy(
                pendingExport = null,
                message = if (ok) {
                    "Dados exportados (${bytes.size / 1024} KB)."
                } else {
                    null
                },
                error = if (ok) null else "Não foi possível gravar o arquivo escolhido.",
            )
        }
    }

    fun discardExport() { _state.value = _state.value.copy(pendingExport = null) }

    fun setConsent(granted: Boolean) {
        _state.value = _state.value.copy(working = true, error = null)
        viewModelScope.launch {
            when (val r = repo.setConsent(granted)) {
                is ApiResult.Ok -> _state.value = _state.value.copy(
                    working = false,
                    user = r.value,
                    message = if (granted) {
                        "Consentimento registrado."
                    } else {
                        "Consentimento revogado. Você pode conceder de novo quando quiser."
                    },
                )
                is ApiResult.Err -> _state.value = _state.value.copy(working = false, error = r.message)
            }
        }
    }

    fun deleteAccount() {
        _state.value = _state.value.copy(working = true, error = null)
        viewModelScope.launch {
            when (val r = repo.deleteAccount()) {
                is ApiResult.Ok -> _state.value = _state.value.copy(working = false, accountDeleted = true)
                is ApiResult.Err -> _state.value = _state.value.copy(working = false, error = r.message)
            }
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
    fun clearError() { _state.value = _state.value.copy(error = null) }
}
