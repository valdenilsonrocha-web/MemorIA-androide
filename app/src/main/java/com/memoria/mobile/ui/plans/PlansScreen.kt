package com.memoria.mobile.ui.plans

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.ViewWeek
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.R
import com.memoria.mobile.ui.common.PageTitle
import com.memoria.mobile.ui.common.PageTopMessage
import com.memoria.mobile.ui.common.LoadingBox
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.theme.RedMiss
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Cores de `.premium-feature`, `.premium-badge-new` e `.plan-card.featured` (main.css).
private val FEATURE_BG = Color(0xFFFEF3C7)
private val FEATURE_TEXT = Color(0xFF92400E)
private val BADGE_NEW = Color(0xFFDC2626)
private val FEATURED_BORDER = Color(0xFFF59E0B)
private val CERT_BORDER = Color(0xFFE5E7EB)

private val DATE_BR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * "Planos" — the web `plansPage`, done natively and in the web's order: hero,
 * description, premium features, plan cards, payment method, payer data for the
 * invoice, hints, security certificates, the pay button, summary and status.
 *
 * The only structural difference is the card itself: the web hands that step
 * to the gateway's hosted checkout, the app collects it here and turns it into a
 * token on the device (see [CardFormSection]).
 */
@OptIn(ExperimentalLayoutApi::class)
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
        state.error?.let { snackbar.showSnackbar(com.memoria.mobile.ui.common.errorToast(it)); vm.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) { com.memoria.mobile.ui.common.MemoriaSnackbar(it) } },
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
            // `showPageTopGuide('plansPage')`: the animated text guide, prepended
            // ABOVE the page's h2.
            Column {
                PageTopMessage("Aqui você conhece os planos e ativa recursos extras do MemorIA.")
                PageTitle("Planos", iconRes = R.drawable.ic_premium)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                // A web abre esta página com a `plans-hero.png` (`.premium-hero-image`).
                Image(
                    painter = painterResource(R.drawable.plans_hero),
                    contentDescription = "Tela de assinatura do MemorIA com benefícios premium",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .heightIn(max = 220.dp)
                        .shadow(10.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, Color(0x140F172A), RoundedCornerShape(18.dp)),
                )
            }

            Text(
                "Ative com cartão de crédito e aproveite ${state.trialDays} dias grátis para testar " +
                    "todos os recursos premium. Após o período de teste, a cobrança começa " +
                    "automaticamente no plano escolhido.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PREMIUM_FEATURES.forEach { (feature, isNew) -> FeaturePill(feature, isNew) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PlanCard(
                    title = "Plano Mensal",
                    price = state.monthlyPrice,
                    note = null,
                    featured = false,
                    selected = state.selected == BillingCycle.MONTHLY,
                    onSelect = { vm.select(BillingCycle.MONTHLY) },
                )
                PlanCard(
                    title = "Plano Anual",
                    price = state.annualPrice,
                    note = state.annualNote,
                    featured = true,
                    selected = state.selected == BillingCycle.ANNUAL,
                    onSelect = { vm.select(BillingCycle.ANNUAL) },
                )
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Forma de pagamento:", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    PaymentMethodOption(Icons.Filled.CreditCard, "Cartão de crédito", active = true, enabled = true) {}
                    PaymentMethodOption(Icons.Filled.QrCode, "Pix (em breve)", active = false, enabled = false) {}
                    PaymentMethodOption(Icons.Filled.ViewWeek, "Boleto (em breve)", active = false, enabled = false) {}

                    if (!state.hasSubscription) {
                        InvoicePayerSection(state.payer, vm::onPayer)
                        CardFormSection(state, vm)
                    }

                    Text(
                        "Você autoriza o cartão agora no checkout seguro e recebe ${state.trialDays} dias " +
                            "grátis. A cobrança do plano começa após o teste.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "  Segurança: o MemorIA não coleta nem armazena número do cartão, CVV ou " +
                                "validade. O pagamento é processado no checkout seguro do ${state.gatewayName}.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SecurityCertificates(state.gatewayName)

                    if (!state.hasSubscription) {
                        Button(
                            onClick = vm::subscribe,
                            enabled = !state.working,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.working) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp).padding(end = 4.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(Icons.Filled.CreditCard, contentDescription = null)
                            }
                            // Web `updatePaymentMethodUI`: " Pagar com Cartao" (sic).
                            Text("  Pagar com Cartão")
                        }
                    }

                    Text(
                        state.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )

                    val active = state.hasSubscription
                    Text(
                        statusMessage(state),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Extra do app: o cancelamento é feito aqui mesmo.
                    if (active) {
                        OutlinedButton(
                            onClick = { confirmCancel = true },
                            enabled = !state.working,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Cancelar assinatura", color = RedMiss)
                        }
                    }
                }
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

/** The web's `updatePremiumStatusMessage`. */
private fun statusMessage(state: PlansUiState): String {
    if (!state.hasSubscription) return "Premium inativo no momento."
    val plan = if (state.savedCycle == "annual") "Anual" else "Mensal"
    val last4 = state.savedCardLast4?.let { " • Cartão final $it" }.orEmpty()
    val trial = state.savedTrialEndsAtMillis
        ?.takeIf { it > System.currentTimeMillis() }
        ?.let {
            " • Teste grátis até " +
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_BR)
        }
        .orEmpty()
    return "Premium ativo • Plano $plan • Cartão$last4$trial"
}

@Composable
private fun FeaturePill(text: String, isNew: Boolean) {
    Surface(color = FEATURE_BG, shape = RoundedCornerShape(999.dp)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = FEATURE_TEXT, modifier = Modifier.size(18.dp))
            Text(
                " $text",
                color = FEATURE_TEXT,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (isNew) {
                Box(
                    Modifier
                        .padding(start = 6.dp)
                        .background(BADGE_NEW, RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("NOVO", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    note: String?,
    featured: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val borderColor = when {
        featured -> FEATURED_BORDER
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Card(
        Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect),
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.padding(start = 8.dp)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    price,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (note != null) {
                    Text(
                        note,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodOption(
    icon: ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            2.dp,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        color = when {
            active -> MaterialTheme.colorScheme.background // --bg-secondary
            !enabled -> Color(0xFFF3F4F6)
            else -> Color.White
        },
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = active, onClick = onClick, enabled = enabled)
            Icon(icon, contentDescription = null)
            Text("  $label", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun InvoicePayerSection(payer: InvoicePayer, onPayer: ((InvoicePayer) -> InvoicePayer) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("  Dados do pagador para nota fiscal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            "Esses dados podem ser de outra pessoa (ex.: filho pagador e pai usuário do app).",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PayerField("Nome completo ou razão social *", "Nome do pagador", payer.legalName) { v ->
            onPayer { it.copy(legalName = v) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PayerField("CPF ou CNPJ *", "Somente números", payer.document, KeyboardType.Number, Modifier.weight(1f)) { v ->
                onPayer { it.copy(document = v.filter(Char::isDigit).take(14)) }
            }
            PayerField("Telefone *", "DDD + número", payer.phone, KeyboardType.Number, Modifier.weight(1f)) { v ->
                onPayer { it.copy(phone = v.filter(Char::isDigit).take(15)) }
            }
        }
        PayerField("E-mail para nota fiscal *", "pagador@email.com", payer.email, KeyboardType.Email) { v ->
            onPayer { it.copy(email = v) }
        }
        PayerField("Logradouro *", "Rua, avenida, etc.", payer.addressLine1) { v ->
            onPayer { it.copy(addressLine1 = v) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PayerField("Número *", "123", payer.addressNumber, modifier = Modifier.weight(1f)) { v ->
                onPayer { it.copy(addressNumber = v) }
            }
            PayerField("Bairro *", "Centro", payer.neighborhood, modifier = Modifier.weight(1f)) { v ->
                onPayer { it.copy(neighborhood = v) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PayerField("Cidade *", "São Paulo", payer.city, modifier = Modifier.weight(1f)) { v ->
                onPayer { it.copy(city = v) }
            }
            PayerField("UF *", "SP", payer.state, modifier = Modifier.weight(1f)) { v ->
                onPayer { it.copy(state = v.take(2).uppercase()) }
            }
        }
        PayerField("CEP *", "Somente números", payer.zipCode, KeyboardType.Number) { v ->
            onPayer { it.copy(zipCode = v.filter(Char::isDigit).take(8)) }
        }
    }
}

@Composable
private fun PayerField(
    label: String,
    placeholder: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
    )
}

/** The web's `.security-certificates` block, with the PCI DSS `<details>`. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SecurityCertificates(gatewayName: String) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("  Certificações e segurança", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CertItem(Icons.Filled.Lock, "Criptografia SSL/TLS")
            CertItem(Icons.Filled.WorkspacePremium, "PCI DSS via $gatewayName")
            CertItem(Icons.Filled.VerifiedUser, "Conformidade LGPD")
        }
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, CERT_BORDER, RoundedCornerShape(8.dp))
                .clickable { open = !open }
                .padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("  Saiba mais sobre PCI DSS", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            }
            AnimatedVisibility(open) {
                Text(
                    "PCI DSS é um padrão internacional de segurança para pagamentos com cartão. No " +
                        "MemorIA, os dados sensíveis do cartão não passam pelo nosso servidor; o pagamento " +
                        "é feito no checkout seguro do $gatewayName.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CertItem(icon: ImageVector, label: String) {
    Row(
        Modifier
            .border(1.dp, CERT_BORDER, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("  $label") } },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
