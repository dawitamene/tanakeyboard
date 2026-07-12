package com.addiyon.keyboard

internal class SuggestionRefreshGate {
    private var blocked = false
    private var pending = false

    fun beginDeleteGesture() {
        if (blocked) return
        blocked = true
        pending = false
    }

    fun requestRefresh(): Boolean {
        if (!blocked) return true
        pending = true
        return false
    }

    fun endDeleteGesture(): Boolean {
        if (!blocked) return false
        blocked = false
        return pending.also { pending = false }
    }
}
