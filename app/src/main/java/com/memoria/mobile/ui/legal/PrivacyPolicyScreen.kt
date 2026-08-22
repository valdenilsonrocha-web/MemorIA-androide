package com.memoria.mobile.ui.legal

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.memoria.mobile.ui.common.BackTopBar

/** Contact for privacy matters, as published in the web policy. */
const val PRIVACY_CONTACT_EMAIL = "contato@brasmaster.com.br"

/**
 * The clauses, in the order and wording the web modal uses. Kept as data so the
 * screen and any future consent sheet render the same text — a policy that says
 * two different things in two places is worse than one that says nothing.
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
    "Contato de privacidade" to PRIVACY_CONTACT_EMAIL,
)

/**
 * "Política de Privacidade e LGPD" — the web `lgpdPolicyModal`.
 *
 * Reachable from the sign-up form as well as from Settings, because a consent
 * box the user cannot read before ticking is not informed consent.
 */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(topBar = { BackTopBar("Política de Privacidade", onBack) }) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Como o MemorIA trata os seus dados, nos termos da Lei Geral de Proteção de " +
                    "Dados (Lei 13.709/2018).",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            CLAUSES.forEach { (title, body) ->
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$title: ") }
                        append(body)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$PRIVACY_CONTACT_EMAIL"))
                                .putExtra(Intent.EXTRA_SUBJECT, "MemorIA — privacidade e LGPD"),
                        )
                    }.onFailure {
                        if (it !is ActivityNotFoundException) throw it
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Falar com o encarregado de dados")
            }

            Text(
                "Você pode exercer os seus direitos diretamente no app, em Mais > Privacidade " +
                    "e dados: exportar tudo o que guardamos, revogar o consentimento ou apagar " +
                    "a conta.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
