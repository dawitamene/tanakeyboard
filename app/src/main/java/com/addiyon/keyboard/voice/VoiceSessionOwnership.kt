package com.addiyon.keyboard.voice

internal data class VoiceSessionTicket(val generation: Long)

internal class VoiceSessionOwnership {
    private var generation = 0L
    private var active = false
    private var destroyed = false

    fun begin(): VoiceSessionTicket? {
        if (destroyed) return null
        generation += 1
        active = true
        return VoiceSessionTicket(generation)
    }

    fun invalidate(): VoiceSessionTicket {
        generation += 1
        active = false
        return VoiceSessionTicket(generation)
    }

    fun destroy() {
        generation += 1
        active = false
        destroyed = true
    }

    fun isCurrent(ticket: VoiceSessionTicket): Boolean =
        !destroyed && active && ticket.generation == generation

    fun isGeneration(ticket: VoiceSessionTicket): Boolean =
        !destroyed && ticket.generation == generation
}
