package com.memoria.mobile.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRefreshTest {

    @Test
    fun savedCredentialsTriggerSilentRefresh() {
        assertEquals(
            ExpiryPlan.SilentRefresh,
            SessionRefresh.plan(rememberEnabled = true, hasSavedCredentials = true, refreshBlocked = false),
        )
    }

    @Test
    fun rejectedPasswordKeepsAlarms() {
        assertEquals(
            ExpiryPlan.DropTokenKeepAlarms,
            SessionRefresh.plan(rememberEnabled = true, hasSavedCredentials = true, refreshBlocked = true),
        )
    }

    @Test
    fun noCredentialsCancelAlarms() {
        assertEquals(
            ExpiryPlan.DropTokenCancelAlarms,
            SessionRefresh.plan(rememberEnabled = true, hasSavedCredentials = false, refreshBlocked = false),
        )
        assertEquals(
            ExpiryPlan.DropTokenCancelAlarms,
            SessionRefresh.plan(rememberEnabled = false, hasSavedCredentials = true, refreshBlocked = false),
        )
    }

    @Test
    fun loginMustNotCarryABearerToken() {
        assertFalse(SessionRefresh.shouldAttachAuthHeader("/api/auth/login"))
        assertFalse(SessionRefresh.shouldAttachAuthHeader("/api/auth/register"))
        assertFalse(SessionRefresh.shouldAttachAuthHeader("/api/auth/forgot-password"))
        assertFalse(SessionRefresh.shouldAttachAuthHeader("/api/auth/reset-password/"))
        assertTrue(SessionRefresh.shouldAttachAuthHeader("/api/medications"))
        assertTrue(SessionRefresh.shouldAttachAuthHeader("/api/auth/me"))
        assertTrue(SessionRefresh.shouldAttachAuthHeader("/api/history"))
    }

    @Test
    fun expiredSessionErrorsAreRetryableSoTheOfflineQueueIsNotDropped() {
        assertTrue(SessionRefresh.isRetryableFailure("Sessão inválida ou credenciais incorretas."))
        assertTrue(SessionRefresh.isRetryableFailure("Token inválido ou expirado."))
        assertTrue(SessionRefresh.isRetryableFailure("O servidor demorou demais a responder. Verifique a internet e o endereço em “Servidor” (https://x)."))
        assertFalse(SessionRefresh.isRetryableFailure("Acesso negado."))
        assertFalse(SessionRefresh.isRetryableFailure("Erro no servidor. Tente novamente."))
    }
}
