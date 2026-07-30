package com.addiyon.keyboard.composing

import android.view.inputmethod.InputConnection
import com.addiyon.keyboard.EditorToken
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CommittedWordSnapshotTest {
    @Test
    fun snapshotCarriesTheCompleteEditorAndPolicyScope() {
        val connection = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java)
        ) { _, _, _ -> null } as InputConnection
        val token = EditorToken(
            generation = 7,
            selectionGeneration = 11,
            selectionStart = 4,
            selectionEnd = 4,
            connection = connection
        )
        val snapshot = CommittedWordSnapshot(
            editorToken = token,
            selectionStart = 4,
            selectionEnd = 4,
            spanStart = 1,
            spanEnd = 6,
            word = "hello",
            cursorOffset = 3,
            isAmharic = false,
            isEmailField = true,
            isPrivateField = false,
            isNumberMode = false,
            source = CommittedWordSource.EXPLICIT_DELETE
        )

        assertSame(token, snapshot.editorToken)
        assertEquals(4, snapshot.selectionStart)
        assertEquals(4, snapshot.selectionEnd)
        assertEquals(1, snapshot.spanStart)
        assertEquals(6, snapshot.spanEnd)
        assertEquals("hello", snapshot.word)
        assertEquals(3, snapshot.cursorOffset)
        assertFalse(snapshot.isAmharic)
        assertTrue(snapshot.isEmailField)
        assertFalse(snapshot.isPrivateField)
        assertFalse(snapshot.isNumberMode)
        assertEquals(CommittedWordSource.EXPLICIT_DELETE, snapshot.source)
    }

    @Test
    fun policyMatchRequiresEveryCapturedFieldPolicyToRemainIdentical() {
        val snapshot = CommittedWordSnapshot(
            editorToken = EditorToken(
                generation = 1,
                selectionGeneration = 2,
                selectionStart = 0,
                selectionEnd = 0,
                connection = Proxy.newProxyInstance(
                    InputConnection::class.java.classLoader,
                    arrayOf(InputConnection::class.java)
                ) { _, _, _ -> null } as InputConnection
            ),
            selectionStart = 0,
            selectionEnd = 0,
            spanStart = 0,
            spanEnd = 1,
            word = "a",
            cursorOffset = 1,
            isAmharic = true,
            isEmailField = false,
            isPrivateField = true,
            isNumberMode = false,
            source = CommittedWordSource.CURSOR_OBSERVATION
        )

        assertTrue(snapshot.matchesPolicy(true, false, true, false))
        assertFalse(snapshot.matchesPolicy(false, false, true, false))
        assertFalse(snapshot.matchesPolicy(true, true, true, false))
        assertFalse(snapshot.matchesPolicy(true, false, false, false))
        assertFalse(snapshot.matchesPolicy(true, false, true, true))
    }
}
