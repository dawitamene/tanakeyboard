package com.addiyon.keyboard.composing

import com.addiyon.keyboard.EditorGateway
import com.addiyon.keyboard.testing.EditorPersonality
import com.addiyon.keyboard.testing.FakeEditor
import com.addiyon.keyboard.testing.SelectionUpdateDelivery
import com.addiyon.keyboard.transliteration.Transliterator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The behavioural contract for every edit the keyboard makes, asserted on the
 * DOCUMENT rather than on the calls that produced it.
 *
 * Each scenario runs against every [EditorPersonality] and every
 * [SelectionUpdateDelivery] — the axes along which real editors differ, and
 * therefore the axes along which the reported bugs appeared in some apps and not
 * others. A behaviour is only correct here if it is correct in all of them.
 */
class TypingControllerTest {

    // ------------------------------------------------------------ typing

    @Test
    fun typingAWordComposesItAndSpaceCommitsIt() = everywhere { driver ->
        "cool".forEach(driver::type)
        assertEquals("cool", driver.text)
        driver.space()
        assertEquals("cool ", driver.text)
        "breeze".forEach(driver::type)
        driver.space()
        assertEquals("cool breeze ", driver.text)
    }

    /**
     * The first reported bug. A caret dropped into the middle of a run-together
     * word, then space, used to scatter characters the user never typed.
     */
    @Test
    fun spaceAfterMovingTheCaretIntoAWordSplitsItCleanly() = everywhereCaretIsReported { driver ->
        "coolbree".forEach(driver::type)
        driver.tap(4)
        driver.space()
        assertEquals("cool bree", driver.text)
    }

    /**
     * The second half of that report: after the split, putting the caret back at
     * the end of a word must resume it rather than start something unrelated.
     */
    @Test
    fun caretAtAWordEndResumesThatWord() = everywhereCaretIsReported { driver ->
        "coolbree".forEach(driver::type)
        driver.tap(4)
        driver.space()
        driver.tap(driver.text.length)
        driver.type('z')
        assertEquals("cool breez", driver.text)
        assertEquals("breez", driver.buffer)
    }

    @Test
    fun caretInsideAWordTypesPlainlyAndComposesNothing() = everywhereCaretIsReported { driver ->
        driver.reset("cool breeze here")
        driver.tap(2)
        driver.type('x')
        assertEquals("coxol breeze here", driver.text)
        assertFalse(driver.isComposing)
    }

    @Test
    fun caretAtAWordEndInTheMiddleOfADocumentResumesWithoutDisturbingNeighbours() =
        everywhereCaretIsReported { driver ->
            driver.reset("cool breeze here")
            driver.tap(11)
            driver.type('s')
            assertEquals("cool breezes here", driver.text)
            assertEquals("breezes", driver.buffer)
        }

    @Test
    fun typingBeforeAWordDoesNotAdoptIt() = everywhereCaretIsReported { driver ->
        driver.reset("breeze")
        driver.tap(0)
        driver.type('x')
        assertEquals("xbreeze", driver.text)
    }

    // ------------------------------------------------------------ deletion

    @Test
    fun backspaceShrinksTheComposingWordOneCharacterAtATime() = everywhere { driver ->
        "cool".forEach(driver::type)
        driver.backspace()
        assertEquals("coo", driver.text)
        driver.backspace()
        driver.backspace()
        driver.backspace()
        assertEquals("", driver.text)
        assertFalse(driver.isComposing)
    }

    @Test
    fun backspaceIntoCommittedTextResumesTheWordItLandsIn() = everywhereCaretIsReported { driver ->
        driver.reset("cool breeze")
        driver.tap(11)
        driver.backspace()
        assertEquals("cool breez", driver.text)
        assertEquals("breez", driver.buffer)
    }

    @Test
    fun backspaceDeletesARangeSelection() = everywhereCaretIsReported { driver ->
        driver.reset("cool breeze here")
        driver.select(5, 12)
        driver.backspace()
        assertEquals("cool here", driver.text)
    }

    // ------------------------------------------------------- suggestion chips

    @Test
    fun tappingACompletionReplacesTheComposingWord() = everywhere { driver ->
        driver.reset("say ")
        "hel".forEach(driver::type)
        driver.completion("hello")
        assertEquals("say hello ", driver.text)
    }

    /**
     * The case the offset-based path could not do: the word is committed text the
     * caret merely sits at the end of, so replacing it used to need absolute span
     * offsets — and silently did nothing in every editor whose reads could not be
     * cross-checked.
     */
    @Test
    fun tappingACompletionReplacesACommittedWordAtTheCaret() = everywhereCaretIsReported { driver ->
        driver.reset("cool breeze here")
        driver.tap(11)
        driver.completion("breezes")
        assertEquals("cool breezes  here", driver.text)
    }

    @Test
    fun tappingACompletionDeepInALongDocumentReplacesOnlyThatWord() = everywhereCaretIsReported { driver ->
        val prose = "the quick brown fox jumps over the lazy dog while " +
            "another lazy dog watches quietly from the porch step"
        driver.reset(prose)
        val caret = prose.indexOf("watches") + "watches".length
        driver.tap(caret)
        driver.completion("watched")
        assertEquals(
            prose.replaceRange(caret - "watches".length, caret, "watched "),
            driver.text
        )
    }

    @Test
    fun tappingAPredictionInsertsAndReplacesNothing() = everywhereCaretIsReported { driver ->
        driver.reset("cool breeze ")
        driver.tap(12)
        driver.prediction("today")
        assertEquals("cool breeze today ", driver.text)
    }

    // ------------------------------------------- cursor moves never mutate text

    /**
     * The load-bearing invariant. Cursor movement may finalize a composition but
     * must never add, remove or replace a character — which is exactly what the
     * "spaces disappeared" and "random words appeared" reports describe.
     */
    @Test
    fun noSequenceOfCaretMovesEverChangesTheDocument() = everywhereCaretIsReported { driver ->
        driver.reset("cool breeze here")
        val before = driver.text
        for (position in 0..before.length) {
            driver.tap(position)
        }
        for (position in before.length downTo 0) {
            driver.tap(position)
        }
        driver.select(2, 9)
        driver.tap(4)
        assertEquals(before, driver.text)
    }

    @Test
    fun caretMovesWhileComposingFinalizeWithoutLosingTheWord() = everywhereCaretIsReported { driver ->
        driver.reset("say ")
        "hello".forEach(driver::type)
        driver.tap(0)
        assertEquals("say hello", driver.text)
        assertFalse(driver.isComposing)
    }

    @Test
    fun endingTheSessionFreezesTheWordInPlace() = everywhere { driver ->
        "cool".forEach(driver::type)
        driver.finishInput()
        assertEquals("cool", driver.text)
    }

    @Test
    fun startingANewSessionNeverTouchesTheOutgoingField() = everywhere { driver ->
        "cool".forEach(driver::type)
        val before = driver.text
        driver.startInput()
        assertEquals(before, driver.text)
        assertFalse(driver.isComposing)
    }

    // ------------------------------------------------------------- Amharic

    @Test
    fun amharicCommitsFidelWhileComposingRawLatin() = everywhereAmharic { driver ->
        "selam".forEach(driver::type)
        assertEquals("selam", driver.text)
        driver.space()
        assertEquals(Transliterator.transliterate("selam") + " ", driver.text)
    }

    /**
     * Amharic parity for committed-word resume. The field holds fidel and the
     * buffer must hold the SERA Latin it came from, so adoption consults what this
     * keyboard actually composed rather than guessing a reverse transliteration.
     */
    @Test
    fun amharicResumesACommittedFidelWordAsItsOriginalLatin() = everywhereAmharic { driver ->
        "selam".forEach(driver::type)
        driver.space()
        val fidel = Transliterator.transliterate("selam")
        assertEquals("$fidel ", driver.text)

        driver.tap(fidel.length)
        driver.type('u')
        // Amharic composes raw Latin in the field, so the re-opened word shows as
        // Latin again -- and the space committed earlier is untouched.
        assertEquals("selamu", driver.buffer)
        assertEquals("selamu ", driver.text)
    }

    /** A fidel word this keyboard never composed has no known Latin: never guess. */
    @Test
    fun amharicNeverAdoptsFidelItDidNotCompose() = everywhereAmharic { driver ->
        driver.reset("ሰላም")
        driver.tap(3)
        driver.type('u')
        assertEquals("ሰላምu", driver.text)
        assertEquals("u", driver.buffer)
    }

    /**
     * The invariant, stated as a property rather than a scenario: across a long
     * random mix of typing, deletion, caret moves, selections and chip taps, the
     * document only ever changes in ways an explicit key action asked for.
     *
     * Caret moves and selections are interleaved with real edits, so this covers
     * the orderings a hand-written scenario would never think to try -- a caret
     * move landing between a commit and its echo, a chip tap immediately after a
     * selection change, a backspace straight into an adopted word.
     */
    @Test
    fun randomInteractionNeverCorruptsTheDocument() {
        val words = listOf("cool", "breeze", "here", "and", "there")
        for (personality in EditorPersonality.entries) {
            if (personality == EditorPersonality.REJECTS_WRITES) continue
            for (seed in 0 until 40) {
                val random = java.util.Random(seed.toLong())
                // DROPPED is excluded for the same reason as in
                // everywhereCaretIsReported: this property moves the caret, and an
                // editor that never reports a selection change makes that move
                // unobservable to any IME.
                val deliveries = SelectionUpdateDelivery.entries
                    .filter { it != SelectionUpdateDelivery.DROPPED }
                val driver = ControllerDriver(
                    personality,
                    deliveries[seed % deliveries.size],
                    amharic = false
                )
                driver.reset("cool breeze here")
                // Mirrors the document independently: only explicit edits touch it.
                var expectedLength = driver.text.length
                repeat(60) {
                    when (random.nextInt(6)) {
                        0 -> {
                            driver.tap(random.nextInt(driver.text.length + 1))
                        }
                        1 -> {
                            val a = random.nextInt(driver.text.length + 1)
                            val b = random.nextInt(driver.text.length + 1)
                            driver.select(minOf(a, b), maxOf(a, b))
                        }
                        2 -> {
                            // Typing over a range selection replaces it, so the
                            // expected delta is one character minus whatever the
                            // selection covered.
                            val replaced = driver.selectionLength
                            driver.type(('a' + random.nextInt(26)))
                            expectedLength += 1 - replaced
                        }
                        3 -> {
                            // Backspace removes a selection, one character, or
                            // nothing at all at the very start of the field, so
                            // its delta is read back rather than predicted.
                            val lengthBefore = driver.text.length
                            driver.backspace()
                            expectedLength -= lengthBefore - driver.text.length
                        }
                        4 -> {
                            val replaced = driver.selectionLength
                            driver.space()
                            expectedLength += 1 - replaced
                        }
                        5 -> {
                            val word = words[random.nextInt(words.size)]
                            val lengthBefore = driver.text.length
                            driver.completion(word)
                            expectedLength += driver.text.length - lengthBefore
                        }
                    }
                    assertEquals(
                        "$personality seed=$seed: document length drifted from the " +
                            "edits actually requested",
                        expectedLength,
                        driver.text.length
                    )
                }
            }
        }
    }

    // --------------------------------------------------------------- harness

    private fun everywhere(scenario: (ControllerDriver) -> Unit) =
        matrix(amharic = false, needsCaretReports = false, scenario)

    private fun everywhereAmharic(scenario: (ControllerDriver) -> Unit) =
        matrix(amharic = true, needsCaretReports = false, scenario)

    /**
     * For scenarios whose premise is "the user moved the caret". An editor that
     * never reports a selection change cannot be tracked by any IME — the caret
     * move is unobservable — so [SelectionUpdateDelivery.DROPPED] is excluded
     * rather than asserted against. Every other delivery mode still applies:
     * deferred, coalesced and batch-end reporting are all real, and the behaviour
     * must hold under each.
     */
    private fun everywhereCaretIsReported(scenario: (ControllerDriver) -> Unit) =
        matrix(amharic = false, needsCaretReports = true, scenario)

    private fun matrix(
        amharic: Boolean,
        needsCaretReports: Boolean,
        scenario: (ControllerDriver) -> Unit
    ) {
        val failures = mutableListOf<String>()
        for (personality in EditorPersonality.entries) {
            if (personality == EditorPersonality.REJECTS_WRITES) continue
            for (delivery in SelectionUpdateDelivery.entries) {
                if (needsCaretReports && delivery == SelectionUpdateDelivery.DROPPED) continue
                val driver = ControllerDriver(personality, delivery, amharic)
                try {
                    scenario(driver)
                } catch (t: AssertionError) {
                    failures += "$personality/$delivery: ${t.message}"
                }
            }
        }
        if (failures.isNotEmpty()) {
            throw AssertionError(
                "${failures.size} editor configurations diverge:\n" + failures.joinToString("\n")
            )
        }
    }
}

/** Drives a real [TypingController] over a real in-memory document. */
private class ControllerDriver(
    private val personality: EditorPersonality,
    private val delivery: SelectionUpdateDelivery,
    private val amharic: Boolean
) {
    private lateinit var editor: FakeEditor
    private lateinit var gateway: EditorGateway
    private lateinit var controller: TypingController

    init {
        reset("")
    }

    val text: String get() = editor.text
    val buffer: String get() = controller.buffer
    val isComposing: Boolean get() = controller.isComposing
    val hasSelection: Boolean get() = selectionLength > 0
    val selectionLength: Int get() = kotlin.math.abs(editor.selectionEnd - editor.selectionStart)

    fun reset(initial: String) {
        editor = FakeEditor(initial, personality = personality, delivery = delivery)
        gateway = EditorGateway(connectionProvider = { editor })
        controller = TypingController(editor = gateway, profile = ::profile)
        editor.onSelectionUpdate { update ->
            controller.onSelectionChanged(
                selectionStart = update.newSelStart,
                selectionEnd = update.newSelEnd,
                candidatesStart = update.candidatesStart,
                candidatesEnd = update.candidatesEnd
            )
        }
        gateway.beginSession(editor.selectionStart, editor.selectionEnd)
        controller.onStartInput()
    }

    private fun profile(): TypingProfile = if (amharic) {
        TypingProfile(
            isWordCharacter = { it.all(Char::isLetter) },
            commitTransform = Transliterator::transliterate,
            transformStandalone = Transliterator::transliterate,
            wordEndingAtCursor = ResumableWord::amharicWordEndingAtCursor,
            remembersRawLatin = true
        )
    } else {
        TypingProfile(
            isWordCharacter = { it.all { char -> char.isLetter() || char == '\'' } },
            wordEndingAtCursor = ResumableWord::latinWordEndingAtCursor
        )
    }

    fun type(char: Char) {
        controller.onCharacter(char.toString())
        editor.flush()
    }

    fun space() {
        controller.onSpace()
        editor.flush()
    }

    fun backspace() {
        controller.onBackspace()
        editor.flush()
    }

    fun completion(word: String) {
        controller.onSuggestionTap(word, SuggestionKind.COMPLETION)
        editor.flush()
    }

    fun prediction(word: String) {
        controller.onSuggestionTap(word, SuggestionKind.PREDICTION)
        editor.flush()
    }

    fun tap(position: Int) {
        editor.setSelection(position, position)
        editor.flush()
    }

    fun select(start: Int, end: Int) {
        editor.setSelection(start, end)
        editor.flush()
    }

    fun startInput() {
        controller.onStartInput()
        editor.flush()
    }

    fun finishInput() {
        controller.onFinishInput()
        editor.flush()
    }
}
