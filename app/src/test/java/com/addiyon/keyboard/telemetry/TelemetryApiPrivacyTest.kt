package com.addiyon.keyboard.telemetry

import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryApiPrivacyTest {
    @Test
    fun publicFacadeAcceptsNoContentBearingTypes() {
        val forbiddenNames = setOf(
            "java.lang.String",
            "java.lang.CharSequence",
            "android.os.Bundle",
            "android.view.inputmethod.EditorInfo",
            "android.view.inputmethod.InputConnection",
            "java.util.Map"
        )
        val methods = Telemetry::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }

        assertTrue(methods.isNotEmpty())
        methods.forEach { method ->
            method.parameterTypes.forEach { parameter ->
                assertFalse(
                    "${method.name} accepts ${parameter.name}",
                    parameter.name in forbiddenNames ||
                        forbiddenNames.any { forbidden -> forbidden in parameter.interfaces.map { it.name } }
                )
            }
        }
    }

    @Test
    fun firebaseReferencesAreConfinedToTheBackend() {
        val root = sourceRoot()
        val referencingFiles = root.walkTopDown()
            .filter(File::isFile)
            .filter { it.extension == "kt" }
            .filter { it.readText().contains("com.google.firebase.") }
            .map { it.name }
            .toSet()

        assertEquals(setOf("FirebaseTelemetryBackend.kt"), referencingFiles)
    }

    @Test
    fun facadeSourceContainsNoGenericContentApi() {
        val facade = File(sourceRoot(), "telemetry/Telemetry.kt").readText()
        listOf(
            "String",
            "CharSequence",
            "Bundle",
            "EditorInfo",
            "InputConnection",
            "Map<",
            "logEvent",
            "setUserId"
        ).forEach { forbidden ->
            assertFalse(forbidden, facade.contains(forbidden))
        }
    }

    @Test
    fun safeLogForwardsOnlyCategoryAndThrowable() {
        val safeLog = File(sourceRoot(), "SafeLogging.kt").readText()

        assertTrue(safeLog.contains("Telemetry.recordNonFatal(category, t)"))
        assertFalse(safeLog.contains("Telemetry.recordNonFatal(category, msg"))
        assertFalse(safeLog.contains("Telemetry.recordNonFatal(msg"))
        assertTrue(safeLog.contains("if (BuildConfig.DEBUG)"))
        assertTrue(safeLog.contains("android.util.Log.e(TAG, category.name)"))
        assertTrue(safeLog.contains("if (BuildConfig.DEBUG) msg else \"warning\""))
    }

    @Test
    fun rawKeySpaceDeleteAndCursorCallbacksContainNoTelemetryCalls() {
        val service = sourceFile("AddiyonKeyboardService.kt").readText()

        listOf(
            "onCharacter",
            "onDelete",
            "onSpace",
            "onUpdateSelection"
        ).forEach { functionName ->
            functionBodies(service, functionName).forEach { body ->
                assertNoTelemetryCalls("$functionName callback", body)
            }
        }
    }

    @Test
    fun transliterationCompositionCursorAndEmailContentPathsContainNoTelemetryCalls() {
        val root = sourceRoot()
        val contentPathFiles = buildList {
            addAll(kotlinFiles(File(root, "transliteration")))
            addAll(kotlinFiles(File(root, "composing")))
            add(File(root, "EditorGateway.kt"))
            add(File(root, "suggestion/EmailSuggestions.kt"))
        }

        contentPathFiles.forEach { file ->
            assertNoTelemetryCalls(file.relativeTo(root).path, file.readText())
        }

        val service = sourceFile("AddiyonKeyboardService.kt").readText()
        listOf(
            "publishEmailSuggestions",
            "isEmailWordCharacter"
        ).forEach { functionName ->
            functionBodies(service, functionName).forEach { body ->
                assertNoTelemetryCalls("$functionName path", body)
            }
        }
    }

    @Test
    fun voiceTranscriptPathsNeverForwardRecognizedTextToTelemetry() {
        val root = sourceRoot()
        val service = sourceFile("AddiyonKeyboardService.kt").readText()
        val controller = File(root, "voice/VoiceInputController.kt").readText()
        val platform = File(root, "voice/VoicePlatform.kt").readText()
        val composer = File(root, "voice/VoiceComposer.kt").readText()

        assertNoTelemetryCalls(
            "onVoicePartialResult transcript path",
            functionBodies(service, "onVoicePartialResult").single()
        )
        val finalResultBody = functionBodies(service, "onVoiceFinalResult").single()
        assertEquals(
            listOf("voiceFinished"),
            telemetryCallNames(finalResultBody)
        )
        assertTrue(
            finalResultBody.contains(
                "Telemetry.voiceFinished(TelemetryVoiceResult.COMPLETED, isPrivateField)"
            )
        )

        listOf("onResults", "onPartialResults").forEach { functionName ->
            (
                functionBodies(controller, functionName) +
                    functionBodies(platform, functionName)
                ).forEach { body ->
                assertNoTelemetryCalls("voice $functionName transcript path", body)
            }
        }
        functionBodies(platform, "bestHypothesis").forEach { body ->
            assertNoTelemetryCalls("voice bestHypothesis transcript path", body)
        }
        assertNoTelemetryCalls("VoiceComposer transcript path", composer)
    }

    @Test
    fun suggestionAcceptanceLogsOnlyEnumKindAndPrivateFieldState() {
        val service = sourceFile("AddiyonKeyboardService.kt").readText()
        val tapBodies = functionBodies(service, "onSuggestionTapped")

        assertEquals(
            listOf("suggestionAccepted"),
            tapBodies.flatMap(::telemetryCallNames)
        )
        assertTrue(
            tapBodies.any {
                it.contains("Telemetry.suggestionAccepted(acceptedKind, isPrivateField)")
            }
        )
    }

    private fun assertNoTelemetryCalls(label: String, source: String) {
        val calls = telemetryCallNames(source)
        assertTrue("$label contains $calls", calls.isEmpty())
    }

    private fun telemetryCallNames(source: String): List<String> =
        Regex("""\bTelemetry\s*\.\s*([A-Za-z_][A-Za-z0-9_]*)""")
            .findAll(source)
            .map { it.groupValues[1] }
            .toList()

    private fun kotlinFiles(directory: File): List<File> =
        if (!directory.isDirectory) {
            emptyList()
        } else {
            directory.walkTopDown()
                .filter(File::isFile)
                .filter { it.extension == "kt" }
                .toList()
        }

    private fun sourceFile(relativePath: String): File =
        File(sourceRoot(), relativePath).also {
            check(it.isFile) { "Missing source file: $it" }
        }

    private fun functionBodies(source: String, functionName: String): List<String> {
        val declaration = Regex(
            """\bfun\s+${Regex.escape(functionName)}\s*\("""
        )
        return declaration.findAll(source).map { match ->
            val bodyStart = source.indexOf('{', match.range.last + 1)
            check(bodyStart >= 0) { "Missing body for $functionName" }
            var depth = 0
            var bodyEnd = -1
            for (index in bodyStart until source.length) {
                when (source[index]) {
                    '{' -> depth += 1
                    '}' -> {
                        depth -= 1
                        if (depth == 0) {
                            bodyEnd = index
                            break
                        }
                    }
                }
            }
            check(bodyEnd >= 0) { "Unterminated body for $functionName" }
            source.substring(bodyStart, bodyEnd + 1)
        }.toList().also {
            check(it.isNotEmpty()) { "Missing function: $functionName" }
        }
    }

    private fun sourceRoot(): File =
        listOf(
            File("src/main/java/com/addiyon/keyboard"),
            File("app/src/main/java/com/addiyon/keyboard")
        ).first(File::isDirectory)
}
