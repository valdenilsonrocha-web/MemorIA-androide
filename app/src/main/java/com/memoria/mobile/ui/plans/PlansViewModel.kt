package com.memoria.mobile.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.PaymentConfig
import com.memoria.mobile.data.remote.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BillingCycle(val apiValue: String) {
    MONTHLY("monthly"),
    ANNUAL("annual"),
}

data class PlansUiState(
    val loading: Boolean = true,
    val working: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val user: User? = null,
    val config: PaymentConfig? = null,
    val selected: BillingCycle = BillingCycle.ANNUAL,
    val payerEmail: String = "",
    /** Gateway checkout link, once opened; kept so the status can be polled. */
    val checkoutUrl: String? = null,
    val sessionId: String? = null,
    /**
     * Host of the gateway back_url. When the checkout navigates there the flow
     * is over, and the WebView hands control back to the native screen instead
     * of letting the site render.
     */
    val returnHost: String? = null,
    val awaitingConfirmation: Boolean = false,
) {
    val isPremium: Boolean get() = user?.isPremium == true

    val subscriptionStatus: String? get() = user?.subscriptionStatus

    /** Falls back to the site's published prices if the config call fails. */
    val monthlyPrice: String
        get() = config?.plans?.monthly?.displayPrice ?: "R$ 14,90/mês"

    val annualPrice: String
        get() = config?.plans?.annual?.displayPrice ?: "R$ 12,90/mês (plano anual)"

    val trialDays: Int get() = 7

    val canCheckout: Boolean
        get() = !working && payerEmail.contains("@") && payerEmail.contains(".")
}

class PlansViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(PlansUiState())
    val state: StateFlow<PlansUiState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val user = (repo.me() as? ApiResult.Ok)?.value
            val config = (repo.paymentConfig() as? ApiResult.Ok)?.value
            _state.value = _state.value.copy(
                loading = false,
                user = user,
                config = config,
                // The account's own e-mail is the sensible default payer, so the
                // common case needs no typing at all.
                payerEmail = _state.value.payerEmail.ifBlank { user?.email.orEmpty() },
            )
        }
    }

    fun select(cycle: BillingCycle) { _state.value = _state.value.copy(selected = cycle) }

    fun onPayerEmail(v: String) { _state.value = _state.value.copy(payerEmail = v, error = null) }

    /**
     * Asks the backend to open a Mercado Pago subscription. The screen then shows
     * the gateway's page inside the app, never in a browser.
     */
    fun startCheckout() {
        val s = _state.value
        if (!s.canCheckout) {
            _state.value = s.copy(error = "Informe um e-mail válido para a cobrança.")
            return
        }
        _state.value = s.copy(working = true, error = null)
        viewModelScope.launch {
            when (val r = repo.createCheckout(s.selected.apiValue, s.payerEmail)) {
                is ApiResult.Ok -> {
                    val url = r.value.url
                    if (url.isNullOrBlank()) {
                        _state.value = _state.value.copy(
                            working = false,
                            error = "O servidor não devolveu o link de pagamento.",
                        )
                    } else {
                        _state.value = _state.value.copy(
                            working = false,
                            checkoutUrl = url,
                            sessionId = r.value.sessionId,
                            returnHost = returnHostOf(repo.currentBaseUrl()),
                        )
                    }
                }
                is ApiResult.Err -> _state.value = _state.value.copy(working = false, error = r.message)
            }
        }
    }

    fun consumeCheckoutUrl() { _state.value = _state.value.copy(checkoutUrl = null) }

    /**
     * Called when the user comes back from the gateway.
     *
     * Mercado Pago confirms a subscription asynchronously (its webhook reaches
     * the backend a moment later), so a single check right after the tab closes
     * would usually say "not yet". It polls a few times before giving up, and a
     * "not confirmed" answer is reported as pending rather than as an error.
     */
    fun confirmAfterCheckout() {
        val sessionId = _state.value.sessionId ?: return refreshUser()
        _state.value = _state.value.copy(awaitingConfirmation = true)
        viewModelScope.launch {
            repeat(CONFIRMATION_ATTEMPTS) { attempt ->
                if (attempt > 0) delay(CONFIRMATION_DELAY_MS)
                val status = repo.subscriptionStatus(sessionId)
                if (status is ApiResult.Ok) {
                    val user = (repo.me() as? ApiResult.Ok)?.value
                    _state.value = _state.value.copy(
                        awaitingConfirmation = false,
                        user = user ?: _state.value.user,
                        sessionId = null,
                        message = "Assinatura confirmada. Bem-vindo ao Premium!",
                    )
                    return@launch
                }
            }
            val user = (repo.me() as? ApiResult.Ok)?.value
            _state.value = _state.value.copy(
                awaitingConfirmation = false,
                user = user ?: _state.value.user,
                message = if (user?.isPremium == true) {
                    "Assinatura confirmada. Bem-vindo ao Premium!"
                } else {
                    "Ainda não recebemos a confirmação do pagamento. Assim que o Mercado " +
                        "Pago confirmar, o Premium é liberado sozinho."
                },
            )
        }
    }

    fun cancelSubscription() {
        _state.value = _state.value.copy(working = true, error = null)
        viewModelScope.launch {
            when (val r = repo.cancelSubscription()) {
                is ApiResult.Ok -> {
                    val user = (repo.me() as? ApiResult.Ok)?.value
                    _state.value = _state.value.copy(
                        working = false,
                        user = user ?: _state.value.user,
                        message = "Assinatura cancelada.",
                    )
                }
                is ApiResult.Err -> _state.value = _state.value.copy(working = false, error = r.message)
            }
        }
    }

    private fun refreshUser() {
        viewModelScope.launch {
            (repo.me() as? ApiResult.Ok)?.value?.let {
                _state.value = _state.value.copy(user = it)
            }
        }
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
    fun clearError() { _state.value = _state.value.copy(error = null) }

    /**
     * The gateway sends the user back to the MemorIA site when it is done. The
     * app never lets that page render — it matches the host and closes the
     * checkout instead, so the flow ends on a native screen.
     */
    private fun returnHostOf(baseUrl: String): String? =
        runCatching { android.net.Uri.parse(baseUrl).host }.getOrNull()

    private companion object {
        const val CONFIRMATION_ATTEMPTS = 5
        const val CONFIRMATION_DELAY_MS = 3_000L
    }
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
