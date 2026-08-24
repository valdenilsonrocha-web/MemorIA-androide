package com.memoria.mobile.ui.plans

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.SectionCard
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.theme.GreenOk
import com.memoria.mobile.ui.theme.RedMiss

/**
 * "Planos" — the web `plansPage`, done natively.
 *
 * Everything is native and talks only to the MemorIA backend: plan choice, card
 * form, subscription status and cancellation. There is no browser, no WebView and
 * no page from the website anywhere in the flow.
 *
 * The card is exchanged for a Mercado Pago token on the device, so the number
 * never reaches the MemorIA backend and is never stored.
 */
@Composable
fun PlansScreen(onBack: () -> Unit) {
    val vm = repoViewModel { PlansViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmCancel by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        topBar = { BackTopBar("Planos", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        if (state.loading && state.user == null) {
            LoadingBox(Modifier.padding(inner))
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.isPremium) {
                PremiumStatusCard(
                    status = state.subscriptionStatus,
                    working = state.working,
                    onCancel = { confirmCancel = true },
                )
            }

            SectionCard("O que o Premium libera", icon = Icons.Filled.WorkspacePremium) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Ative com cartão de crédito e aproveite ${state.trialDays} dias grátis para " +
                            "testar todos os recursos premium. Depois do teste, a cobrança começa " +
                            "automaticamente no plano escolhido.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PREMIUM_FEATURES.forEach { feature ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = GreenOk)
                            Text("  $feature", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            if (!state.isPremium) {
                SectionCard("Escolha o plano") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlanCard(
                            title = "Plano Mensal",
                            price = state.monthlyPrice,
                            note = "Cancele quando quiser, aqui mesmo no app.",
                            selected = state.selected == BillingCycle.MONTHLY,
                            onSelect = { vm.select(BillingCycle.MONTHLY) },
                        )
                        PlanCard(
                            title = "Plano Anual",
                            price = state.annualPrice,
                            note = "${state.trialDays} dias grátis. Melhor custo por mês.",
                            selected = state.selected == BillingCycle.ANNUAL,
                            onSelect = { vm.select(BillingCycle.ANNUAL) },
                        )
                    }
                }

                CardFormSection(state, vm)
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancelar assinatura?") },
            text = {
                Text(
                    "O Premium fica ativo até o fim do período já pago e depois a conta volta ao " +
                        "plano gratuito. Você pode assinar de novo quando quiser.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; vm.cancelSubscription() }) {
                    Text("Cancelar assinatura", color = RedMiss)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Manter Premium") }
            },
        )
    }
}

@Composable
private fun PremiumStatusCard(status: String?, working: Boolean, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = GreenOk)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Você é Premium", style = MaterialTheme.typography.titleLarge)
                    Text(
                        statusLabel(status),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(
                onClick = onCancel,
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancelar assinatura", color = RedMiss)
            }
        }
    }
}

private fun statusLabel(status: String?): String = when (status) {
    "authorized" -> "Assinatura ativa."
    "pending" -> "Pagamento em processamento."
    "cancelled" -> "Assinatura cancelada — o acesso vai até o fim do período pago."
    "paused" -> "Assinatura pausada."
    null, "" -> "Todos os recursos liberados."
    else -> "Situação da assinatura: $status"
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    note: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onSelect),
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.padding(start = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    price,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    note,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
