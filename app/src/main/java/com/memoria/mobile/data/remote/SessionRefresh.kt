package com.memoria.mobile.data.remote

/**
 * What to do when the server rejects the JWT. Tokens last 7 days, so this is a
 * weekly event — not an edge case — and cancelling every alarm on expiry is how
 * reminders used to die for anyone who did not open the app that week.
 */
enum class ExpiryPlan {
    /** Saved CPF+password: log in again without kicking the user out. */
    SilentRefresh,

    /** Password was rejected (or refresh is blocked): show login, keep alarms. */
    DropTokenKeepAlarms,

    /** No remembered credentials: real logout. Cancel alarms. */
    DropTokenCancelAlarms,
}

object SessionRefresh {

    const val RETRY_COOLDOWN_MS = 15_000L

    /**
     * Login/register/recovery must never carry a bearer token. Attaching an
     * expired JWT to `/auth/login` makes the interceptor treat a wrong-password
     * 401 as "session over" and can loop expireSession into itself.
     */
    fun shouldAttachAuthHeader(encodedPath: String): Boolean {
        val path = encodedPath.trimEnd('/')
        return !(path.endsWith("/auth/login") ||
            path.endsWith("/auth/register") ||
            path.endsWith("/auth/forgot-password") ||
            path.endsWith("/auth/reset-password"))
    }

    fun plan(
        rememberEnabled: Boolean,
        hasSavedCredentials: Boolean,
        refreshBlocked: Boolean,
    ): ExpiryPlan = when {
        rememberEnabled && hasSavedCredentials && !refreshBlocked -> ExpiryPlan.SilentRefresh
        rememberEnabled && hasSavedCredentials -> ExpiryPlan.DropTokenKeepAlarms
        else -> ExpiryPlan.DropTokenCancelAlarms
    }

    fun looksLikeNetworkError(message: String): Boolean =
        message.contains("“Servidor”") ||
            message.contains("ligação", ignoreCase = true) ||
            message.contains("Falha de rede") ||
            message.contains("conexão", ignoreCase = true)

    fun looksLikeExpiredSession(message: String): Boolean =
        message.contains("Sessão inválida", ignoreCase = true) ||
            message.contains("Token inválido", ignoreCase = true) ||
            message.contains("expirad", ignoreCase = true)

    fun isRetryableFailure(message: String): Boolean =
        looksLikeNetworkError(message) || looksLikeExpiredSession(message)
}
