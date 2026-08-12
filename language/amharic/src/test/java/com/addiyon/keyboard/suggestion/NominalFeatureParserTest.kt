package com.addiyon.keyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NominalFeatureParserTest {
    @Test
    fun parsesRealRecordsFromEveryNominalLexiconSource() {
        val cases = listOf(
            Triple(0, "'' [g=m,pos=N]", setOf(PartOfSpeech.NOUN)),
            Triple(0, "'' [pos=N|ADJ,-h]", setOf(PartOfSpeech.NOUN, PartOfSpeech.ADJECTIVE)),
            Triple(1, "'' [pos=N,t=[eng=\"teacher\"]]", setOf(PartOfSpeech.NOUN)),
            Triple(1, "'' [pos=ADJ,t=[eng=\"honored,respectful\"]]", setOf(PartOfSpeech.ADJECTIVE)),
            Triple(2, "", emptySet()),
            Triple(3, "", emptySet()),
        )

        cases.forEach { (kind, raw, expectedPartsOfSpeech) ->
            val parsed = NominalFeatureParser.parse(raw, kind)
            assertFalse("unexpected malformed record: $raw", parsed.malformed)
            assertEquals(expectedPartsOfSpeech, parsed.partsOfSpeech)
        }
    }

    @Test
    fun distinguishesAbsentPositiveNegativeAndEnumeratedFeatures() {
        val parsed = NominalFeatureParser.parse(
            "'' [+h,-pl,+def,pos=N|ADJ,p=sm2|sf2]",
            kind = 0,
        )

        assertEquals(FeatureState.POSITIVE, parsed.human)
        assertEquals(FeatureState.NEGATIVE, parsed.plural)
        assertEquals(FeatureState.POSITIVE, parsed.definite)
        assertEquals(FeatureState.ABSENT, parsed.accusative)
        assertEquals(PersonFeature.Values(setOf("sm2", "sf2")), parsed.person)
        assertEquals(setOf("N", "ADJ"), parsed.enumerated.getValue("pos"))
    }

    @Test
    fun mapsGenderStemClassAndRestrictionsWithoutSubstringChecks() {
        val ordinary = NominalFeatureParser.parse(
            "ራስ [g=f,pos=N,adp=በ|ለ|ከ|ስለ,-gen,-dis,-col,p=0]",
            kind = 0,
        )
        val alternate = NominalFeatureParser.parse("'' [pos=N]", kind = 1)

        assertEquals(Gender.FEMININE, ordinary.gender)
        assertEquals(StemClass.ORDINARY, ordinary.stemClass)
        assertEquals(setOf("በ", "ለ", "ከ", "ስለ"), ordinary.allowedAdpositions)
        assertFalse(ordinary.allowsGenitive)
        assertFalse(ordinary.allowsDistributive)
        assertFalse(ordinary.allowsCollective)
        assertEquals(PersonFeature.None, ordinary.person)
        assertEquals(StemClass.ALTERNATE_AN, alternate.stemClass)
    }

    @Test
    fun pZeroIsNotConfusedWithAnotherEnumeratedToken() {
        val parsed = NominalFeatureParser.parse("'' [pos=N,sp=0,ptype=dem]", kind = 0)

        assertEquals(PersonFeature.Absent, parsed.person)
        assertTrue("sp=0" in parsed.unknownFeatures)
        assertTrue("ptype=dem" in parsed.unknownFeatures)
    }

    @Test
    fun preservesNestedUnknownFeaturesContainingCommas() {
        val parsed = NominalFeatureParser.parse(
            "'' [pos=ADJ,t=[eng=\"modern,sophisticated,fashionable\"]]",
            kind = 1,
        )

        assertEquals(
            listOf("t=[eng=\"modern,sophisticated,fashionable\"]"),
            parsed.unknownFeatures,
        )
        assertEquals(
            setOf("[eng=\"modern,sophisticated,fashionable\"]"),
            parsed.enumerated.getValue("t"),
        )
    }

    @Test
    fun rejectsMalformedFeatureBlocksConservatively() {
        val missingClose = NominalFeatureParser.parse("'' [pos=N,-pl", kind = 0)
        val trailingGarbage = NominalFeatureParser.parse("'' [pos=N] garbage", kind = 0)

        assertTrue(missingClose.malformed)
        assertTrue(trailingGarbage.malformed)
    }
}
