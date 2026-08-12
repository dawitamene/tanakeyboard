package com.addiyon.keyboard.model

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class EnterActionPolicyTest {
    @Test
    fun everySupportedEditorActionResolvesToItsMatchingKeyAction() {
        val cases = mapOf(
            EditorInfo.IME_ACTION_GO to EnterAction.GO,
            EditorInfo.IME_ACTION_SEARCH to EnterAction.SEARCH,
            EditorInfo.IME_ACTION_SEND to EnterAction.SEND,
            EditorInfo.IME_ACTION_NEXT to EnterAction.NEXT,
            EditorInfo.IME_ACTION_PREVIOUS to EnterAction.PREVIOUS,
            EditorInfo.IME_ACTION_DONE to EnterAction.DONE
        )

        cases.forEach { (actionId, expected) ->
            assertEquals(
                EnterActionResolution(expected, actionId),
                EnterActionPolicy.resolve(InputType.TYPE_CLASS_TEXT, actionId)
            )
        }
    }

    @Test
    fun unspecifiedNoneAndUnknownActionsResolveToNewline() {
        listOf(
            EditorInfo.IME_ACTION_UNSPECIFIED,
            EditorInfo.IME_ACTION_NONE,
            15
        ).forEach { actionId ->
            assertEquals(
                EnterAction.NEWLINE,
                EnterActionPolicy.resolve(InputType.TYPE_CLASS_TEXT, actionId).action
            )
        }
    }

    @Test
    fun noEnterActionFlagOverridesEveryDeclaredAction() {
        listOf(
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_PREVIOUS,
            EditorInfo.IME_ACTION_DONE
        ).forEach { actionId ->
            val resolution = EnterActionPolicy.resolve(
                InputType.TYPE_CLASS_TEXT,
                actionId or EditorInfo.IME_FLAG_NO_ENTER_ACTION
            )

            assertEquals(EnterAction.NEWLINE, resolution.action)
            assertEquals(actionId, resolution.editorActionId)
        }
    }

    @Test
    fun explicitActionWinsWhenHostAlsoSetsMultilineFlag() {
        val multiline = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE

        listOf(
            EditorInfo.IME_ACTION_GO to EnterAction.GO,
            EditorInfo.IME_ACTION_SEARCH to EnterAction.SEARCH,
            EditorInfo.IME_ACTION_SEND to EnterAction.SEND,
            EditorInfo.IME_ACTION_NEXT to EnterAction.NEXT,
            EditorInfo.IME_ACTION_PREVIOUS to EnterAction.PREVIOUS,
            EditorInfo.IME_ACTION_DONE to EnterAction.DONE
        ).forEach { (actionId, expected) ->
            assertEquals(
                expected,
                EnterActionPolicy.resolve(multiline, actionId).action
            )
        }
    }

    @Test
    fun multilineWithoutAnExplicitActionResolvesToNewline() {
        val multiline = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE

        assertEquals(
            EnterAction.NEWLINE,
            EnterActionPolicy.resolve(multiline, EditorInfo.IME_ACTION_UNSPECIFIED).action
        )
        assertEquals(
            EnterAction.NEWLINE,
            EnterActionPolicy.resolve(multiline, EditorInfo.IME_ACTION_NONE).action
        )
    }

    @Test
    fun unrelatedImeFlagsDoNotChangeTheMaskedAction() {
        val options = EditorInfo.IME_ACTION_SEARCH or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN

        assertEquals(
            EnterActionResolution(EnterAction.SEARCH, EditorInfo.IME_ACTION_SEARCH),
            EnterActionPolicy.resolve(InputType.TYPE_CLASS_TEXT, options)
        )
    }

    @Test
    fun actionPolicyIsIndependentOfTextAndNumericFieldClass() {
        assertEquals(
            EnterAction.DONE,
            EnterActionPolicy.resolve(
                InputType.TYPE_CLASS_NUMBER,
                EditorInfo.IME_ACTION_DONE
            ).action
        )
        assertEquals(
            EnterAction.GO,
            EnterActionPolicy.resolve(
                InputType.TYPE_CLASS_PHONE,
                EditorInfo.IME_ACTION_GO
            ).action
        )
    }

    @Test
    fun defaultResolutionIsAPlainUnspecifiedNewline() {
        assertEquals(
            EnterActionResolution(
                EnterAction.NEWLINE,
                EditorInfo.IME_ACTION_UNSPECIFIED
            ),
            EnterActionPolicy.default
        )
    }
}
