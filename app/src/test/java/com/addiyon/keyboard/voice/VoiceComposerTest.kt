package com.addiyon.keyboard.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceComposerTest {

    @Test
    fun firstPartialAfterLetterGetsLeadingSpace() {
        val composer = VoiceComposer()
        assertEquals(" hello", composer.updatePartial("hello", 'x'))
    }

    @Test
    fun firstPartialAfterSpaceOrEmptyFieldHasNoLeadingSpace() {
        assertEquals("hello", VoiceComposer().updatePartial("hello", ' '))
        assertEquals("hello", VoiceComposer().updatePartial("hello", null))
    }

    @Test
    fun successivePartialsReturnFullReplacementText() {
        val composer = VoiceComposer()
        assertEquals(" hello", composer.updatePartial("hello", 'x'))
        assertEquals(" hello world", composer.updatePartial("hello world", null))
    }

    @Test
    fun identicalPartialReturnsNull() {
        val composer = VoiceComposer()
        assertEquals("hello", composer.updatePartial("hello", null))
        assertNull(composer.updatePartial("hello", null))
        assertNull(composer.updatePartial(" hello ", null))
    }

    @Test
    fun blankPartialReturnsNullAndDoesNotOpenUtterance() {
        val composer = VoiceComposer()
        assertNull(composer.updatePartial("   ", 'x'))
        assertFalse(composer.isComposing)
    }

    @Test
    fun finalizeUsesPrefixFromUtteranceStart() {
        val composer = VoiceComposer()
        composer.updatePartial("hello", 'x')
        composer.updatePartial("hello world", null)
        val commit = composer.finalize("hello world", null)
        assertEquals(VoiceComposer.FinalCommit(" hello world", false), commit)
        assertFalse(composer.isComposing)
    }

    @Test
    fun finalizeWithBlankRawFallsBackToLastPartial() {
        val composer = VoiceComposer()
        composer.updatePartial("hello", null)
        assertEquals(
            VoiceComposer.FinalCommit("hello", false),
            composer.finalize(null, null)
        )
    }

    @Test
    fun finalizeWithNothingAtAllReturnsNull() {
        assertNull(VoiceComposer().finalize(null, 'x'))
        assertNull(VoiceComposer().finalize("   ", 'x'))
    }

    @Test
    fun refinedFinalOverridesLastPartial() {
        val composer = VoiceComposer()
        composer.updatePartial("hello were", 'x')
        assertEquals(
            VoiceComposer.FinalCommit(" hello world", false),
            composer.finalize("hello world", null)
        )
    }

    @Test
    fun punctuationFinalAfterWhitespaceRequestsSpaceDeletion() {
        val composer = VoiceComposer()
        assertEquals(
            VoiceComposer.FinalCommit(",", true),
            composer.finalize(",", ' ')
        )
    }

    @Test
    fun punctuationFinalAfterLetterGetsNoSpaceAndNoDeletion() {
        val composer = VoiceComposer()
        assertEquals(
            VoiceComposer.FinalCommit(",", false),
            composer.finalize(",", 'x')
        )
    }

    @Test
    fun amharicPunctuationBehavesLikeLatin() {
        assertEquals(
            VoiceComposer.FinalCommit("።", true),
            VoiceComposer().finalize("።", ' ')
        )
    }

    @Test
    fun normalizesWhitespace() {
        assertEquals("hello world", VoiceComposer.normalize("  hello   world  "))
        val composer = VoiceComposer()
        assertEquals("hello world", composer.updatePartial("  hello \n world ", null))
    }

    @Test
    fun nextUtteranceAfterFinalizeUsesFreshCharBefore() {
        val composer = VoiceComposer()
        composer.updatePartial("hello", null)
        composer.finalize("hello", null)
        // The previous commit ended with a letter, so the next utterance
        // needs its own leading space.
        assertEquals(" world", composer.updatePartial("world", 'o'))
    }

    @Test
    fun externalFinalizationResetsUtterance() {
        val composer = VoiceComposer()
        composer.updatePartial("hello", 'x')
        assertTrue(composer.isComposing)
        composer.onFinalizedExternally()
        assertFalse(composer.isComposing)
        assertNull(composer.finalize(null, null))
        assertEquals("world", composer.updatePartial("world", ' '))
    }

    @Test
    fun finalWithoutPartialsUsesCharBeforePassedToFinalize() {
        val composer = VoiceComposer()
        assertEquals(
            VoiceComposer.FinalCommit(" hello", false),
            composer.finalize("hello", 'x')
        )
    }

    @Test
    fun detectsStandalonePunctuation() {
        assertTrue(VoiceComposer.startsWithStandalonePunctuation("።"))
        assertTrue(VoiceComposer.startsWithStandalonePunctuation(", hello"))
        assertFalse(VoiceComposer.startsWithStandalonePunctuation("hello"))
    }
}
