package com.addiyon.keyboard.benchmarkhost

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.widget.AppCompatEditText
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicInteger

enum class ImeTestField {
    NORMAL,
    MULTILINE,
    SEARCH,
    SEND,
    DONE,
    EMAIL,
    URI,
    PASSWORD,
    NUMBER,
    PHONE,
    FAULT
}

enum class ImeFaultMode {
    NONE,
    THROW_ALL,
    REJECT_MUTATIONS,
    NULL_READS,
    SLOW_READS
}

class ImeTestHostActivity : ComponentActivity() {
    private val fields = EnumMap<ImeTestField, EditText>(ImeTestField::class.java)
    private val editorActions = EnumMap<ImeTestField, AtomicInteger>(ImeTestField::class.java)

    val faultField: FaultInjectingEditText
        get() = fields.getValue(ImeTestField.FAULT) as FaultInjectingEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContent())
        applyIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    fun field(kind: ImeTestField): EditText = fields.getValue(kind)

    fun actionCount(kind: ImeTestField): Int =
        editorActions[kind]?.get() ?: 0

    fun focusField(kind: ImeTestField) {
        val field = field(kind)
        field.requestFocus()
        field.setSelection(field.text.length)
        field.post {
            getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun configureFault(mode: ImeFaultMode) {
        faultField.faultMode = mode
        getSystemService(InputMethodManager::class.java)?.restartInput(faultField)
    }

    fun finalizeFaultComposition() {
        BaseInputConnection.removeComposingSpans(faultField.editableText)
    }

    private fun createContent(): View {
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        ImeTestField.entries.forEach { kind ->
            val label = TextView(this).apply {
                text = kind.name.lowercase().replaceFirstChar(Char::uppercase)
                setTypeface(typeface, Typeface.BOLD)
            }
            val editor = createEditor(kind).apply {
                id = View.generateViewId()
                tag = kind.name
                hint = "IME ${kind.name.lowercase()} field"
                contentDescription = hint
                minHeight = (56 * density).toInt()
            }
            label.labelFor = editor.id
            container.addView(
                label,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            container.addView(
                editor,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            fields[kind] = editor
            if (kind in actionFields) {
                val count = AtomicInteger()
                editorActions[kind] = count
                editor.setOnEditorActionListener { _, _, _ ->
                    count.incrementAndGet()
                    true
                }
            }
        }
        return ScrollView(this).apply {
            isFillViewport = true
            addView(container)
        }
    }

    private fun createEditor(kind: ImeTestField): EditText {
        val editor = if (kind == ImeTestField.FAULT) {
            FaultInjectingEditText(this)
        } else {
            EditText(this)
        }
        editor.inputType = when (kind) {
            ImeTestField.MULTILINE ->
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            ImeTestField.EMAIL ->
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            ImeTestField.URI ->
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            ImeTestField.PASSWORD ->
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            ImeTestField.NUMBER -> InputType.TYPE_CLASS_NUMBER
            ImeTestField.PHONE -> InputType.TYPE_CLASS_PHONE
            else -> InputType.TYPE_CLASS_TEXT
        }
        editor.imeOptions = when (kind) {
            ImeTestField.SEARCH -> EditorInfo.IME_ACTION_SEARCH
            ImeTestField.SEND -> EditorInfo.IME_ACTION_SEND
            ImeTestField.DONE -> EditorInfo.IME_ACTION_DONE
            ImeTestField.MULTILINE -> EditorInfo.IME_FLAG_NO_ENTER_ACTION
            else -> EditorInfo.IME_ACTION_NONE
        }
        editor.isSingleLine = kind != ImeTestField.MULTILINE
        return editor
    }

    private fun applyIntent(intent: Intent?) {
        val targetField = intent
            ?.getStringExtra(EXTRA_FIELD)
            ?.let { value -> ImeTestField.entries.firstOrNull { it.name == value.uppercase() } }
            ?: ImeTestField.NORMAL
        val fault = intent
            ?.getStringExtra(EXTRA_FAULT)
            ?.let { value -> ImeFaultMode.entries.firstOrNull { it.name == value.uppercase() } }
            ?: ImeFaultMode.NONE
        faultField.faultMode = fault
        field(targetField).post { focusField(targetField) }
    }

    companion object {
        const val EXTRA_FIELD = "field"
        const val EXTRA_FAULT = "fault"
        private val actionFields = setOf(
            ImeTestField.SEARCH,
            ImeTestField.SEND,
            ImeTestField.DONE
        )
    }
}

class FaultInjectingEditText(context: Context) : AppCompatEditText(context) {
    @Volatile
    var faultMode: ImeFaultMode = ImeFaultMode.NONE

    val connectionCreations = AtomicInteger()

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val target = super.onCreateInputConnection(outAttrs) ?: return null
        connectionCreations.incrementAndGet()
        return FaultInputConnection(target) { faultMode }
    }
}

private class FaultInputConnection(
    target: InputConnection,
    private val mode: () -> ImeFaultMode
) : InputConnectionWrapper(target, false) {
    override fun getTextBeforeCursor(maxChars: Int, flags: Int): CharSequence? =
        read { super.getTextBeforeCursor(maxChars, flags) }

    override fun getTextAfterCursor(maxChars: Int, flags: Int): CharSequence? =
        read { super.getTextAfterCursor(maxChars, flags) }

    override fun getSelectedText(flags: Int): CharSequence? =
        read { super.getSelectedText(flags) }

    override fun getExtractedText(
        request: ExtractedTextRequest?,
        flags: Int
    ): ExtractedText? = read { super.getExtractedText(request, flags) }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean =
        mutate { super.setComposingText(text, newCursorPosition) }

    override fun setComposingRegion(start: Int, end: Int): Boolean =
        mutate { super.setComposingRegion(start, end) }

    override fun finishComposingText(): Boolean =
        mutate { super.finishComposingText() }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean =
        mutate { super.commitText(text, newCursorPosition) }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean =
        mutate { super.deleteSurroundingText(beforeLength, afterLength) }

    override fun deleteSurroundingTextInCodePoints(
        beforeLength: Int,
        afterLength: Int
    ): Boolean = mutate {
        super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
    }

    override fun performEditorAction(editorAction: Int): Boolean =
        mutate { super.performEditorAction(editorAction) }

    override fun sendKeyEvent(event: KeyEvent?): Boolean =
        mutate { super.sendKeyEvent(event) }

    private fun <T> read(block: () -> T?): T? = when (mode()) {
        ImeFaultMode.THROW_ALL -> throw RuntimeException("Injected editor read failure")
        ImeFaultMode.NULL_READS -> null
        ImeFaultMode.SLOW_READS -> {
            SystemClock.sleep(SLOW_READ_MILLIS)
            block()
        }
        else -> block()
    }

    private fun mutate(block: () -> Boolean): Boolean = when (mode()) {
        ImeFaultMode.THROW_ALL -> throw RuntimeException("Injected editor mutation failure")
        ImeFaultMode.REJECT_MUTATIONS -> false
        else -> block()
    }

    private companion object {
        const val SLOW_READ_MILLIS = 50L
    }
}
