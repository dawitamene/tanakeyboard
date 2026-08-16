package com.addiyon.keyboard.ai

import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AiRepositoryQuotaTest {
    @Test
    fun `quota uses provider input and output token totals`() = runBlocking {
        val repository = AiRepository(apiReturning(
            QuotaResponse(
                inputTokens = 12_000,
                outputTokens = 3_500,
                totalTokens = 15_500,
                reservedTokens = 500,
                limit = 50_000,
                remaining = 34_000,
                unit = "tokens"
            )
        ))

        val quota = repository.quota("jwt", "anonymous").getOrThrow()

        assertEquals(15_500, quota.used)
        assertEquals(50_000, quota.limit)
        assertEquals(34_000, quota.remaining)
    }

    @Test
    fun `quota sums token breakdown when total is omitted`() = runBlocking {
        val repository = AiRepository(apiReturning(
            QuotaResponse(
                inputTokens = 800,
                outputTokens = 200,
                limit = 50_000
            )
        ))

        val quota = repository.quota(null, "anonymous").getOrThrow()

        assertEquals(1_000, quota.used)
        assertEquals(49_000, quota.remaining)
    }

    @Test
    fun `quota error retains the server remaining token count`() {
        assertEquals(
            25_500,
            quotaRemainingFromError(
                "{\"code\":\"DAILY_TOKEN_LIMIT_REACHED\",\"remaining\":25500}"
            )
        )
        assertEquals(
            25_500,
            quotaRemainingFromError(AiError.QuotaExceeded(25_500).toString())
        )
    }

    private fun apiReturning(response: QuotaResponse): AiApi = Proxy.newProxyInstance(
        AiApi::class.java.classLoader,
        arrayOf(AiApi::class.java)
    ) { _, method, _ ->
        if (method.name == "quotaStatus") response else error("Unexpected API call: ${method.name}")
    } as AiApi
}
