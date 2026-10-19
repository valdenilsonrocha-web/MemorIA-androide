package com.memoria.mobile.ui.common

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A contact the phone knows has WhatsApp, with the number in the form the app stores. */
data class WhatsAppContact(val name: String, val phone: String)

/**
 * The phone's WhatsApp contacts. WhatsApp adds a row of its own MIME type to the
 * address book for every contact that has an account, holding the WhatsApp id
 * (`5511987654321@s.whatsapp.net`), so the number is exactly the one WhatsApp
 * delivers to — not whichever of the contact's numbers happens to be first.
 */
object WhatsAppContacts {

    private val MIME_TYPES = arrayOf(
        "vnd.android.cursor.item/vnd.com.whatsapp.profile",
        "vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile",
    )

    fun load(context: Context): List<WhatsAppContact> {
        val found = linkedMapOf<String, WhatsAppContact>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.DISPLAY_NAME, ContactsContract.Data.DATA1),
            "${ContactsContract.Data.MIMETYPE} IN (?, ?)",
            MIME_TYPES,
            "${ContactsContract.Data.DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0).orEmpty().trim()
                val phone = fromJid(cursor.getString(1)) ?: continue
                found.putIfAbsent(phone, WhatsAppContact(name.ifBlank { phone }, phone))
            }
        }
        return found.values.toList()
    }

    /** `5511987654321@s.whatsapp.net` → `11987654321`; anything else → null. */
    internal fun fromJid(jid: String?): String? {
        val digits = Regex("^(\\d{8,15})@").find(jid.orEmpty())?.groupValues?.get(1) ?: return null
        return forField("+$digits")
    }

    /**
     * A number as the phone fields expect it: a Brazilian number as DDD + local
     * number (the app adds 55 itself), a foreign one with its `+` country code.
     */
    internal fun forField(raw: String): String {
        val international = raw.trim().startsWith("+")
        val digits = raw.filter { it.isDigit() }.trimStart('0')
        if (!international && digits.length <= 11) return digits
        if (digits.startsWith("55") && digits.length in 12..13) return digits.drop(2)
        return "+$digits"
    }

    /** Name and number of the row picked in the system contact picker. */
    fun fromPickerResult(context: Context, uri: Uri): WhatsAppContact? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val number = c.getString(1).orEmpty()
            if (number.isBlank()) null else WhatsAppContact(c.getString(0).orEmpty(), forField(number))
        }
    }.getOrNull()
}

/**
 * "Importar dos contatos do WhatsApp": lists only the contacts that have
 * WhatsApp, so a caregiver's alert goes to a number that can receive it.
 * Without the contacts permission (or with no WhatsApp contacts on the phone)
 * it falls back to the system picker, which needs no permission.
 * Nothing is uploaded except the one contact the user chooses.
 */
@Composable
fun WhatsAppContactImportButton(
    onPicked: (name: String, phone: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<WhatsAppContact>?>(null) }
    var loading by remember { mutableStateOf(false) }

    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            WhatsAppContacts.fromPickerResult(context, uri)?.let { onPicked(it.name, it.phone) }
        }
    }
    val openSystemPicker = {
        runCatching {
            systemPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            openSystemPicker()
            return@rememberLauncherForActivityResult
        }
        loading = true
        scope.launch {
            val list = withContext(Dispatchers.IO) { runCatching { WhatsAppContacts.load(context) }.getOrDefault(emptyList()) }
            loading = false
            if (list.isEmpty()) openSystemPicker() else contacts = list
        }
    }

    OutlinedButton(
        onClick = { permission.launch(Manifest.permission.READ_CONTACTS) },
        enabled = !loading,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
        }
        Text("  Importar dos contatos do WhatsApp")
    }

    contacts?.let { list ->
        WhatsAppContactDialog(
            contacts = list,
            onDismiss = { contacts = null },
            onPick = { picked ->
                contacts = null
                onPicked(picked.name, picked.phone)
            },
        )
    }
}

@Composable
private fun WhatsAppContactDialog(
    contacts: List<WhatsAppContact>,
    onDismiss: () -> Unit,
    onPick: (WhatsAppContact) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val digits = query.filter { it.isDigit() }
    val shown = contacts.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) ||
            (digits.isNotEmpty() && it.phone.contains(digits))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contatos do WhatsApp") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Só o contato que escolher é guardado no MemorIA.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Procurar nome ou número") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.phone }) { contact ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(contact) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(contact.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                contact.phone,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
                if (shown.isEmpty()) Text("Nenhum contato encontrado.", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
