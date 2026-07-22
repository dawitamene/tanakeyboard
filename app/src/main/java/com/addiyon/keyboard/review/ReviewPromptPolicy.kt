package com.addiyon.keyboard.review

/**
 * When to show the one-time Play in-app review prompt. Pure Kotlin so the
 * eligibility rules are JVM-testable; the Android pieces (the session counter
 * in KeyboardPrefs, the ReviewManager call in MainActivity) just consult this.
 *
 * "Session" here means one keyboard input session (onStartInputView with
 * restarting = false), a decent proxy for real usage. The prompt waits for
 * [MIN_SESSIONS] of them so only users who have actually typed with the
 * keyboard for a while are asked — Play's guidance is to request the review
 * at a natural moment after meaningful engagement, not on first contact.
 */
object ReviewPromptPolicy {
    /** Input sessions before the user counts as engaged enough to ask. */
    const val MIN_SESSIONS = 25

    /**
     * Sessions stop being counted past this, so the preference write on
     * keyboard-open stops once the count can no longer change an outcome.
     */
    const val COUNT_CAP = 100

    fun shouldCount(sessions: Int): Boolean = sessions < COUNT_CAP

    fun shouldPrompt(sessions: Int, alreadyPrompted: Boolean): Boolean =
        !alreadyPrompted && sessions >= MIN_SESSIONS
}
