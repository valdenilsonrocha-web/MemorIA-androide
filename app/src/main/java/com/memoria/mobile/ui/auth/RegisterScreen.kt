package com.memoria.mobile.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoria.mobile.ui.common.repoViewModel
import com.memoria.mobile.ui.health.SelectField

/** As opções do `<select id="registerGender">` da web, na mesma ordem. */
private val GENDER_OPTIONS = listOf(
    "not_informed" to "Prefiro não informar",
    "female" to "Feminino",
    "male" to "Masculino",
    "other" to "Outro",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegistered: () -> Unit,
    onBack: () -> Unit,
    onOpenPolicy: () -> Unit,
) {
    val vm = repoViewModel { AuthViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    var name by rememberSaveable { mutableStateOf("") }
    var cpf by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var lgpdConsent by rememberSaveable { mutableStateOf(false) }
    var susCard by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("not_informed") }

    Scaffold(
        // The `authModal` header in register mode: `fa-user` + "Cadastro", as the
        // login screen shows "Login". Leaving is "Cancelar" / "Faça login" (or back).
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
                Text("Cadastro", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Mesma ordem e mesmos rótulos do `registerForm` da web.
            OutlinedTextField(
                value = cpf,
                onValueChange = { cpf = it },
                label = { Text("CPF *") },
                placeholder = { Text("000.000.000-00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome Completo *") },
                placeholder = { Text("Seu nome completo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha * (mínimo ${AuthViewModel.REGISTER_MIN_PASSWORD} caracteres)") },
                placeholder = { Text("Mínimo ${AuthViewModel.REGISTER_MIN_PASSWORD} caracteres") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                placeholder = { Text("seu@email.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Telefone") },
                placeholder = { Text("(00) 00000-0000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            // `registerSusCard`: maxlength 15.
            OutlinedTextField(
                value = susCard,
                onValueChange = { susCard = it.take(15) },
                label = { Text("Cartão SUS") },
                placeholder = { Text("Número do cartão SUS") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            // `registerGender` — o `<select>` da web, com as mesmas opções.
            SelectField(
                options = GENDER_OPTIONS,
                selected = gender,
                onSelect = { gender = it },
                label = "Sexo",
            )

            // The consent is now the user's, not a constant in the request: they
            // tick it, and they can read what they are agreeing to first.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { lgpdConsent = !lgpdConsent },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = lgpdConsent, onCheckedChange = { lgpdConsent = it })
                Text(
                    "Aceito compartilhar meus dados com o sistema de saúde pública para fins de " +
                        "monitoramento e estatísticas (LGPD)",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(onClick = onOpenPolicy) {
                Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ler Política de Privacidade")
            }

            if (state.error != null) {
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            // `.form-actions` da web: Cancelar + Cadastrar lado a lado.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onBack,
                    enabled = !state.loading,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text("Cancelar")
                }
                Button(
                    // Como na web, o botão fica ativo: sem o consentimento o
                    // cadastro responde com a mensagem em vez de ficar mudo.
                    onClick = {
                        vm.register(
                            cpf, name, password, email, phone, lgpdConsent, onRegistered,
                            susCard = susCard, gender = gender,
                        )
                    },
                    enabled = !state.loading,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Cadastrar")
                    }
                }
            }

            // "Já tem conta? Faça login"
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                            append("Já tem conta? ")
                        }
                        withStyle(
                            SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
                        ) { append("Faça login") }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
