package com.memoria.mobile.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Mercado Pago's card tokenisation endpoint, called straight from the phone.
 *
 * This is what makes a fully native checkout possible without MemorIA ever
 * holding card data: the app posts the card to Mercado Pago using the PUBLIC
 * key, gets back an opaque `id`, and only that id is sent to the MemorIA
 * backend. The card number never reaches our server, and nothing about it is
 * stored on the device.
 *
 * Base URL is Mercado Pago's own API, never the MemorIA backend.
 */
interface CardTokenService {

    @POST("v1/card_tokens")
    suspend fun createCardToken(
        @Query("public_key") publicKey: String,
        @Body body: CardTokenRequest,
    ): Response<CardTokenResponse>

    companion object {
        const val BASE_URL = "https://api.mercadopago.com/"
    }
}

@JsonClass(generateAdapter = true)
data class CardTokenRequest(
    val card_number: String,
    val expiration_month: Int,
    val expiration_year: Int,
    val security_code: String,
    val cardholder: CardHolder,
)

@JsonClass(generateAdapter = true)
data class CardHolder(
    val name: String,
    val identification: CardIdentification,
)

@JsonClass(generateAdapter = true)
data class CardIdentification(
    /** Brazil: "CPF". */
    val type: String,
    val number: String,
)

@JsonClass(generateAdapter = true)
data class CardTokenResponse(
    val id: String? = null,
    val last_four_digits: String? = null,
    val status: String? = null,
    /** Present when Mercado Pago rejects the card data. */
    val message: String? = null,
    val cause: List<CardTokenCause> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CardTokenCause(
    val code: String? = null,
    val description: String? = null,
)
