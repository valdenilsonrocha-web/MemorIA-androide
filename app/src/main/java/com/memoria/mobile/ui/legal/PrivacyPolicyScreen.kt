package com.memoria.mobile.ui.legal

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.memoria.mobile.ui.common.BackTopBar
import com.memoria.mobile.ui.common.MemoriaToast

/** Contact for privacy matters, as published in the web policy. */
const val PRIVACY_CONTACT_EMAIL = "contato@brasmaster.com.br"

/** CNPJ of the data controller, as published in the public policy. */
const val PRIVACY_CONTROLLER_CNPJ = "51.987.719/0001-07"

/** The full public policy — the same address registered in the Play Console. */
const val PRIVACY_POLICY_URL = "https://35.247.217.66.nip.io/privacidade.html"

/** Public page explaining how to delete the account and its data. */
const val ACCOUNT_DELETION_URL = "https://35.247.217.66.nip.io/excluir-conta.html"

/** Title of the web `lgpdPolicyModal`. */
const val LGPD_POLICY_TITLE = "Política de Privacidade e LGPD"

/**
 * A short summary of the public policy. Kept as data so the screen and the
 * Settings dialog render the same text — a policy that says two different
 * things in two places is worse than one that says nothing. The full text lives
 * only at [PRIVACY_POLICY_URL], so the app never carries a stale copy of it.
 */
private val CLAUSES: List<Pair<String, String>> = listOf(
    "Controlador dos dados" to "CNPJ $PRIVACY_CONTROLLER_CNPJ.",
    "Finalidade" to
        "lembretes de medicação, histórico de doses, relatórios, avisos aos seus contatos e " +
        "sincronização da sua conta com o servidor do MemorIA.",
    "Dados tratados" to
        "cadastro (nome, CPF, e-mail, telefone, cidade/UF e, se informados, Cartão SUS e sexo), " +
        "medicamentos, histórico de doses, " +
        "fotos de receitas, contatos de cuidadores e as informações de saúde que você registrar.",
    "Agenda do celular" to
        "com a sua permissão, o app lê os contatos do celular só para listar quem tem WhatsApp " +
        "quando você toca em \"Importar dos contatos do WhatsApp\". A lista não sai do celular; " +
        "apenas o contato que você escolher é guardado.",
    "Conversa por voz" to
        "ao iniciar uma conversa, o áudio do microfone é enviado pelo backend do MemorIA ao Google Gemini " +
        "para gerar respostas de voz e transcrições. O áudio não é guardado. O texto transcrito da conversa " +
        "(o que você e a assistente disserem) fica guardado na sua conta no servidor do MemorIA, entra na " +
        "exportação dos seus dados, pode ser lido pela equipe do MemorIA para suporte e segurança e é apagado " +
        "quando você exclui a conta. O microfone é encerrado ao sair da tela ou ir para segundo plano. Os dados " +
        "necessários dos medicamentos e histórico podem ser consultados pela assistente. Cadastros exigem sua confirmação na tela.",
    "Base legal" to
        "consentimento do titular e execução dos serviços solicitados no aplicativo.",
    "Compartilhamento" to
        "somente quando necessário para funcionalidades que você ativar, como avisos por " +
        "WhatsApp e e-mail aos seus contatos.",
    "Pagamentos" to
        "a assinatura Premium é processada pela Google Play; o MemorIA não recebe os dados " +
        "do seu cartão.",
    "Seus direitos" to
        "confirmar o tratamento, acessar, corrigir e exportar seus dados, revogar o " +
        "consentimento e excluir a conta — no app, em Configurações > Privacidade e exclusão " +
        "da conta.",
    "Retenção" to
        "os dados ficam guardados enquanto a conta estiver ativa ou até você pedir a exclusão.",
    "Contato de privacidade" to "$PRIVACY_CONTACT_EMAIL.",
)

/** Opens a public page in the browser; a phone without one gets a toast, not a crash. */
private fun openPublicPage(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        MemoriaToast.show("❌ Não foi possível abrir o navegador.")
    }
}

@Composable
private fun PolicySummary() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Este é um resumo. A política de privacidade completa está publicada no site do MemorIA.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = { openPublicPage(context, PRIVACY_POLICY_URL) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Description, contentDescription = null)
            Text("  Ler a política completa")
        }
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
            onClick = { openPublicPage(context, ACCOUNT_DELETION_URL) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Como excluir a conta e os dados")
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
            PolicySummary()
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Fechar")
            }
        }
    }
}

/** The same summary, opened in place by Settings' "Ver Política de Privacidade". */
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
                PolicySummary()
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Fechar") }
        },
    )
}
