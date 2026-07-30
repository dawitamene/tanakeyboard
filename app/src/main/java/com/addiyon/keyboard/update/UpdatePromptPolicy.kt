package com.addiyon.keyboard.update

/**
 * When to offer Play's in-app update sheet. Pure Kotlin so the rules are
 * JVM-testable; the Android side ([InAppUpdateController]) just feeds it what
 * Play reports and acts on the answer.
 *
 * Every available update is offered, whatever Play's `inAppUpdatePriority` or
 * staleness says about it — there is no "minor update" tier that gets skipped.
 * The prompt is deliberately re-offered on each app open, and is only ever the
 * dismissible FLEXIBLE flow, so declining costs one tap and always works.
 */
object UpdatePromptPolicy {

    /**
     * [alreadyPrompted] is per app-open, not persisted: the user gets Play's
     * sheet each time they open the app, and closing it only lasts for that
     * visit. It exists to stop the check from re-firing on every
     * [InAppUpdateController.onResume] within one visit — including the resume
     * that happens when Play's own sheet hands control back, which would
     * otherwise relaunch the flow in a loop the user could not escape.
     *
     * [flexibleAllowed] is not a tier check but a capability one: if Play says
     * this update cannot be delivered flexibly, we skip rather than fall back
     * to the blocking IMMEDIATE flow, since the update must stay declinable.
     */
    fun shouldPrompt(
        updateAvailable: Boolean,
        flexibleAllowed: Boolean,
        alreadyPrompted: Boolean
    ): Boolean = updateAvailable && flexibleAllowed && !alreadyPrompted
}
