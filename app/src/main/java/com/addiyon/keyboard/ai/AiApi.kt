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
data class IssueLinkRequest(
    val email: String,
    @Json(name = "redirectUri") val redirectUri: String
)

@JsonClass(generateAdapter = true)
data class IssueLinkResponse(
    val sent: Boolean,
    val devLink: String? = null
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

    @POST("auth/link")
    suspend fun issueLink(@Body body: IssueLinkRequest): IssueLinkResponse

    @GET("usage/status")
    suspend fun quotaStatus(
        @Header("Authorization") auth: String? = null,
        @Header("X-Anonymous-Id") anonId: String? = null
    ): QuotaResponse
}
