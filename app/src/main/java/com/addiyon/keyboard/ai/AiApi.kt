package com.addiyon.keyboard.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class RevampRequest(
    val text: String,
    val tone: String,
    val strength: String? = null,
    val instruction: String? = null
)

@JsonClass(generateAdapter = true)
data class RevampResponse(
    val text: String,
    val tone: String,
    val strength: String? = null,
    val truncated: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class AlternativesResponse(
    val alternatives: List<String>,
    val tone: String,
    val strength: String? = null
)

@JsonClass(generateAdapter = true)
data class ContinueRequest(
    val email: String
)

@JsonClass(generateAdapter = true)
data class ContinueResponse(
    val email: String,
    val nextStep: String
)

@JsonClass(generateAdapter = true)
data class SendOtpRequest(
    val email: String,
    val otpFor: String? = null
)

@JsonClass(generateAdapter = true)
data class SendOtpResponse(
    val email: String,
    val retryAfter: Int? = null
)

@JsonClass(generateAdapter = true)
data class VerifyOtpRequest(
    val email: String,
    val otp: String
)

@JsonClass(generateAdapter = true)
data class VerifyOtpResponse(
    val email: String,
    val token: String
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val otpToken: String,
    val name: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val message: String? = null,
    val token: String,
    val user: AuthUser? = null,
    val expiresAt: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthUser(
    val id: Int? = null,
    val email: String? = null,
    val name: String? = null,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleTokenRequest(
    val idToken: String
)

@JsonClass(generateAdapter = true)
data class QuotaResponse(
    val count: Int? = null,
    val used: Int? = null,
    val limit: Int,
    val remaining: Int? = null
)

interface AiApi {
    @POST("text")
    suspend fun revamp(
        @Body body: RevampRequest,
        @Header("Authorization") auth: String? = null,
        @Header("X-Anonymous-Id") anonId: String? = null
    ): RevampResponse

    @POST("text/alternatives")
    suspend fun alternatives(
        @Body body: RevampRequest,
        @Header("Authorization") auth: String? = null,
        @Header("X-Anonymous-Id") anonId: String? = null
    ): AlternativesResponse

    @POST("auth/continue")
    suspend fun authContinue(@Body body: ContinueRequest): ContinueResponse

    @POST("auth/send-otp")
    suspend fun sendOtp(@Body body: SendOtpRequest): SendOtpResponse

    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body body: VerifyOtpRequest): VerifyOtpResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/oauth/google/token")
    suspend fun googleToken(@Body body: GoogleTokenRequest): AuthResponse

    @GET("usage/status")
    suspend fun quotaStatus(
        @Header("Authorization") auth: String? = null,
        @Header("X-Anonymous-Id") anonId: String? = null
    ): QuotaResponse
}
