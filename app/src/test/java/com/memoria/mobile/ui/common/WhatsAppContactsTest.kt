package com.memoria.mobile.ui.common

import com.memoria.mobile.ui.meds.MED_FORM_DEFS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsAppContactsTest {

    @Test
    fun `a WhatsApp id becomes the DDD plus number the phone fields expect`() {
        assertEquals("11987654321", WhatsAppContacts.fromJid("5511987654321@s.whatsapp.net"))
        // DDD 55 (Rio Grande do Sul): only the country code is removed.
        assertEquals("5599998888", WhatsAppContacts.fromJid("555599998888@s.whatsapp.net"))
        assertEquals("+351912345678", WhatsAppContacts.fromJid("351912345678@s.whatsapp.net"))
    }

    @Test
    fun `anything that is not a person's WhatsApp id is ignored`() {
        assertNull(WhatsAppContacts.fromJid(null))
        assertNull(WhatsAppContacts.fromJid("Mensagem para +55 11 98765-4321"))
        assertNull(WhatsAppContacts.fromJid("123@s.whatsapp.net"))
    }

    @Test
    fun `numbers from the address book keep the app's format`() {
        assertEquals("11987654321", WhatsAppContacts.forField("(11) 98765-4321"))
        assertEquals("11987654321", WhatsAppContacts.forField("+55 11 98765-4321"))
        assertEquals("11987654321", WhatsAppContacts.forField("011 98765-4321"))
        assertEquals("+351912345678", WhatsAppContacts.forField("+351 912 345 678"))
    }

    @Test
    fun `every pharmaceutical form of the site exists in the app, in the same order`() {
        val site = listOf(
            "comprimido", "capsula", "liquido", "locao", "spray", "pomada", "gotas", "gel", "supositorio",
            "injecao", "creme", "po", "adesivo", "espuma", "inalador", "goma", "anel", "cha", "erva", "ampola",
            "adesivo_bucal", "caneta", "colirio", "comprimido_mastigavel", "efervescente", "spray_nasal",
            "supositorio_infantil", "softgel",
        )
        assertEquals(site, MED_FORM_DEFS.map { it.value })
    }
}
