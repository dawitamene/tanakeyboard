package com.addiyon.keyboard.ai

import retrofit2.HttpException
import java.io.IOException

interface AiCompletionSource {
    suspend fun complete(prefix: String, jwt: String?, anonId: String): Result<String?>
}

class AiCompletionRepository(
    private val api: AiApi
) : AiCompletionSource {
    override suspend fun complete(
        prefix: String,
        jwt: String?,
        anonId: String
    ): Result<String?> = try {
        val response = api.completion(
            body = CompletionRequest(prefix),
            auth = jwt?.let { "Bearer $it" },
            anonId = anonId
        )
        Result.success(response.completion?.takeIf { it.isNotBlank() })
    } catch (error: IOException) {
        Result.failure(Exception(AiError.Offline.toString(), error))
    } catch (error: HttpException) {
        Result.failure(error)
    } catch (error: Throwable) {
        Result.failure(error)
    }
}
