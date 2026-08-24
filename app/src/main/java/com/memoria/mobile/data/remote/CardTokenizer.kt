package com.memoria.mobile.data.remote

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/** The card as typed on the native form, before it becomes a token. */
data class CardInput(
    val number: String,
    val holderName: String,
    val expiryMonth: Int,
    val expiryYear: Int,
    val securityCode: String,
    val holderDocument: String,
)

/**
 * Turns a card into a Mercado Pago token, on device.
 *
 * Deliberately built on its OWN OkHttp client rather than the app's: the shared
 * client attaches the MemorIA bearer token to every request and logs request
 * lines in debug builds. Neither belongs anywhere near a card number — this
 * client carries no auth header and no logging interceptor at all.
 */
class CardTokenizer {

    private val service: CardTokenService by lazy {
        Retrofit.Builder()
            .baseUrl(CardTokenService.BASE_URL)
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .callTimeout(25, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(CardTokenService::class.java)
    }

    /**
     * @return the token id, or a message describing what the user must fix.
     */
    suspend fun tokenize(publicKey: String, card: CardInput): Result<String> {
        val request = CardTokenRequest(
            card_number = card.number.filter { it.isDigit() },
            expiration_month = card.expiryMonth,
            expiration_year = card.expiryYear,
            security_code = card.securityCode.filter { it.isDigit() },
            cardholder = CardHolder(
                name = card.holderName.trim(),
                identification = CardIdentification(
                    type = "CPF",
                    number = card.holderDocument.filter { it.isDigit() },
                ),
            ),
        )

        return runCatching { service.createCardToken(publicKey, request) }
            .fold(
                onSuccess = { response ->
                    val body = response.body()
                    val id = body?.id
                    if (response.isSuccessful && !id.isNullOrBlank()) {
                        Result.success(id)
                    } else {
                        Result.failure(IllegalStateException(readableError(body, response.code())))
                    }
                },
                onFailure = { Result.failure(IllegalStateException(NETWORK_ERROR)) },
            )
    }

    /**
     * Mercado Pago answers with a `cause` list whose descriptions name the exact
     * field at fault. Surfacing it beats a generic "cartão inválido" for someone
     * who simply mistyped the expiry.
     */
    private fun readableError(body: CardTokenResponse?, code: Int): String {
        val cause = body?.cause?.firstOrNull()
        val description = cause?.description?.takeIf { it.isNotBlank() }
        if (description != null) return translate(cause.code, description)
        body?.message?.takeIf { it.isNotBlank() }?.let { return it }
        return if (code == 400) {
            "Confira os dados do cartão e tente de novo."
        } else {
            "Não foi possível validar o cartão agora. Tente de novo em instantes."
        }
    }

    /** The common rejections, in words the cardholder can act on. */
    private fun translate(code: String?, fallback: String): String = when (code) {
        "205" -> "Digite o número do cartão."
        "208", "209" -> "Digite o mês e o ano de validade."
        "212", "213", "214" -> "Digite o CPF do titular do cartão."
        "220", "221" -> "Digite o nome do titular como está no cartão."
        "224" -> "Digite o código de segurança (CVV)."
        "E301" -> "Número do cartão inválido. Confira os dígitos."
        "E302" -> "Código de segurança inválido."
        "316" -> "Nome do titular inválido."
        "325", "326" -> "Data de validade inválida."
        else -> fallback
    }

    private companion object {
        const val NETWORK_ERROR =
            "Sem conexão com o Mercado Pago para validar o cartão. Verifique a internet."
    }
}
