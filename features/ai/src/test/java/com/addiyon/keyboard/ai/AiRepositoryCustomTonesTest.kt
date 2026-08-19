package com.addiyon.keyboard.ai

import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRepositoryCustomTonesTest {

    @Test
    fun `listCustomTones returns mapped custom tones from api`() = runBlocking {
        val mockResponse = listOf(
            CustomToneResponseDto(
                id = "custom-1",
                label = "Custom Witty",
                promptFragment = "Write wittily",
                icon = "rocket",
                color = "purple"
            ),
            CustomToneResponseDto(
                id = "custom-2",
                label = "Custom Bullet",
                promptFragment = "Use bullet points",
                icon = "star",
                color = "amber"
            )
        )

        val repository = AiRepository(apiReturning(
            onList = { mockResponse }
        ))

        val result = repository.listCustomTones("jwt-token").getOrThrow()
        assertEquals(2, result.size)
        assertEquals("custom-1", result[0].id)
        assertEquals("Custom Witty", result[0].label)
        assertEquals("Write wittily", result[0].promptFragment)

        val tone = result[0].toCustomTone()
        assertEquals("custom-1", tone.id)
        assertEquals("Custom Witty", tone.title)
        assertEquals("Write wittily", tone.instruction)
        assertEquals("rocket", tone.icon)
        assertEquals("purple", tone.color)
    }

    @Test
    fun `upsertCustomTone sends DTO and receives created tone`() = runBlocking {
        val mockResponse = CustomToneResponseDto(
            id = "custom-new",
            label = "New Tone",
            promptFragment = "Be concise",
            icon = "palette",
            color = "teal"
        )

        val repository = AiRepository(apiReturning(
            onUpsert = { mockResponse }
        ))

        val result = repository.upsertCustomTone(
            jwt = "jwt-token",
            toneId = "custom-new",
            label = "New Tone",
            instruction = "Be concise",
            icon = "palette",
            color = "teal"
        ).getOrThrow()

        assertEquals("custom-new", result.id)
        assertEquals("New Tone", result.label)
        assertEquals("Be concise", result.promptFragment)
    }

    @Test
    fun `deleteCustomTone calls delete endpoint`() = runBlocking {
        val repository = AiRepository(apiReturning(
            onDelete = { mapOf("success" to true) }
        ))

        val result = repository.deleteCustomTone("jwt-token", "custom-to-delete").getOrThrow()
        assertTrue(result)
    }

    private fun apiReturning(
        onList: () -> List<CustomToneResponseDto> = { emptyList() },
        onUpsert: () -> CustomToneResponseDto = { error("not implemented") },
        onDelete: () -> Map<String, Boolean> = { emptyMap() }
    ): AiApi = Proxy.newProxyInstance(
        AiApi::class.java.classLoader,
        arrayOf(AiApi::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "listCustomTones" -> onList()
            "upsertCustomTone" -> onUpsert()
            "deleteCustomTone" -> onDelete()
            else -> error("Unexpected API call: ${method.name}")
        }
    } as AiApi
}
