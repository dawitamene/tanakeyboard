package com.addiyon.keyboard.suggestion

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AmharicVerbLexiconTest {
    @Test
    fun productionArtifactContainsHornMorphoRulesAndNoGeneratedSurfaces() {
        val runtime = productionRuntime()

        assertEquals(89_365, runtime.stateCount)
        assertEquals(331_733, runtime.transitionCount)
        assertEquals(27_531, runtime.ruleWeightCount)
        assertTrue(runtime.constraintPageCount > 0)
        assertEquals(0, runtime.generatedSurfaceCount)
    }

    @Test
    fun exactAnalysisRunsHornMorphoRulesAgainstTheBaseRootLexicon() {
        val runtime = productionRuntime()
        val terminal = requireNotNull(runtime.exact("የሚከተሉትን"))

        assertEquals("የሚከተሉትን", terminal.surface)
        assertTrue(terminal.analyses.any { it.root == "ክትል" })
        assertTrue(terminal.bestAnalysis.lemmaId.startsWith("verb:"))
        assertTrue(terminal.bestAnalysis.analysisId.startsWith(terminal.bestAnalysis.lemmaId))
    }

    @Test
    fun runtimeSupportsHornMorphoFeatureFamiliesMissingFromTheOldSurfaceSlice() {
        val runtime = productionRuntime()
        val examples = listOf(
            "አየኝ",
            "ሰጠልኝ",
            "ስለሄደ",
            "አስተማረ",
            "ተሰበረ",
            "ሄዶ",
            "ሄዷል",
            "እሄዳለሁ",
        )

        examples.forEach { surface ->
            assertTrue("HornMorpho runtime rejected $surface", runtime.exact(surface) != null)
        }
    }

    @Test
    fun prefixCompletionGeneratesTheRequestedRelativeObjectFormOnDevice() {
        val runtime = productionRuntime()
        val completions = runtime.complete("የሚከተ", 15)

        assertTrue(completions.size <= 15)
        assertTrue(
            completions.joinToString { it.surface },
            completions.any { it.surface == "የሚከተሉትን" },
        )
        assertTrue(completions.all { it.surface.startsWith("የሚከተ") })
    }

    @Test
    fun exactLookupRejectsUnlicensedSurfaces() {
        val runtime = productionRuntime()

        assertNull(runtime.exact("ሃሃሃ"))
        assertNull(runtime.exact("የሚከተሉትንሃሃ"))
    }

    @Test
    fun versionChecksumAndBoundsFailuresAreFailClosed() {
        val original = productionBytes()
        expectFailure(original.clone().also { it[5] = 2 }, "unsupported HornMorpho runtime")
        expectFailure(
            original.clone().also { it[it.lastIndex] = (it.last() + 1).toByte() },
            "HornMorpho runtime checksum mismatch",
        )
        val invalid = original.clone().also { bytes ->
            ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(32, Int.MAX_VALUE)
            val checksum = CRC32().apply { update(bytes, 112, bytes.size - 112) }.value.toInt()
            ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(68, checksum)
        }
        expectFailure(invalid, "invalid HornMorpho runtime records")
        assertFalse(AmharicVerbLexicon.EMPTY.isEnabled)
        assertTrue(AmharicVerbLexicon.EMPTY.complete("ለ", 8).isEmpty())
    }

    private fun productionRuntime() = AmharicVerbLexicon.fromBytes(productionBytes())

    private fun productionBytes(): ByteArray = listOf(
        File("src/main/assets/amharic_verbs.ahrf"),
        File("language/amharic/src/main/assets/amharic_verbs.ahrf"),
    ).first(File::isFile).readBytes()

    private fun expectFailure(bytes: ByteArray, expectedMessage: String) {
        try {
            AmharicVerbLexicon.fromBytes(bytes)
            fail("expected production artifact validation to fail")
        } catch (failure: IllegalArgumentException) {
            assertEquals(expectedMessage, failure.message)
        }
    }
}
