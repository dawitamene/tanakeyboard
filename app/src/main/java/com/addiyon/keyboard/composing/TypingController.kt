package com.addiyon.keyboard.composing

import com.addiyon.keyboard.EditorGateway

/**
 * Owns every edit the keyboard makes to the user's text.
 *
 * WHY THIS IS NOT IN AddiyonKeyboardService
 *
 * It used to be, and that is the reason none of it had JVM coverage:
 * `InputMethodService` cannot be constructed off-device, so the orchestration
 * where the cursor and composing bugs actually live could only be exercised by an
 * emulator test. Everything here is plain Kotlin over [EditorGateway], so the
 * whole key-handling surface can be driven against a real in-memory document (see
 * `FakeEditor`) across every editor personality that matters. The service keeps
 * the Android lifecycle and the UI, and forwards key events here.
 *
 * THE INVARIANT
 *
 * Only an explicit user action may change the characters in the field:
 * a character key, backspace, space, enter, or a tapped suggestion. Everything
 * else — cursor moves, selection changes, the keyboard hiding, the input session
 * ending — may only ever *finalize* what is already visible ([Composition.finalizeInPlace],
 * which calls `finishComposingText` and nothing else).
 *
 * That invariant is enforced structurally rather than by checks: no method here
 * computes an absolute document offset, so there is no range for a stale position
 * to point at. See [Composition] and [WordAdoption] for why the previous
 * offset-tracking design could not hold it.
 */
internal class TypingController(
    private val editor: EditorGateway,
    private val profile: () -> TypingProfile,
    /** Notified whenever the composing buffer changes, so suggestions can refresh. */
    private val onCompositionChanged: () -> Unit = {},
    private val onWordCommitted: (String) -> Unit = {}
) {

    private val resumedWords = ResumedWordMemory()

    /**
     * Depth of the controller's own in-flight edits.
     *
     * The platform posts `onUpdateSelection` through a handler, so it never
     * arrives inside an `InputConnection` write. Some editors nonetheless notify
     * synchronously from their own mutation, and without this the controller would
     * re-enter itself mid-write: the callback sees a composition that has been
     * written but not yet cleared, finalizes it, and that finalize triggers another
     * callback, and so on.
     *
     * This is not the echo-classification this rewrite removed — it does not try to
     * decide what a callback means. It only declines to act on one while we are
     * still in the middle of causing it. Any callback that arrives outside our own
     * writes, which is all of them under real asynchronous delivery, is handled
     * normally.
     */
    private var ownEditDepth = 0

    /**
     * One composition, not one per language. There is only ever one composing
     * region in one editor; keeping two composers and an `activeComposer` getter
     * meant every mode transition had to remember to commit the right one first.
     * Language-specific behaviour comes from [profile], read fresh at each use.
     */
    private val composition = Composition(
        editor = editor,
        commitTransform = { raw -> profile().commitTransform(raw) },
        lastUnitStart = { raw -> profile().lastUnitStart(raw) },
        onCommit = { raw, display ->
            if (profile().remembersRawLatin) resumedWords.remember(display, raw)
            onWordCommitted(display)
        }
    )

    val isComposing: Boolean get() = composition.isActive

    /** The raw key buffer — the suggestion pipeline's lookup key. */
    val buffer: String get() = composition.raw

    // ------------------------------------------------------------ key events

    /**
     * A character key was pressed. [text] is already shift-resolved.
     *
     * A non-word character (punctuation) ends the current word: the word commits
     * first, then the punctuation is committed directly rather than composed.
     */
    fun onCharacter(text: String) = applyingOwnEdit {
        val current = profile()
        if (!current.isWordCharacter(text)) {
            commitActiveWord()
            editor.commitText(current.transformStandalone(text))
            onCompositionChanged()
            return@applyingOwnEdit
        }
        if (!composition.isActive && caretIsInsideAWord(current)) {
            // Typing into the interior of an existing word inserts plainly. See
            // WordAdoption.CaretContext.InWord for why this is a rule and not a
            // limitation.
            editor.commitText(text)
            onCompositionChanged()
            return@applyingOwnEdit
        }
        composition.append(text)
        onCompositionChanged()
    }

    /**
     * Backspace. Shrinks the composing buffer if there is one; otherwise deletes
     * from the field and then re-opens whatever word the caret now sits at the end
     * of, so the strip keeps answering the word being edited.
     */
    fun onBackspace(clusterLength: Int = 1) = applyingOwnEdit {
        if (composition.backspace()) {
            onCompositionChanged()
            return@applyingOwnEdit
        }
        val selected = editor.selectedText()?.value
        if (!selected.isNullOrEmpty()) {
            // A range selection is deleted by replacing it with nothing. This is
            // the one place an empty commit is correct: the user asked for it.
            editor.commitText("")
        } else {
            editor.deleteBeforeCursor(clusterLength.coerceAtLeast(1))
        }
        adoptWordAtCaret(profile())
        onCompositionChanged()
    }

    fun onSpace() = applyingOwnEdit {
        commitActiveWord()
        editor.commitText(" ")
        onCompositionChanged()
    }

    /**
     * Enter commits the word first, so a form submission sees the finished word
     * rather than a half-composed one. [performAction] runs the field's IME action
     * (search/go/send) or inserts a newline; the service decides which.
     */
    fun onEnter(performAction: () -> Unit) = applyingOwnEdit {
        commitActiveWord()
        performAction()
        onCompositionChanged()
    }

    /** Committing text from a non-letter key (symbols, emoji) ends the word first. */
    fun onCommitText(text: String) = applyingOwnEdit {
        commitActiveWord()
        editor.commitText(text)
        onCompositionChanged()
    }

    /**
     * A suggestion chip was tapped.
     *
     * A completion replaces the word it was offered for. If that word is already
     * composing, `commitText` swaps the region atomically. If it is committed text
     * the caret is merely sitting at the end of, it is adopted first — with the
     * same offset-free delete+compose used everywhere else, which is why this now
     * works in editors where the old `replaceRange(spanStart, spanEnd, …)` path
     * silently did nothing.
     *
     * A prediction is a NEW word: it inserts at the caret and must never replace
     * anything, so it deliberately skips adoption.
     */
    fun onSuggestionTap(
        word: String,
        kind: SuggestionKind,
        trailingSpace: Boolean = true
    ): Boolean = applyingOwnEdit {
        if (word.isEmpty()) return@applyingOwnEdit false
        if (kind == SuggestionKind.COMPLETION && !composition.isActive) {
            adoptWordAtCaret(profile())
        }
        composition.replaceWith(word, trailingSpace).also { onCompositionChanged() }
    }

    // -------------------------------------------------------------- editor events

    /**
     * The framework reported a selection change — both for the IME's own edits and
     * for the user tapping elsewhere.
     *
     * THE ONE RULE: a composition survives only while the caret is still at its
     * end. Anything else finalizes it in place. There is no attempt to classify
     * callbacks as "our echo" versus "a real move", because the two are treated
     * identically: an echo leaves the caret at the composing end (we only ever
     * write with `newCursorPosition = 1`), so it passes; a real move to anywhere
     * else fails and freezes the word exactly as the user sees it.
     *
     * That is what retires `pendingCursorOffset`, `replacedComposingRegions`,
     * `DeleteResumeGuard`, `predictionBoundaryMutationDepth` and the rest of the
     * echo-guessing ladder.
     */
    fun onSelectionChanged(
        selectionStart: Int,
        selectionEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        if (ownEditDepth > 0) return
        if (!composition.isActive) return
        if (!stillAtCompositionEnd(selectionStart, selectionEnd, candidatesStart, candidatesEnd)) {
            composition.finalizeInPlace()
            onCompositionChanged()
        }
    }

    /** A new input session: drop our bookkeeping without touching either field. */
    fun onStartInput() {
        composition.discard()
        resumedWords.clear()
    }

    /** The session or the input view is ending: freeze what is visible, never rewrite it. */
    fun onFinishInput() = applyingOwnEdit {
        composition.finalizeInPlace()
        resumedWords.clear()
    }

    /**
     * The user switched language. The half-typed word belongs to the outgoing
     * language's pipeline, so it commits before the switch rather than being
     * reinterpreted by the incoming one.
     */
    fun onLanguageChange() = applyingOwnEdit {
        commitActiveWord()
        onCompositionChanged()
    }

    // ------------------------------------------------------------------ internals

    private inline fun <T> applyingOwnEdit(block: () -> T): T {
        ownEditDepth++
        try {
            return block()
        } finally {
            ownEditDepth--
        }
    }

    /**
     * The in-flight word is done: lock it into the field as committed text.
     * Called internally at word boundaries, and by the service when a mode
     * switch (language, numbers, emoji panel) must not carry a half-typed word
     * across. Does not notify [onCompositionChanged]; callers do.
     */
    fun commitActiveWord() {
        if (!composition.isActive) return
        if (!composition.commit()) {
            // The editor refused the write. The buffer is already cleared, and the
            // region still holds the raw text the user typed, so finalize that
            // rather than leaving a live region nothing owns.
            editor.finishComposingText()
        }
    }

    /**
     * True when a word character follows the caret. Costs one short read and also
     * performs the adoption when the caret turns out to be at a word's end, so the
     * common keystroke makes a single pair of reads rather than one per question.
     */
    private fun caretIsInsideAWord(current: TypingProfile): Boolean {
        val context = WordAdoption.inspect(
            editor = editor,
            isWordCharacter = { char -> current.isWordCharacter(char.toString()) },
            wordEndingAtCursor = current.wordEndingAtCursor
        )
        when (context) {
            is WordAdoption.CaretContext.InWord -> return true
            is WordAdoption.CaretContext.Free -> return false
            is WordAdoption.CaretContext.AtWordEnd -> {
                if (!current.allowsAdoption) return false
                // Amharic's field text is fidel while its buffer is SERA Latin, so
                // it can only adopt a word whose Latin spelling this keyboard
                // actually recorded. Anything else is left alone rather than
                // reverse-transliterated on a guess.
                val buffer = if (current.remembersRawLatin) {
                    resumedWords.rawLatinFor(context.word) ?: return false
                } else {
                    context.word
                }
                if (WordAdoption.adopt(editor, context.word)) composition.adopt(buffer)
                return false
            }
        }
    }

    private fun adoptWordAtCaret(current: TypingProfile) {
        caretIsInsideAWord(current)
    }

    private fun stillAtCompositionEnd(
        selectionStart: Int,
        selectionEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ): Boolean {
        if (selectionStart != selectionEnd) return false
        if (candidatesStart >= 0 && candidatesEnd >= candidatesStart) {
            // The framework told us where the region was when this callback was
            // raised; that is authoritative and needs no read back.
            //
            // Deliberately NOT also requiring the region's length to match the
            // current buffer. Callbacks are delivered asynchronously and can
            // describe an intermediate state — adopting a word emits one, then
            // appending to it emits another — so a perfectly ordinary echo can
            // report a region shorter than the buffer now is. Comparing against
            // "now" made those echoes look like foreign edits and finalized the
            // word mid-keystroke. The caret's position relative to the region it
            // was reported against is the whole question.
            return selectionStart == candidatesEnd
        }
        // Compose text fields, WebViews and cross-platform toolkits report -1 here
        // even though they applied the region. Treating that as "the region is
        // gone" is what made the composer give up mid-word in those editors, so
        // ask the field directly instead — relative, so it works everywhere.
        //
        // Residual: if the same characters appear elsewhere and the caret lands at
        // the end of THAT copy, this accepts. The consequence is bounded — the next
        // keystroke extends the composing region, which is still the user's own
        // word — and no text is deleted or overwritten.
        val raw = composition.raw
        return editor.textBeforeCursor(raw.length, optional = false)?.value == raw
    }
}

/** Which strip the tapped chip came from; decides whether it replaces or inserts. */
internal enum class SuggestionKind {
    /** Completes the word at the caret, replacing it. */
    COMPLETION,

    /** A next-word guess: inserts at the caret, replaces nothing. */
    PREDICTION
}

/**
 * Everything [TypingController] needs to know about the active language and field.
 * Supplied fresh on each use so a language or field change takes effect without
 * rebuilding the controller.
 */
internal class TypingProfile(
    /** Does this character belong inside a word, or does it terminate one? */
    val isWordCharacter: (String) -> Boolean,
    /** Buffer -> the text committed to the field. Identity for English. */
    val commitTransform: (String) -> String = { it },
    /** Where backspace cuts back to. Default: one character. */
    val lastUnitStart: (String) -> Int = { it.length - 1 },
    /** Applied to a standalone non-word character, e.g. Amharic "," -> "፣". */
    val transformStandalone: (String) -> String = { it },
    /** The script's caret-at-word-end rule; see [ResumableWord.latinWordEndingAtCursor]. */
    val wordEndingAtCursor: (before: String, after: String) -> String? = { _, _ -> null },
    /** May a committed word at the caret be re-opened for editing at all? */
    val allowsAdoption: Boolean = true,
    /**
     * True when the field text differs from the composing buffer, so adoption must
     * consult [ResumedWordMemory] instead of using the field text directly. Amharic
     * only: the field holds fidel, the buffer holds SERA Latin.
     */
    val remembersRawLatin: Boolean = false
)
