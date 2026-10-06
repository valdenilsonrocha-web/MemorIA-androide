package com.memoria.mobile.ui.plans

import android.app.Activity
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.*
import com.memoria.mobile.BuildConfig
import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import com.memoria.mobile.data.remote.User
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PlayPlan(val basePlanId: String, val offerToken: String, val price: String, val terms: String)
data class PlayPlansState(
    val loading: Boolean = true, val working: Boolean = false, val backendReady: Boolean = false,
    val plans: List<PlayPlan> = emptyList(), val selected: String = BuildConfig.PLAY_ANNUAL_BASE_PLAN,
    val user: User? = null, val error: String? = null, val message: String? = null
)

class PlayBillingViewModel(private val repo: MemoriaRepository, app: Application) : ViewModel(), PurchasesUpdatedListener {
    private val mutable = MutableStateFlow(PlayPlansState())
    val state = mutable.asStateFlow()
    private val appPackageName = app.packageName
    private var connecting = false
    private var details: ProductDetails? = null
    private val verification = Mutex()
    private val client = BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build())
        .enableAutoServiceReconnection().build()

    init { connect() }

    fun connect() {
        if (connecting) return
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            refreshUser()
            when (val result = repo.googlePlayConfig()) {
                is ApiResult.Ok -> {
                    val ready = result.value.configured && result.value.packageName == appPackageName && result.value.productId == BuildConfig.PLAY_PRODUCT_ID
                    mutable.update { it.copy(backendReady = ready, error = if (ready) it.error else "Assinaturas Google Play ainda não configuradas no servidor.") }
                }
                is ApiResult.Err -> mutable.update { it.copy(backendReady = false, error = result.message) }
            }
        }
        if (client.isReady) { loadProducts(); restore(); return }
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) { loadProducts(); restore() }
                else fail("Google Play indisponível. Instale o app pela Play Store e tente novamente.")
            }
            override fun onBillingServiceDisconnected() {
                connecting = false
                mutable.update { it.copy(loading = false, working = false, error = "Conexão com Google Play interrompida. Tente novamente.") }
            }
        })
    }

    private fun loadProducts() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(BuildConfig.PLAY_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS).build()
        )).build()
        client.queryProductDetailsAsync(params) { result, response ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) { fail("Não foi possível carregar os planos da Google Play."); return@queryProductDetailsAsync }
            details = response.productDetailsList.firstOrNull()
            val plans = listOf(BuildConfig.PLAY_MONTHLY_BASE_PLAN, BuildConfig.PLAY_ANNUAL_BASE_PLAN).mapNotNull { base ->
                // Only the base offer: no assumed trial or arbitrary promotion.
                val offer = details?.subscriptionOfferDetails?.firstOrNull { it.basePlanId == base && it.offerId == null } ?: return@mapNotNull null
                val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
                if (phase.billingPeriod != (if (base == BuildConfig.PLAY_ANNUAL_BASE_PLAN) "P1Y" else "P1M")) return@mapNotNull null
                val cadence = if (base == BuildConfig.PLAY_ANNUAL_BASE_PLAN) "ano" else "mês"
                PlayPlan(base, offer.offerToken, phase.formattedPrice + "/" + cadence,
                    "Renovação automática. Condições e valor final apresentados pela Google Play.")
            }
            mutable.update { it.copy(loading = false, plans = plans,
                selected = it.selected.takeIf { selected -> plans.any { p -> p.basePlanId == selected } } ?: plans.firstOrNull()?.basePlanId.orEmpty(),
                error = if (plans.isEmpty()) "Planos indisponíveis. Verifique as assinaturas cadastradas no Play Console." else it.error) }
        }
    }

    fun select(id: String) { mutable.update { it.copy(selected = id) } }

    fun buy(activity: Activity) {
        val s = mutable.value
        if (s.working) return
        if (!s.backendReady) { fail("Assinaturas Google Play ainda não disponíveis. Tente novamente mais tarde."); return }
        val userId = s.user?.id ?: run { fail("Entre na sua conta MemorIA antes de assinar."); return }
        if (s.user.subscriptionStatus in setOf("active", "authorized", "trialing", "pending", "paused")) {
            fail("Você já possui uma assinatura. Gerencie-a antes de contratar outro plano."); return
        }
        if (!client.isReady) { connect(); return }
        mutable.update { it.copy(working = true, error = null) }
        // Refresh ProductDetails just before checkout to avoid stale offer tokens.
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(BuildConfig.PLAY_PRODUCT_ID).setProductType(BillingClient.ProductType.SUBS).build()
        )).build()
        client.queryProductDetailsAsync(params) { result, response ->
            val product = response.productDetailsList.firstOrNull()
            val offer = product?.subscriptionOfferDetails?.firstOrNull { it.basePlanId == s.selected && it.offerId == null }
            if (result.responseCode != BillingClient.BillingResponseCode.OK || product == null || offer == null) {
                fail("Plano indisponível. Atualize os planos e tente novamente."); return@queryProductDetailsAsync
            }
            val account = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray()).joinToString("") { "%02x".format(it) }
            val flow = BillingFlowParams.newBuilder().setObfuscatedAccountId(account)
                .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(product).setOfferToken(offer.offerToken).build())).build()
            val launched = client.launchBillingFlow(activity, flow)
            if (launched.responseCode != BillingClient.BillingResponseCode.OK) handleResult(launched, null)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) { handleResult(result, purchases) }
    private fun handleResult(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> process(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> mutable.update { it.copy(working = false) }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore()
            else -> fail("Não foi possível concluir a compra na Google Play. Tente novamente.")
        }
    }

    fun restore() {
        if (!client.isReady) { connect(); return }
        mutable.update { it.copy(working = true) }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) process(purchases)
            else fail("Não foi possível restaurar as compras. Tente novamente.")
        }
    }

    private fun process(purchases: List<Purchase>) {
        viewModelScope.launch {
            verification.withLock {
                val relevant = purchases.filter { BuildConfig.PLAY_PRODUCT_ID in it.products }
                var message: String? = if (relevant.isEmpty()) "Nenhuma compra ativa encontrada nesta conta Google Play." else null
                for (purchase in relevant) {
                    val pending = purchase.purchaseState == Purchase.PurchaseState.PENDING
                    if (!pending && purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
                    // Claim pending tokens too, so RTDN can activate them while the app is closed.
                    when (val result = repo.verifyGooglePlayPurchase(purchase.purchaseToken)) {
                        is ApiResult.Ok -> message = if (result.value.active) "Assinatura confirmada pela Google Play." else if (pending) "Pagamento pendente. O Premium será ativado após a confirmação." else "Esta assinatura não está ativa."
                        is ApiResult.Err -> { fail(result.message); return@withLock }
                    }
                }
                refreshUser()
                mutable.update { it.copy(working = false, message = message) }
            }
        }
    }

    fun cancelLegacySubscription() {
        if (mutable.value.working || mutable.value.user?.subscriptionProvider == "googleplay") return
        mutable.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            when (val result = repo.cancelSubscription()) {
                is ApiResult.Ok -> { refreshUser(); mutable.update { it.copy(working = false, message = "Assinatura anterior cancelada.") } }
                is ApiResult.Err -> fail(result.message)
            }
        }
    }

    suspend fun refreshUser() {
        when (val result = repo.me()) {
            is ApiResult.Ok -> mutable.update { it.copy(user = result.value) }
            is ApiResult.Err -> mutable.update { it.copy(error = result.message) }
        }
    }
    fun consumeMessage() { mutable.update { it.copy(message = null, error = null) } }
    private fun fail(message: String) { mutable.update { it.copy(loading = false, working = false, error = message) } }
    override fun onCleared() { client.endConnection(); super.onCleared() }
}
