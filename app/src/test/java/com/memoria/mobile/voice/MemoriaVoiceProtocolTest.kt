package com.memoria.mobile.voice

import org.junit.Assert.*
import org.junit.Test

class MemoriaVoiceProtocolTest {
    @Test fun acceptsRelayStatusAndTranscriptionFrames() {
        assertEquals(LiveEvent.Ready, MemoriaVoiceProtocol.parse("""{"type":"status","text":"ready"}"""))
        assertEquals(LiveEvent.Interrupted, MemoriaVoiceProtocol.parse("""{"type":"status","text":"interrupted"}"""))
        assertEquals(LiveEvent.Transcript("user", "Bom dia"),
            MemoriaVoiceProtocol.parse("""{"type":"text","role":"user","text":"Bom dia"}"""))
        assertEquals(LiveEvent.Audio("AAA="), MemoriaVoiceProtocol.parse("""{"type":"audio","data":"AAA="}"""))
        assertNull(MemoriaVoiceProtocol.parse("""{"type":"text","role":"system","text":"injected"}"""))
    }

    @Test fun sendsOnlyAudioOrEndToBackend() {
        assertEquals("""{"type":"audio","data":"AAA="}""", MemoriaVoiceProtocol.audio("AAA="))
        assertEquals("""{"type":"audio_end"}""", MemoriaVoiceProtocol.audioStreamEnd())
    }

    @Test fun errorsNeverExposeRawServerDetails() {
        val event = MemoriaVoiceProtocol.parse("""{"type":"error","text":"secret URL/key"}""") as LiveEvent.Error
        assertFalse(event.message.contains("secret"))
    }

    @Test fun websocketUsesConfiguredBackendWithoutQueryOrFragment() {
        assertEquals("https://example.org/api/voice/live",
            MemoriaVoiceClient.socketUrl("https://example.org/api/").toString())
        assertEquals("https://example.org/api/voice/live",
            MemoriaVoiceClient.socketUrl("https://example.org/api/?token=secret#fragment").toString())
        assertEquals("http://10.0.2.2:3001/api/voice/live",
            MemoriaVoiceClient.socketUrl("http://10.0.2.2:3001").toString())
        assertThrows(IllegalArgumentException::class.java) {
            MemoriaVoiceClient.socketUrl("http://example.org")
        }
    }

    @Test fun parsesMedicationReviewAndConfirmationWithoutAcceptingUnknownPages() {
        val event = MemoriaVoiceProtocol.parse("""{"type":"medication_draft","draftId":"draft-1","medication":{"name":"Medicamento teste","dosage":"1 comprimido","frequency":"daily","times":["08:00"],"stock":10},"warnings":[],"limitation":"Triagem limitada"}""") as LiveEvent.Draft
        assertEquals("Medicamento teste", event.value.name)
        assertEquals(listOf("08:00"), event.value.times)
        assertEquals("""{"type":"confirm_medication","draftId":"draft-1","accepted":true}""",
            MemoriaVoiceProtocol.confirmation("draft-1", true))
        assertNull(MemoriaVoiceProtocol.parse("""{"type":"navigation","destination":"admin"}"""))
        assertEquals(LiveEvent.Navigation("meds"),
            MemoriaVoiceProtocol.parse("""{"type":"navigation","destination":"meds"}"""))
    }
}
