package com.memoria.mobile.ui.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.memoria.mobile.ui.common.BackTopBar

/** Contact for privacy matters, as published in the web policy. */
const val PRIVACY_CONTACT_EMAIL = "contato@brasmaster.com.br"

/** Title of the web `lgpdPolicyModal`. */
const val LGPD_POLICY_TITLE = "Política de Privacidade e LGPD"

/**
 * The clauses, in the order and wording the web modal uses. Kept as data so the
 * screen and the Settings dialog render the same text — a policy that says two
 * different things in two places is worse than one that says nothing.
 */
private val CLAUSES: List<Pair<String, String>> = listOf(
    "Controlador de dados" to "MemorIA.",
    "Finalidade" to
        "lembretes de medicação, histórico de uso, recursos de segurança e sincronização " +
        "opcional em nuvem.",
    "Base legal" to
        "consentimento do titular e execução dos serviços solicitados no aplicativo.",
    "Dados tratados" to
        "cadastro, dados de medicação, histórico de adesão, contatos de emergência e " +
        "preferências de uso.",
    "Compartilhamento" to
        "somente quando necessário para funcionalidades ativadas por você (ex.: integrações, " +
        "sincronização e alertas).",
    "Direitos do titular" to
        "confirmação de tratamento, acesso, correção, revogação do consentimento e " +
        "solicitação de exclusão de dados.",
    "Retenção" to
        "os dados permanecem enquanto a conta estiver ativa ou até solicitação de exclusão.",
    "Contato de privacidade" to "$PRIVACY_CONTACT_EMAIL.",
)

@Composable
private fun PolicyClauses() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CLAUSES.forEach { (title, body) ->
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$title: ") }
                    append(body)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * "Política de Privacidade e LGPD" — the web `lgpdPolicyModal`, as a full screen.
 *
 * Reachable from the sign-up form as well as from the Privacy screen, because a
 * consent box the user cannot read before ticking is not informed consent.
 */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(LGPD_POLICY_TITLE, onBack) }) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PolicyClauses()
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Fechar")
            }
        }
    }
}

/** The same modal, opened in place by Settings' "Ver Política de Privacidade". */
@Composable
fun LgpdPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Description, contentDescription = null)
                Text("  $LGPD_POLICY_TITLE")
            }
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                PolicyClauses()
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Fechar") }
        },
    )
}
