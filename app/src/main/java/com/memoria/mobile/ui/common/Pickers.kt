package com.memoria.mobile.ui.common

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val ISO_LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
private val DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

/**
 * Read-only field that opens the platform date then time dialogs — the mobile
 * equivalent of the web form's `<input type="datetime-local">`.
 *
 * [value] and the value passed to [onChange] are `yyyy-MM-dd'T'HH:mm`, the same
 * string `datetime-local` produces, so records stay interchangeable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val parsed = runCatching { LocalDateTime.parse(value, ISO_LOCAL) }.getOrNull()

    val open = {
        val base = parsed ?: LocalDateTime.now()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                // DatePicker months are 0-based; LocalDate months are 1-based.
                val date = LocalDate.of(year, month + 1, dayOfMonth)
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        onChange(LocalDateTime.of(date, LocalTime.of(hour, minute)).format(ISO_LOCAL))
                    },
                    base.hour,
                    base.minute,
                    true,
                ).show()
            },
            base.year,
            base.monthValue - 1,
            base.dayOfMonth,
        ).show()
    }

    // The field is read-only, so it never takes focus; a transparent overlay
    // makes the whole control open the dialogs, not just the trailing icon.
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = parsed?.format(DISPLAY) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Toque para escolher") },
            trailingIcon = {
                Icon(Icons.Filled.Event, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClick = open)
                .semantics { contentDescription = "Escolher data e hora" },
        )
    }
}

/** Formats a `yyyy-MM-dd'T'HH:mm` string for display, or returns it unchanged. */
fun formatLocalDateTime(raw: String): String =
    runCatching { LocalDateTime.parse(raw, ISO_LOCAL).format(DISPLAY) }.getOrDefault(raw)
