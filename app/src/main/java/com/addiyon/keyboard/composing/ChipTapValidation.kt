package com.addiyon.keyboard.composing

/**
 * Decides whether a published completion (or email) chip tap is still valid
 * against the controller's live state, without trusting the editor-token
 * selection captured at publish time.
 *
 * THE PROBLEM
 *
 * Every chip publication captures an [com.addiyon.keyboard.EditorToken] whose
 * `selectionStart` is the caret position at THAT instant. Between publish and
 * tap the user almost always types another letter, which moves the caret. The
 * previous design compared the current caret against that captured position
 * and rejected the tap once they disagreed. That is exactly the silent-no-op
 * the user reported in rich-text editors (Samsung Notes, Gmail, Docs,
 * anything Compose or WebView backed): the suggestion strip carries the
 * previous keystroke's chips forward while the next lookup is in flight -- the
 * carry is visible, the action generation still matches, but the captured
 * selection has moved on, so the tap looks "stale" to the validator.
 *
 * THE RIGHT CHECK
 *
 * A completion tap is valid when the strip was generated for the state the
 * field is ACTUALLY in right now. For a composing strip that means the
 * controller is still composing (which, by its invariant "caret always at the
 * composing end", means the buffer is intact and the caret is at its end). For
 * a caret-word strip that means the same committed word the caret sits at the
 * end of is still there.
 *
 * Pure Kotlin, zero Android imports -- JVM-unit-testable.
 */
internal fun isCompletionChipTapValid(
    composing: Boolean,
    currentCaretWord: String?,
    publishedCaretWord: String?
): Boolean =
    composing ||
        (publishedCaretWord != null && currentCaretWord == publishedCaretWord)