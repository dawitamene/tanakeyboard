package com.addiyon.keyboard.transliteration

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransliterationPropertyTest {
    @Test
    fun everyFamilyRendersEveryVowelOrderAndLabializedFallback() {
        AmharicTable.families.forEach { (spelling, family) ->
            AmharicTable.vowels.forEach { (vowel, index) ->
                val expected = when (index) {
                    AmharicTable.UA_INDEX ->
                        family.ua?.toString() ?: family.bare + Transliterator.transliterate("ua")
                    else -> family.forms[index].toString()
                }
                assertEquals(
                    "$spelling$vowel",
                    expected,
                    Transliterator.transliterate(spelling + vowel)
                )
            }
            assertEquals(
                spelling,
                family.bare.toString(),
                Transliterator.transliterate(spelling)
            )
        }
    }

    @Test
    fun normalizationIsIdempotentAcrossTheEntireEthiopicBlock() {
        val input = buildString {
            for (codePoint in 0x1200..0x137F) append(codePoint.toChar())
        }
        val once = EthiopicNormalizer.normalize(input)

        assertEquals(once, EthiopicNormalizer.normalize(once))
    }

    @Test
    fun validUnicodeInputsAlwaysProduceWellFormedUtf16() {
        val random = Random(0xADD1)
        val alphabet = listOf(
            "a", "e", "i", "o", "u", "h", "H", "t", "T", "s", "S",
            "c", "C", "g", "n", "'", "`", " ", "\n", ".", ",", "ሰ", "።",
            "🙂", "👩🏽", "́"
        )

        repeat(1_000) {
            val input = buildString {
                repeat(random.nextInt(0, 80)) {
                    append(alphabet[random.nextInt(alphabet.size)])
                }
            }
            val first = Transliterator.transliterate(input)
            val second = Transliterator.transliterate(input)

            assertEquals("seed=0xADD1 iteration=$it", first, second)
            assertFalse("seed=0xADD1 iteration=$it", hasUnpairedSurrogate(first))
        }
    }

    @Test
    fun unknownSymbolsAndMalformedSurrogatesPassThroughWithoutLoss() {
        val input = "\uD83D#\uDC00\t\u0000"

        assertEquals(input, Transliterator.transliterate(input))
    }

    @Test
    fun hardBoundariesPreventMatchesFromCrossingBetweenSegments() {
        val random = Random(0x51A7)
        val letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val boundaries = listOf(" ", "\n", "\t", "#", "🙂")

        repeat(500) {
            val left = buildString {
                repeat(random.nextInt(0, 20)) {
                    append(letters[random.nextInt(letters.length)])
                }
            }
            val right = buildString {
                repeat(random.nextInt(0, 20)) {
                    append(letters[random.nextInt(letters.length)])
                }
            }
            val boundary = boundaries[random.nextInt(boundaries.size)]

            assertEquals(
                "seed=0x51A7 iteration=$it",
                Transliterator.transliterate(left) +
                    boundary +
                    Transliterator.transliterate(right),
                Transliterator.transliterate(left + boundary + right)
            )
        }
    }

    private fun hasUnpairedSurrogate(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                Character.isHighSurrogate(char) -> {
                    if (
                        index + 1 >= value.length ||
                        !Character.isLowSurrogate(value[index + 1])
                    ) {
                        return true
                    }
                    index += 2
                }
                Character.isLowSurrogate(char) -> return true
                else -> index += 1
            }
        }
        return false
    }
}
