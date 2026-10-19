package com.memoria.mobile.ui.auth

import com.memoria.mobile.data.ApiResult
import com.memoria.mobile.data.MemoriaRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationPasswordTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: MemoriaRepository
    private lateinit var vm: AuthViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        repo = mockk(relaxed = true)
        every { repo.currentBaseUrl() } returns "https://example.test"
        vm = AuthViewModel(repo)
    }

    @After fun teardown() { Dispatchers.resetMain() }

    private fun register(password: String, confirmation: String) {
        vm.register("12345678901", "Pessoa Teste", password, confirmation, "", "", true, {})
    }

    @Test fun `requires confirmation before calling server`() = runTest(dispatcher) {
        register("Senha1234", "")
        advanceUntilIdle()
        assertEquals("Digite a senha novamente para confirmar.", vm.state.value.error)
        coVerify(exactly = 0) { repo.register(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `rejects different passwords including case and spaces`() = runTest(dispatcher) {
        listOf("senha1234", "Senha1234 ").forEach {
            register("Senha1234", it)
            assertEquals("As duas senhas não são iguais.", vm.state.value.error)
        }
        advanceUntilIdle()
        coVerify(exactly = 0) { repo.register(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `matching confirmation submits original password once`() = runTest(dispatcher) {
        coEvery { repo.register(any(), any(), any(), any(), any(), any(), any(), any()) } returns ApiResult.Err("Resposta de teste")
        register("Senha1234", "Senha1234")
        advanceUntilIdle()
        coVerify(exactly = 1) { repo.register("12345678901", "Pessoa Teste", "Senha1234", "", "", true, "", "not_informed") }
    }

    @Test fun `matching short passwords still respect minimum length`() = runTest(dispatcher) {
        register("curta", "curta")
        advanceUntilIdle()
        assertEquals("A senha deve ter pelo menos 8 caracteres.", vm.state.value.error)
        coVerify(exactly = 0) { repo.register(any(), any(), any(), any(), any(), any(), any(), any()) }
    }
}
