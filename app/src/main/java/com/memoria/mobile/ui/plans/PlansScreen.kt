package com.memoria.mobile.ui.plans

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.SectionCard
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.theme.GreenOk

/**
 * "Planos" — the web `plansPage`.
 *
 * Card details are never collected here: the button hands the subscription over
 * to the MemorIA site, which runs the gateway's own secure checkout.
 */
@Composable
fun PlansScreen(onBack: () -> Unit, checkoutUrl: String) {
    val vm = repoViewModel { PlansViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(topBar = { BackTopBar("Planos", onBack) }) { inner ->
        if (state.loading && state.config == null && state.user == null) {
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
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = GreenOk)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("Você já é Premium", style = MaterialTheme.typography.titleLarge)
                            Text(
                                state.user?.subscriptionStatus?.let { "Assinatura: $it" }
                                    ?: "Todos os recursos liberados.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SectionCard("O que o Premium libera", icon = Icons.Filled.WorkspacePremium) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Ative com cartão de crédito e aproveite 7 dias grátis para testar todos os " +
                            "recursos premium. Após o período de teste, a cobrança começa automaticamente " +
                            "no plano escolhido.",
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

            SectionCard("Escolha o plano") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PlanCard(
                        title = "Plano Mensal",
                        price = state.monthlyPrice,
                        note = "Cancele quando quiser.",
                        selected = state.selected == BillingCycle.MONTHLY,
                        onSelect = { vm.select(BillingCycle.MONTHLY) },
                    )
                    PlanCard(
                        title = "Plano Anual",
                        price = state.annualPrice,
                        note = "7 dias grátis e depois cobrança anual. Melhor custo por mês.",
                        selected = state.selected == BillingCycle.ANNUAL,
                        onSelect = { vm.select(BillingCycle.ANNUAL) },
                    )
                    Text(
                        "Você autoriza o cartão no checkout seguro do site e recebe 7 dias grátis. " +
                            "A cobrança do plano começa após o teste.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl)))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Text(if (state.isPremium) "  Gerenciar assinatura" else "  Assinar no site MemorIA")
                    }
                    state.error?.let {
                        Text(
                            "Não foi possível confirmar os preços no servidor ($it). Os valores acima " +
                                "podem estar desatualizados — confirme no site antes de assinar.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
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
