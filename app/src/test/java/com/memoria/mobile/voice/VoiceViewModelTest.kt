package com.memoria.mobile.voice

import com.memoria.mobile.ui.voice.VoiceStatus
import com.memoria.mobile.ui.voice.VoiceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeConnection : LiveVoiceConnection {
        var emit: (LiveEvent) -> Unit = {}
        var closed = false
        var audioSent = 0
        var ended = 0
        override fun connect(onEvent: (LiveEvent) -> Unit) { emit = onEvent }
        override fun sendAudio(pcm: ByteArray) { if (!closed) audioSent++ }
        override fun endAudio() { ended++ }
        override fun close() { closed = true }
    }
    private class FakeAudio : VoiceAudio {
        var starts = 0
        var stops = 0
        var interrupted = 0
        var capture: (ByteArray) -> Unit = {}
        override suspend fun start(onAudio: (ByteArray) -> Unit, onError: (String) -> Unit) {
            starts++
            capture = onAudio
        }
        override fun play(base64: String) {}
        override fun interrupt() { interrupted++ }
        override suspend fun stop() { stops++ }
    }

    @Test fun waitsForReadyAndMutesOutgoingAudio() = runTest(dispatcher) {
        val audio = FakeAudio()
        val connection = FakeConnection()
        val vm = VoiceViewModel(audio) { connection }
        vm.start()
        runCurrent()
        assertEquals(0, audio.starts)
        assertEquals(VoiceStatus.CONNECTING, vm.state.value.status)
        connection.emit(LiveEvent.Ready)
        runCurrent()
        assertEquals(1, audio.starts)
        audio.capture(byteArrayOf(0, 0))
        assertEquals(1, connection.audioSent)
        vm.toggleMute()
        runCurrent()
        audio.capture(byteArrayOf(0, 0))
        assertEquals(1, connection.audioSent)
        assertEquals(1, connection.ended)
        vm.toggleMute()
        runCurrent()
        audio.capture(byteArrayOf(0, 0))
        assertEquals(2, connection.audioSent)
        vm.stop()
        runCurrent()
        assertTrue(connection.closed)
        assertEquals(VoiceStatus.IDLE, vm.state.value.status)
    }

    @Test fun staleReadyCannotRestartTheMicrophoneAfterStopping() = runTest(dispatcher) {
        val audio = FakeAudio()
        val connection = FakeConnection()
        val vm = VoiceViewModel(audio) { connection }
        vm.start()
        runCurrent()
        vm.stop()
        runCurrent()
        connection.emit(LiveEvent.Ready)
        runCurrent()
        assertEquals(0, audio.starts)
        assertEquals(VoiceStatus.IDLE, vm.state.value.status)
    }

    @Test fun setupTimeoutClosesConnectionWithoutOpeningMicrophone() = runTest(dispatcher) {
        val audio = FakeAudio()
        val connection = FakeConnection()
        val vm = VoiceViewModel(audio) { connection }
        vm.start()
        runCurrent()
        advanceTimeBy(25001)
        runCurrent()
        assertTrue(connection.closed)
        assertEquals(0, audio.starts)
        assertNotNull(vm.state.value.error)
    }

    @Test fun interruptionFlushesPlaybackAndKeepsTranscriptionInMemory() = runTest(dispatcher) {
        val audio = FakeAudio()
        val connection = FakeConnection()
        val vm = VoiceViewModel(audio) { connection }
        vm.start()
        runCurrent()
        connection.emit(LiveEvent.Ready)
        connection.emit(LiveEvent.Transcript("user", "Bom dia"))
        connection.emit(LiveEvent.Transcript("assistant", "Olá"))
        connection.emit(LiveEvent.Audio("AAA="))
        runCurrent()
        assertEquals(VoiceStatus.SPEAKING, vm.state.value.status)
        connection.emit(LiveEvent.Interrupted)
        runCurrent()
        assertEquals(1, audio.interrupted)
        assertEquals(listOf("Bom dia", "Olá"), vm.state.value.lines.map { it.text })
        assertEquals(VoiceStatus.LISTENING, vm.state.value.status)
        vm.stop()
        runCurrent()
    }
}
