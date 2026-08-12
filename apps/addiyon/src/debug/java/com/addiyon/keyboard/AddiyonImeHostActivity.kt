package com.addiyon.keyboard

import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

class AddiyonImeHostActivity : Activity() {
    lateinit var editor: EditText
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editor = EditText(this).apply {
            isSingleLine = false
            minLines = 3
        }
        setContentView(editor)
        editor.requestFocus()
        editor.post {
            getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun clearAndFocus() {
        editor.text.clear()
        editor.requestFocus()
        editor.setSelection(0)
        getSystemService(InputMethodManager::class.java)?.restartInput(editor)
    }
}
