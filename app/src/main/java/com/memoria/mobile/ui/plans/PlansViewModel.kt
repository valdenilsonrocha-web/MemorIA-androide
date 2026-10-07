package com.memoria.mobile.ui.plans

import kotlinx.coroutines.flow.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.CardInput
import com.memoria.mobile.data.remote.PaymentConfig
import com.memoria.mobile.data.remote.User
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BillingCycle(val apiValue: String) {
    MONTHLY("monthly"),
    ANNUAL("annual"),
}

/**
 * "Dados do pagador para nota fiscal" — the web's `invoicePayer`. It can be
 * someone other than the user (a son paying for his father), and, like on the
 * web, it is remembered on the device for the next time.
 */
@JsonClass(generateAdapter = true)
data class InvoicePayer(
    val legalName: String = "",
    val document: String = "",
    val phone: String = "",
    val email: String = "",
    val addressLine1: String = "",
    val addressNumber: String = "",
    val neighborhood: String = "",
    val city: String = "",
    val state: String = "",
    val zipCode: String = "",
)

data class PlansUiState(
    val loading: Boolean = true,
    val working: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val user: User? = null,
    val config: PaymentConfig? = null,
    val selected: BillingCycle = BillingCycle.ANNUAL,
    val payer: InvoicePayer = InvoicePayer(),
    // What the phone remembers about the subscription it started (web `settings.premium`).
    val savedCycle: String? = null,
    val savedCardLast4: String? = null,
    val savedTrialEndsAtMillis: Long? = null,
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
    val subscriptionStatus: String? get() = user?.subscriptionStatus

    /**
     * A subscription the user actually started. `isPremium` alone is not enough:
     * the backend also grants it during the free-access period after sign-up,
     * and that user still has to be able to subscribe here.
     */
    val hasSubscription: Boolean
        get() = subscriptionStatus?.lowercase()?.let {
            it in setOf("authorized", "active", "trialing", "pending", "paused")
        } == true

    /** The backend's prices are what gets charged; the web's are the fallback. */
    val monthlyPrice: String
        get() = config?.plans?.monthly?.displayPrice?.ifBlank { null } ?: "R$ 18,90/mês"

    val annualPrice: String
        get() = config?.plans?.annual?.displayPrice?.ifBlank { null } ?: "R$ 12,90/mês"

    /** Free trial of the subscription (`TRIAL_PERIOD_DAYS` on the backend, 7 days). */
    val trialDays: Int get() = TRIAL_DAYS

    val annualNote: String
        get() = if (config?.plans?.annual?.displayPrice.isNullOrBlank()) {
            "$trialDays dias grátis e depois cobrança anual de R$ 154,80"
        } else {
            "$trialDays dias grátis e depois cobrança de $annualPrice"
        }

    val summary: String
        get() {
            val after = when (selected) {
                BillingCycle.ANNUAL ->
                    if (config?.plans?.annual?.displayPrice.isNullOrBlank()) {
                        "cobrança anual de R$ 154,80 (equivalente a R$ 12,90/mês)"
                    } else {
                        "cobrança de $annualPrice"
                    }
                BillingCycle.MONTHLY ->
                    if (config?.plans?.monthly?.displayPrice.isNullOrBlank()) {
                        "R$ 18,90 por mês"
                    } else {
                        "cobrança de $monthlyPrice"
                    }
            }
            return "$trialDays dias grátis via cartão de crédito. Após o período de teste, $after."
        }

    /** Checkout gateway named in the security notes. */
    val gatewayName: String
        get() = if (config?.gateway == "stripe") "Stripe" else "Mercado Pago"

    /** Without it the app cannot tokenise, so the form would be a dead end. */
    val canPay: Boolean get() = !config?.publicKey.isNullOrBlank()

    companion object {
        // Backend `TRIAL_DAYS` / `TRIAL_PERIOD_DAYS` = 7, the web's "7 dias grátis".
        const val TRIAL_DAYS = 7
    }
}

class PlansViewModel(private val repo: MemoriaRepository) : ViewModel() {

    private val _state = MutableStateFlow(PlansUiState())
    val state: StateFlow<PlansUiState> = _state.asStateFlow()

    private val payerAdapter = Moshi.Builder().build().adapter(InvoicePayer::class.java)

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val savedPayer = repo.local.uiSetting(KEY_PAYER)
                ?.let { runCatching { payerAdapter.fromJson(it) }.getOrNull() }
            val savedCycle = repo.local.uiSetting(KEY_CYCLE)
            val savedLast4 = repo.local.uiSetting(KEY_LAST4)
            val savedTrial = repo.local.uiSetting(KEY_TRIAL_ENDS)?.toLongOrNull()
            val user = (repo.me() as? ApiResult.Ok)?.value
            val config = (repo.paymentConfig() as? ApiResult.Ok)?.value
            val current = _state.value
            _state.value = current.copy(
                loading = false,
                user = user,
                config = config,
                selected = if (savedCycle == "monthly") BillingCycle.MONTHLY else current.selected,
                payer = savedPayer ?: current.payer.copy(
                    // The account's own data is the sensible default payer.
                    legalName = current.payer.legalName.ifBlank { user?.name.orEmpty() },
                    email = current.payer.email.ifBlank { user?.email.orEmpty() },
                    phone = current.payer.phone.ifBlank { user?.phone.orEmpty().filter { it.isDigit() } },
                    city = current.payer.city.ifBlank { user?.city.orEmpty() },
                    state = current.payer.state.ifBlank { user?.state.orEmpty() },
                ),
                savedCycle = savedCycle,
                savedCardLast4 = savedLast4,
                savedTrialEndsAtMillis = savedTrial,
                holderName = current.holderName.ifBlank { user?.name.orEmpty() },
            )
        }
    }

    fun select(cycle: BillingCycle) { _state.update { it.copy(selected = cycle) } }

    fun onPayer(update: (InvoicePayer) -> InvoicePayer) {
        _state.update { it.copy(payer = update(_state.value.payer), error = null) }
    }

    // Raw digits only. Formatting happens in the field's VisualTransformation, so
    // the caret never fights the formatter.
    fun onCardNumber(v: String) {
        _state.update { it.copy(
            cardNumber = v.filter { it.isDigit() }.take(19),
            cardNumberError = false,
            error = null,
        ) }
    }

    fun onHolderName(v: String) {
        _state.update { it.copy(holderName = v, holderNameError = false, error = null) }
    }

    fun onExpiry(v: String) {
        _state.update { it.copy(
            expiry = v.filter { it.isDigit() }.take(4),
            expiryError = false,
            error = null,
        ) }
    }

    fun onSecurityCode(v: String) {
        _state.update { it.copy(
            securityCode = v.filter { it.isDigit() }.take(4),
            securityCodeError = false,
            error = null,
        ) }
    }

    fun onHolderDocument(v: String) {
        _state.update { it.copy(
            holderDocument = v.filter { it.isDigit() }.take(11),
            holderDocumentError = false,
            error = null,
        ) }
    }

    /** The web's `missingInvoiceFields`, in the same order and wording. */
    private fun missingInvoiceFields(p: InvoicePayer): List<String> {
        val document = p.document.filter { it.isDigit() }
        val phone = p.phone.filter { it.isDigit() }
        val zip = p.zipCode.filter { it.isDigit() }
        return buildList {
            if (p.legalName.isBlank()) add("nome/razão social do pagador")
            if (document.length != 11 && document.length != 14) add("CPF/CNPJ")
            if (p.email.isBlank()) add("email")
            if (phone.length < 10) add("telefone")
            if (p.addressLine1.isBlank()) add("logradouro")
            if (p.addressNumber.isBlank()) add("numero")
            if (p.neighborhood.isBlank()) add("bairro")
            if (p.city.isBlank()) add("cidade")
            if (p.state.trim().length != 2) add("UF")
            if (zip.length != 8) add("CEP")
        }
    }

    /**
     * The whole purchase, without leaving the app: validate locally, swap the
     * card for a token on the device, then let the MemorIA backend create the
     * subscription from that token alone.
     */
    fun subscribe() {
        val s = _state.value

        val missing = missingInvoiceFields(s.payer)
        if (missing.isNotEmpty()) {
            _state.value = s.copy(
                error = "⚠️ Complete os dados do pagador para nota fiscal: ${missing.joinToString(", ")}.",
            )
            return
        }

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
        val publicKey = s.config?.publicKey
        if (publicKey.isNullOrBlank()) {
            _state.value = s.copy(error = "❌ Erro ao criar sessão de pagamento.")
            return
        }

        val payer = s.payer.copy(
            legalName = s.payer.legalName.trim(),
            document = s.payer.document.filter { it.isDigit() },
            phone = s.payer.phone.filter { it.isDigit() },
            email = s.payer.email.trim().lowercase(),
            addressLine1 = s.payer.addressLine1.trim(),
            addressNumber = s.payer.addressNumber.trim(),
            neighborhood = s.payer.neighborhood.trim(),
            city = s.payer.city.trim(),
            state = s.payer.state.trim().uppercase(),
            zipCode = s.payer.zipCode.filter { it.isDigit() },
        )

        _state.value = s.copy(working = true, error = null, payer = payer)
        viewModelScope.launch {
            // Like the web's `persistInvoicePayerFromForm`: remembered before paying.
            repo.local.setUiSetting(KEY_PAYER, payerAdapter.toJson(payer))

            val card = CardInput(
                number = s.cardNumber,
                holderName = s.holderName,
                expiryMonth = expiry!!.first,
                expiryYear = expiry.second,
                securityCode = s.securityCode,
                holderDocument = s.holderDocument,
            )
            val last4 = s.cardNumber.takeLast(4)

            val token = repo.tokenizeCard(publicKey, card).getOrElse { failure ->
                _state.update { it.copy(
                    working = false,
                    error = failure.message ?: "Erro ao conectar ao gateway de pagamento.",
                ) }
                return@launch
            }

            // The card is now a token; drop the raw data from state immediately
            // rather than leaving it in memory for the rest of the session.
            clearCardFields()

            when (val r = repo.subscribeWithCard(s.selected.apiValue, token, payer.email)) {
                is ApiResult.Ok -> {
                    // The server says how long the trial it started is; 7 days otherwise.
                    val trialDays = r.value.trialDays.takeIf { it > 0 } ?: PlansUiState.TRIAL_DAYS
                    val trialEnds = System.currentTimeMillis() + trialDays * DAY_MILLIS
                    repo.local.setUiSetting(KEY_CYCLE, s.selected.apiValue)
                    repo.local.setUiSetting(KEY_LAST4, last4)
                    repo.local.setUiSetting(KEY_TRIAL_ENDS, trialEnds.toString())
                    val user = (repo.me() as? ApiResult.Ok)?.value
                    _state.update { it.copy(
                        working = false,
                        user = user ?: _state.value.user,
                        savedCycle = s.selected.apiValue,
                        savedCardLast4 = last4,
                        savedTrialEndsAtMillis = trialEnds,
                        message = "🎉 Assinatura iniciada! Você está no teste grátis de $trialDays dias.",
                    ) }
                }
                is ApiResult.Err -> _state.update { it.copy(
                    working = false,
                    error = r.message,
                ) }
            }
        }
    }

    fun cancelSubscription() {
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.cancelSubscription()) {
                is ApiResult.Ok -> {
                    val user = (repo.me() as? ApiResult.Ok)?.value
                    _state.update { it.copy(
                        working = false,
                        user = user ?: _state.value.user,
                        message = "Assinatura cancelada.",
                    ) }
                }
                is ApiResult.Err -> _state.update { it.copy(working = false, error = r.message) }
            }
        }
    }

    private fun clearCardFields() {
        _state.update { it.copy(
            cardNumber = "",
            expiry = "",
            securityCode = "",
            holderDocument = "",
        ) }
    }

    override fun onCleared() {
        clearCardFields()
        super.onCleared()
    }

    fun consumeMessage() { _state.update { it.copy(message = null) } }
    fun clearError() { _state.update { it.copy(error = null) } }

    private companion object {
        const val KEY_PAYER = "invoice_payer"
        const val KEY_CYCLE = "premium_cycle"
        const val KEY_LAST4 = "premium_card_last4"
        const val KEY_TRIAL_ENDS = "premium_trial_ends_at"
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}

/** What Premium unlocks, in the order the web page lists it; `true` = "NOVO" badge. */
val PREMIUM_FEATURES = listOf(
    "Cadastro ilimitado de medicações" to false,
    "Relatórios avançados" to false,
    "Múltiplos contatos de emergência" to true,
    "Alertas para contatos via WhatsApp e e-mail" to true,
    "Armazenamento de receitas por foto no histórico" to true,
    "Sincronização em nuvem das receitas" to true,
    "Integração com Alexa e assistente de voz nas Configurações" to true,
)
