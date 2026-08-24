package com.memoria.mobile.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.CardInput
import com.memoria.mobile.data.remote.PaymentConfig
import com.memoria.mobile.data.remote.User
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
    // Card form. Held only while the screen is open and cleared the moment the
    // card becomes a token — nothing here is ever persisted.
    val cardNumber: String = "",
    val holderName: String = "",
    val expiry: String = "",
    val securityCode: String = "",
    val holderDocument: String = "",
    val cardNumberError: Boolean = false,
    val holderNameError: Boolean = false,
    val expiryError: Boolean = false,
    val securityCodeError: Boolean = false,
    val holderDocumentError: Boolean = false,
) {
    val isPremium: Boolean get() = user?.isPremium == true

    val subscriptionStatus: String? get() = user?.subscriptionStatus

    /** Falls back to the published prices if the config call fails. */
    val monthlyPrice: String
        get() = config?.plans?.monthly?.displayPrice ?: "R$ 14,90/mês"

    val annualPrice: String
        get() = config?.plans?.annual?.displayPrice ?: "R$ 12,90/mês (plano anual)"

    val trialDays: Int get() = 7

    /** Without it the app cannot tokenise, so the form would be a dead end. */
    val canPay: Boolean get() = !config?.publicKey.isNullOrBlank()
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
                // The account's own e-mail is the sensible default payer.
                payerEmail = _state.value.payerEmail.ifBlank { user?.email.orEmpty() },
                holderName = _state.value.holderName.ifBlank { user?.name.orEmpty() },
            )
        }
    }

    fun select(cycle: BillingCycle) { _state.value = _state.value.copy(selected = cycle) }

    fun onPayerEmail(v: String) { _state.value = _state.value.copy(payerEmail = v, error = null) }

    // Raw digits only. Formatting happens in the field's VisualTransformation, so
    // the caret never fights the formatter.
    fun onCardNumber(v: String) {
        _state.value = _state.value.copy(
            cardNumber = v.filter { it.isDigit() }.take(19),
            cardNumberError = false,
            error = null,
        )
    }

    fun onHolderName(v: String) {
        _state.value = _state.value.copy(holderName = v, holderNameError = false, error = null)
    }

    fun onExpiry(v: String) {
        _state.value = _state.value.copy(
            expiry = v.filter { it.isDigit() }.take(4),
            expiryError = false,
            error = null,
        )
    }

    fun onSecurityCode(v: String) {
        _state.value = _state.value.copy(
            securityCode = v.filter { it.isDigit() }.take(4),
            securityCodeError = false,
            error = null,
        )
    }

    fun onHolderDocument(v: String) {
        _state.value = _state.value.copy(
            holderDocument = v.filter { it.isDigit() }.take(11),
            holderDocumentError = false,
            error = null,
        )
    }

    /**
     * The whole purchase, without leaving the app: validate locally, swap the
     * card for a Mercado Pago token on the device, then let the MemorIA backend
     * create the subscription from that token alone.
     */
    fun subscribe() {
        val s = _state.value

        val expiry = CardValidation.parseExpiry(s.expiry)
        val invalid = s.copy(
            cardNumberError = !CardValidation.isCardNumberPlausible(s.cardNumber),
            holderNameError = !CardValidation.isHolderNamePlausible(s.holderName),
            expiryError = expiry == null,
            securityCodeError = !CardValidation.isSecurityCodePlausible(s.securityCode),
            holderDocumentError = !CardValidation.isCpfValid(s.holderDocument),
        )
        if (invalid.cardNumberError || invalid.holderNameError || invalid.expiryError ||
            invalid.securityCodeError || invalid.holderDocumentError
        ) {
            _state.value = invalid.copy(error = "Confira os dados destacados e tente de novo.")
            return
        }
        if (!s.payerEmail.contains("@")) {
            _state.value = s.copy(error = "Informe um e-mail válido para a cobrança.")
            return
        }
        val publicKey = s.config?.publicKey
        if (publicKey.isNullOrBlank()) {
            _state.value = s.copy(
                error = "Pagamento indisponível: o servidor não informou a chave do Mercado Pago.",
            )
            return
        }

        _state.value = s.copy(working = true, error = null)
        viewModelScope.launch {
            val card = CardInput(
                number = s.cardNumber,
                holderName = s.holderName,
                expiryMonth = expiry!!.first,
                expiryYear = expiry.second,
                securityCode = s.securityCode,
                holderDocument = s.holderDocument,
            )

            val token = repo.tokenizeCard(publicKey, card).getOrElse { failure ->
                _state.value = _state.value.copy(
                    working = false,
                    error = failure.message ?: "Não foi possível validar o cartão.",
                )
                return@launch
            }

            // The card is now a token; drop the raw data from state immediately
            // rather than leaving it in memory for the rest of the session.
            clearCardFields()

            when (val r = repo.subscribeWithCard(s.selected.apiValue, token, s.payerEmail)) {
                is ApiResult.Ok -> {
                    val user = (repo.me() as? ApiResult.Ok)?.value
                    _state.value = _state.value.copy(
                        working = false,
                        user = user ?: _state.value.user,
                        message = "Assinatura ativa. Bem-vindo ao Premium!",
                    )
                }
                is ApiResult.Err -> _state.value = _state.value.copy(
                    working = false,
                    error = r.message,
                )
            }
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

    private fun clearCardFields() {
        _state.value = _state.value.copy(
            cardNumber = "",
            expiry = "",
            securityCode = "",
            holderDocument = "",
        )
    }

    override fun onCleared() {
        clearCardFields()
        super.onCleared()
    }

    fun consumeMessage() { _state.value = _state.value.copy(message = null) }
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
