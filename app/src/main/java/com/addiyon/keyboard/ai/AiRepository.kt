package com.addiyon.keyboard.ai

import java.io.IOException
import retrofit2.HttpException

class AiRepository(
    private val api: AiApi,
    private val baseUrl: String = "https://api.textrevamp.com/"
) {
    suspend fun revamp(
        text: String,
        tab: AiToneTab,
        strength: AiStrength = AiStrength.Balanced,
        jwt: String?,
        anonId: String
    ): Result<AiResult> {
        val auth = jwt?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
        val req = RevampRequest(
            text = text,
            tone = tab.tone,
            strength = strength.label,
            instruction = tab.instruction
        )
        return try {
            val res = api.revamp(req, auth, anonId)
            val words = res.text.trim()
            if (words.isEmpty()) return Result.failure(Exception("empty response"))
            Result.success(AiResult(res.text, res.tone, res.strength ?: strength.label, res.truncated == true))
        } catch (e: HttpException) {
            Result.failure(mapHttp(e))
        } catch (e: IOException) {
            Result.failure(Exception(AiError.Offline.toString(), e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun alternatives(
        text: String,
        tab: AiToneTab,
        strength: AiStrength = AiStrength.Balanced,
        jwt: String?,
        anonId: String
    ): Result<List<String>> {
        val auth = jwt?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
        val req = RevampRequest(text, tab.tone, strength.label, tab.instruction)
        return try {
            val res = api.alternatives(req, auth, anonId)
            Result.success(res.alternatives)
        } catch (e: HttpException) {
            Result.failure(mapHttp(e))
        } catch (e: IOException) {
            Result.failure(Exception(AiError.Offline.toString(), e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun issueMagicLink(email: String, redirectUri: String): Result<IssueLinkResponse> {
        return try {
            val res = api.issueLink(IssueLinkRequest(email, redirectUri))
            Result.success(res)
        } catch (e: HttpException) {
            Result.failure(mapHttp(e))
        } catch (e: IOException) {
            Result.failure(Exception(AiError.Offline.toString(), e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun quota(jwt: String?, anonId: String): Result<AiQuota> {
        val auth = jwt?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
        return try {
            val res = api.quotaStatus(auth, anonId)
            val used = res.used ?: res.count ?: 0
            val remaining = res.remaining ?: (res.limit - used).coerceAtLeast(0)
            Result.success(AiQuota(used, res.limit, remaining, todayIso()))
        } catch (e: HttpException) {
            Result.failure(mapHttp(e))
        } catch (e: IOException) {
            Result.failure(Exception(AiError.Offline.toString(), e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapHttp(e: HttpException): Exception {
        val code = e.code()
        return when (code) {
            401 -> Exception(AiError.NeedsAuth.toString())
            429 -> Exception(AiError.QuotaExceeded().toString())
            400 -> Exception(AiError.Server(e.message()).toString())
            else -> Exception(AiError.Server("HTTP $code").toString())
        }
    }

    fun parseAiError(exception: Throwable): AiError {
        val msg = exception.message ?: ""
        return when {
            msg.contains("NeedsAuth") -> AiError.NeedsAuth
            msg.contains("QuotaExceeded") -> AiError.QuotaExceeded()
            msg.contains("Offline") -> AiError.Offline
            else -> AiError.Server(msg.ifEmpty { "Unknown error" })
        }
    }
}
