package com.memoria.mobile.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.PaymentConfig
import com.memoria.mobile.data.remote.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BillingCycle { MONTHLY, ANNUAL }

data class PlansUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val user: User? = null,
    val config: PaymentConfig? = null,
    val selected: BillingCycle = BillingCycle.ANNUAL,
) {
    val isPremium: Boolean get() = user?.isPremium == true

    /** Falls back to the prices shown on the web page when the API is unreachable. */
    val monthlyPrice: String
        get() = config?.plans?.monthly?.displayPrice ?: "R$ 14,90/mês"

    val annualPrice: String
        get() = config?.plans?.annual?.displayPrice ?: "R$ 9,90/mês (cobrança anual)"
}

class PlansViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(PlansUiState())
    val state: StateFlow<PlansUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val user = (repo.me() as? ApiResult.Ok)?.value
            when (val r = repo.paymentConfig()) {
                is ApiResult.Ok ->
                    _state.value = _state.value.copy(loading = false, user = user, config = r.value)
                // Prices have static fallbacks, so a config failure is a note,
                // not a blocker — the page still explains the plans.
                is ApiResult.Err ->
                    _state.value = _state.value.copy(loading = false, user = user, error = r.message)
            }
        }
    }

    fun select(cycle: BillingCycle) { _state.value = _state.value.copy(selected = cycle) }

    fun clearError() { _state.value = _state.value.copy(error = null) }
}

/** What Premium unlocks, in the order the web page lists it. */
val PREMIUM_FEATURES = listOf(
    "Cadastro ilimitado de medicações",
    "Relatórios avançados",
    "Múltiplos contatos de emergência",
    "Alertas para contatos via WhatsApp e e-mail",
    "Armazenamento de receitas por foto no histórico",
    "Sincronização em nuvem das receitas",
    "Integração com Alexa e assistente de voz",
    "Sem anúncios",
)
