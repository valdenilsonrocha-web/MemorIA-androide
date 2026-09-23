package com.memoria.mobile.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * The card fields, native Compose — no browser, no WebView, no page from the site.
 *
 * The web sends the user to the gateway's hosted checkout for this step; the app
 * collects the card itself and exchanges it for a token on the device, so the
 * number never reaches the MemorIA backend and is never stored.
 */
@Composable
fun CardFormSection(state: PlansUiState, vm: PlansViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                "  Dados do cartão",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        OutlinedTextField(
            value = state.cardNumber,
            onValueChange = vm::onCardNumber,
            label = { Text("Número do cartão *") },
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
            label = { Text("Nome do titular *") },
            placeholder = { Text("Nome como está no cartão") },
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
                label = { Text("Validade *") },
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
                label = { Text("CVV *") },
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
            label = { Text("CPF do titular *") },
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
    }
}
