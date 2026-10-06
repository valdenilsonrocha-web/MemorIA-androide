package com.memoria.mobile.ui.plans

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.BuildConfig
import com.memoria.mobile.R
import com.memoria.mobile.ui.common.PageTitle
import com.memoria.mobile.ui.common.systemViewModel

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
fun PlansScreen(onBack: () -> Unit) {
    val vm = systemViewModel { repo, app, _ -> PlayBillingViewModel(repo, app) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var confirmLegacyCancel by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.restore() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.message, state.error) {
        (state.error ?: state.message)?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    if (confirmLegacyCancel) {
        AlertDialog(onDismissRequest = { confirmLegacyCancel = false },
            title = { Text("Cancelar assinatura anterior?") },
            text = { Text("Isso cancela a assinatura contratada anteriormente fora da Google Play.") },
            confirmButton = { TextButton(onClick = { confirmLegacyCancel = false; vm.cancelLegacySubscription() }) { Text("Confirmar cancelamento") } },
            dismissButton = { TextButton(onClick = { confirmLegacyCancel = false }) { Text("Manter assinatura") } })
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PageTitle("Planos", iconRes = R.drawable.ic_premium)
            Text("MemorIA Premium", style = MaterialTheme.typography.headlineSmall)
            Text("Desbloqueie os recursos Premium com uma assinatura pela Google Play.")
            PREMIUM_FEATURES.forEach { (feature, _) -> Text("✓ " + feature) }
            if (state.loading) CircularProgressIndicator()
            val active = state.user?.subscriptionStatus in setOf("active", "authorized", "trialing", "pending", "paused")
            if (!active) {
                state.plans.forEach { plan ->
                    Card(onClick = { vm.select(plan.basePlanId) }, enabled = !state.working) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text((if (plan.basePlanId == BuildConfig.PLAY_ANNUAL_BASE_PLAN) "Plano Anual" else "Plano Mensal") +
                                if (plan.basePlanId == state.selected) " ✓" else "", style = MaterialTheme.typography.titleMedium)
                            Text(plan.price, style = MaterialTheme.typography.headlineSmall)
                            Text(plan.terms)
                        }
                    }
                }
                Button(onClick = { context.activity()?.let(vm::buy) },
                    enabled = !state.working && state.backendReady && state.plans.isNotEmpty() && state.user?.id != null && context.activity() != null,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.working) "Confirmando…" else "Assinar com Google Play")
                }
            } else {
                Text(if (state.user?.subscriptionProvider == "googleplay") "Premium ativo pela Google Play." else "Você já possui uma assinatura Premium.")
            }
            if (active && state.user?.subscriptionProvider != "googleplay") {
                OutlinedButton(onClick = { confirmLegacyCancel = true }, enabled = !state.working) { Text("Cancelar assinatura anterior") }
            }
            Text("O pagamento é processado pela Google Play. Você pode gerenciar e cancelar a renovação na sua conta Google Play.")
            OutlinedButton(onClick = vm::restore, enabled = !state.working, modifier = Modifier.fillMaxWidth()) { Text("Restaurar compras") }
            run {
                OutlinedButton(onClick = {
                    val uri = Uri.parse("https://play.google.com/store/account/subscriptions").buildUpon()
                        .appendQueryParameter("sku", BuildConfig.PLAY_PRODUCT_ID).appendQueryParameter("package", context.packageName).build()
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }, modifier = Modifier.fillMaxWidth()) { Text("Gerenciar assinatura na Google Play") }
            }
            if (!state.backendReady && !state.loading) Text("Assinaturas Google Play aguardam configuração no servidor.")
            if (!state.loading) OutlinedButton(onClick = vm::connect) { Text("Atualizar planos") }
            TextButton(onClick = onBack) { Text("Voltar") }
        }
    }
}
