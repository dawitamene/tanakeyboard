package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailSuggestionsTest {

    private fun chips(token: String): List<EmailChip> =
        EmailSuggestions.emailChipsFor(token)

    @Test
    fun emptyTokenReturnsNoChips() {
        assertEquals(emptyList<EmailChip>(), chips(""))
    }

    @Test
    fun whitespaceOnlyTokenReturnsNoChips() {
        assertEquals(emptyList<EmailChip>(), chips(" "))
        assertEquals(emptyList<EmailChip>(), chips("\t"))
    }

    @Test
    fun tokenWithInternalWhitespaceReturnsNoChips() {
        // Whitespace terminates the email token at parse time; a single
        // token never spans a space.
        assertEquals(emptyList<EmailChip>(), chips("a b"))
    }

    @Test
    fun stage1ReturnsThreeProviderDomains() {
        val list = chips("john")
        assertEquals(3, list.size)
        assertEquals(EmailChip("@gmail.com", "john@gmail.com"), list[0])
        assertEquals(EmailChip("@yahoo.com", "john@yahoo.com"), list[1])
        assertEquals(EmailChip("@outlook.com", "john@outlook.com"), list[2])
    }

    @Test
    fun stage1PreservesLocalPartCasing() {
        val list = chips("Jo")
        assertEquals(3, list.size)
        assertEquals("Jo@gmail.com", list[0].commit)
        assertEquals("Jo@yahoo.com", list[1].commit)
        assertEquals("Jo@outlook.com", list[2].commit)
        // Display labels themselves never carry the local-part casing.
        assertEquals("@gmail.com", list[0].display)
    }

    @Test
    fun stage1WorksForSingleCharLocalPart() {
        // The chips are only shown after the user has typed >=1 char.
        val list = chips("j")
        assertEquals(3, list.size)
        assertEquals("j@gmail.com", list[0].commit)
    }

    @Test
    fun atSignAloneAlreadyTriggersStage2() {
        // As soon as '@' appears the strip switches to TLD suffixes.
        val list = chips("john@")
        assertEquals(3, list.size)
        assertEquals(EmailChip(".com", "john@.com"), list[0])
        assertEquals(EmailChip(".org", "john@.org"), list[1])
        assertEquals(EmailChip(".net", "john@.net"), list[2])
    }

    @Test
    fun stage2AppendsTldAfterTypedDomain() {
        val list = chips("john@mycomp")
        assertEquals(3, list.size)
        assertEquals(EmailChip(".com", "john@mycomp.com"), list[0])
        assertEquals(EmailChip(".org", "john@mycomp.org"), list[1])
        assertEquals(EmailChip(".net", "john@mycomp.net"), list[2])
    }

    @Test
    fun stage2NaivelyAppendsEvenIfTokenLooksComplete() {
        // Documented behavior: we don't merge/replace existing suffixes.
        // "john@mycomp.co" still has '@' so stage 2 applies; chip becomes
        // "john@mycomp.co.com" if tapped. The user typed it, we don't
        // second-guess.
        val list = chips("john@mycomp.co")
        assertTrue(list.isNotEmpty())
        assertEquals(".com", list[0].display)
        assertEquals("john@mycomp.co.com", list[0].commit)
    }

    @Test
    fun stage2MultipleAtSignsStillStage2() {
        // Multiple '@' is still routed to stage 2; the resulting commit is
        // malformed but we don't validate.
        val list = chips("a@@b")
        assertEquals(3, list.size)
        assertEquals(".com", list[0].display)
        assertEquals("a@@b.com", list[0].commit)
    }

    @Test
    fun savedAddressIsPrioritizedAndDoesNotAddASecondSuffix() {
        val list = EmailSuggestions.emailChipsFor(
            "jo",
            listOf("jo@example.com", "jordan@example.com")
        )
        assertEquals("jo@example.com", list[0].commit)
    }
}
