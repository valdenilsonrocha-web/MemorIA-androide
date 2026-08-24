package com.memoria.mobile.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.memoria.mobile.ui.common.SectionCard

/**
 * The card form, native Compose — no browser, no WebView, no page from the site.
 *
 * What is typed here goes straight to Mercado Pago to be exchanged for a token;
 * neither the MemorIA backend nor this device ever stores it. The fields are
 * deliberately plain and large: the person filling them in is often elderly.
 */
@Composable
fun CardFormSection(state: PlansUiState, vm: PlansViewModel) {
    SectionCard("Dados do cartão", icon = Icons.Filled.CreditCard) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.payerEmail,
                onValueChange = vm::onPayerEmail,
                label = { Text("E-mail para a cobrança") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.cardNumber,
                onValueChange = vm::onCardNumber,
                label = { Text("Número do cartão") },
                placeholder = { Text("0000 0000 0000 0000") },
                singleLine = true,
                visualTransformation = CardNumberTransformation(),
                isError = state.cardNumberError,
                supportingText = if (state.cardNumberError) {
                    { Text("Confira o número do cartão.") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.holderName,
                onValueChange = vm::onHolderName,
                label = { Text("Nome como está no cartão") },
                singleLine = true,
                isError = state.holderNameError,
                supportingText = if (state.holderNameError) {
                    { Text("Digite o nome completo impresso no cartão.") }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.expiry,
                    onValueChange = vm::onExpiry,
                    label = { Text("Validade") },
                    placeholder = { Text("MM/AA") },
                    singleLine = true,
                    visualTransformation = ExpiryTransformation(),
                    isError = state.expiryError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.securityCode,
                    onValueChange = vm::onSecurityCode,
                    label = { Text("CVV") },
                    placeholder = { Text("123") },
                    singleLine = true,
                    isError = state.securityCodeError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.expiryError || state.securityCodeError) {
                Text(
                    "Confira a validade (MM/AA) e o código de segurança.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = state.holderDocument,
                onValueChange = vm::onHolderDocument,
                label = { Text("CPF do titular") },
                placeholder = { Text("000.000.000-00") },
                singleLine = true,
                visualTransformation = CpfTransformation(),
                isError = state.holderDocumentError,
                supportingText = if (state.holderDocumentError) {
                    { Text("CPF inválido.") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "  Os dados do cartão vão cifrados direto para o Mercado Pago. O MemorIA " +
                        "recebe apenas um código de autorização — nunca o número do seu cartão, " +
                        "e nada fica guardado no celular.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
                }
                Text(if (state.working) "Processando..." else "Assinar Premium")
            }

            Text(
                "Cobrança processada pelo Mercado Pago. Você pode cancelar a qualquer momento, " +
                    "aqui mesmo no app.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
