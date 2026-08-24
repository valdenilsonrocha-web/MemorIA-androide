package com.memoria.mobile.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * MemorIA backend REST surface (base = `<root>/api/`). Mirrors
 * frontend/public/src/services/apiService.js so the mobile app talks to the very
 * same server the PWA uses.
 */
interface ApiService {

    // Auth
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<Envelope<AuthData>>

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<Envelope<AuthData>>

    @GET("auth/me")
    suspend fun me(): Response<Envelope<UserData>>

    @PUT("auth/profile")
    suspend fun updateProfile(@Body body: ProfileUpdateRequest): Response<Envelope<UserData>>

    // Recuperação de senha
    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<SimpleResponse>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): Response<SimpleResponse>

    // Medications
    @GET("medications")
    suspend fun getMedications(): Response<Envelope<MedicationListData>>

    @POST("medications")
    suspend fun createMedication(@Body body: MedicationRequest): Response<Envelope<MedicationData>>

    @PUT("medications/{id}")
    suspend fun updateMedication(
        @Path("id") id: String,
        @Body body: MedicationRequest,
    ): Response<Envelope<MedicationData>>

    @DELETE("medications/{id}")
    suspend fun deleteMedication(@Path("id") id: String): Response<SimpleResponse>

    // History
    @GET("history")
    suspend fun getHistory(
        @Query("limit") limit: Int = 100,
        @Query("status") status: String? = null,
    ): Response<Envelope<HistoryListData>>

    @POST("history")
    suspend fun addHistory(@Body body: HistoryRequest): Response<SimpleResponse>

    @GET("history/adherence")
    suspend fun getAdherence(@Query("days") days: Int = 30): Response<Envelope<Adherence>>

    // Prescriptions
    @GET("prescriptions")
    suspend fun getPrescriptions(): Response<Envelope<PrescriptionListData>>

    @POST("prescriptions")
    suspend fun createPrescription(
        @Body body: PrescriptionRequest,
    ): Response<Envelope<PrescriptionData>>

    @DELETE("prescriptions/{id}")
    suspend fun deletePrescription(@Path("id") id: String): Response<SimpleResponse>

    // Payments
    @GET("payments/config")
    suspend fun paymentConfig(): Response<Envelope<PaymentConfig>>

    // Admin (owner dashboard — 403 for a non-admin account)
    @GET("admin/owner-stats")
    suspend fun ownerStats(): Response<Envelope<OwnerStats>>

    // Assinatura nativa: envia SÓ o token do cartão, nunca o cartão.
    @POST("payments/subscribe")
    suspend fun subscribeWithCard(
        @Body body: SubscribeRequest,
    ): Response<Envelope<SubscriptionStatusData>>

    // Checkout / assinatura
    @POST("payments/create-checkout-session")
    suspend fun createCheckoutSession(
        @Body body: CheckoutRequest,
    ): Response<Envelope<CheckoutSession>>

    @POST("payments/subscription-status")
    suspend fun subscriptionStatus(
        @Body body: SubscriptionStatusRequest,
    ): Response<Envelope<SubscriptionStatusData>>

    @POST("payments/cancel-subscription")
    suspend fun cancelSubscription(): Response<SimpleResponse>

    // LGPD — direitos do titular
    /**
     * Portability export. Returned as a raw body on purpose: the point of the
     * right of access is to hand over everything the server holds, so modelling
     * it into DTOs would quietly drop any field the app does not know about.
     */
    @GET("auth/export-data")
    suspend fun exportData(): Response<ResponseBody>

    @PUT("auth/consent")
    suspend fun updateConsent(@Body body: ConsentRequest): Response<Envelope<UserData>>

    @DELETE("auth/account")
    suspend fun deleteAccount(): Response<SimpleResponse>

    // Health (absolute URL: `<root>/health`, outside the /api prefix)
    @GET
    suspend fun health(@Url url: String): Response<Map<String, Any>>
}
