package com.memoria.mobile.ui.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * O `<select>` da web: um campo só de leitura que abre a lista de opções.
 *
 * Feito à mão com [DropdownMenu] em vez de `ExposedDropdownMenuBox` porque a
 * assinatura de `menuAnchor` muda entre versões do Material 3 — assim compila
 * igual em qualquer BOM. Usado por Saúde, Relatórios e Meus Médicos.
 */
@Composable
fun SelectField(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    accessibilityLabel: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: options.firstOrNull()?.second.orEmpty()

    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = label?.let { { Text(it) } },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // O campo é só de leitura e nunca recebe foco; esta camada transparente
        // faz o controlo inteiro abrir a lista, como o `<select>` da web.
        Box(
            Modifier
                .matchParentSize()
                .clickable { expanded = true }
                .semantics { contentDescription = accessibilityLabel ?: label ?: current },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}
