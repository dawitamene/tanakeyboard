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
import android.view.inputmethod.SurroundingText
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
    SLOW_READS,
    REJECT_COMPOSING_REGION,
    THROW_COMPOSING_REGION,
    ACCEPT_COMPOSING_REGION_WITHOUT_SPAN,

    /**
     * Reads hand back unstyled text, so the composing region the editor really
     * is holding is invisible to us. Models the many real editors -- WebViews,
     * some Compose fields, cross-platform toolkits -- that apply
     * setComposingText faithfully but never echo SPAN_COMPOSING back through
     * getSurroundingText/getExtractedText.
     */
    PLAIN_TEXT_READS,

    /**
     * getSurroundingText answers without saying where in the document the
     * window it returned starts (offset -1, the documented "unknown"), exactly
     * as the framework's DEFAULT InputConnection.getSurroundingText does --
     * it stitches its reply out of the get*Cursor reads, which carry no
     * absolute positions. Every editor that doesn't override that method
     * inherits it, which is every Jetpack Compose text field (Compose
     * implements InputConnection directly instead of extending
     * BaseInputConnection) plus WebViews and other fake-editable hosts.
     */
    UNKNOWN_SURROUNDING_OFFSET
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

    fun resetMutationLedger(kind: ImeTestField) {
        mutationTrackingField(kind).mutationLedger.reset()
    }

    fun mutationSnapshot(kind: ImeTestField): ImeMutationSnapshot =
        mutationTrackingField(kind).mutationLedger.snapshot()

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
        val editor = when (kind) {
            ImeTestField.NORMAL -> MutationTrackingEditText(this)
            ImeTestField.FAULT -> FaultInjectingEditText(this)
            else -> EditText(this)
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

    private fun mutationTrackingField(kind: ImeTestField): MutationTrackingEditText =
        field(kind) as? MutationTrackingEditText
            ?: error("$kind does not expose an IME mutation ledger")

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
        if (intent?.getBooleanExtra(EXTRA_CLEAR, false) == true) {
            field(targetField).editableText.clear()
        }
        field(targetField).post { focusField(targetField) }
    }

    companion object {
        const val EXTRA_FIELD = "field"
        const val EXTRA_FAULT = "fault"
        const val EXTRA_CLEAR = "clear"
        private val actionFields = setOf(
            ImeTestField.SEARCH,
            ImeTestField.SEND,
            ImeTestField.DONE
        )
    }
}

enum class ImeMutationOperation {
    SET_COMPOSING_REGION,
    SET_COMPOSING_TEXT,
    FINISH_COMPOSING_TEXT,
    COMMIT_TEXT,
    DELETE_SURROUNDING_TEXT,
    DELETE_SURROUNDING_TEXT_IN_CODE_POINTS,
    SET_SELECTION
}

data class ImeMutationSnapshot(
    val operations: List<ImeMutationOperation>
) {
    val contentMutationCount: Int
        get() = operations.count { it != ImeMutationOperation.SET_SELECTION }

    val selectionMutationCount: Int
        get() = operations.count { it == ImeMutationOperation.SET_SELECTION }
}

class ImeMutationLedger {
    private val lock = Any()
    private val operations = mutableListOf<ImeMutationOperation>()

    fun record(operation: ImeMutationOperation) {
        synchronized(lock) {
            operations += operation
        }
    }

    fun reset() {
        synchronized(lock) {
            operations.clear()
        }
    }

    fun snapshot(): ImeMutationSnapshot =
        synchronized(lock) {
            ImeMutationSnapshot(operations.toList())
        }
}

class MutationTrackingEditText(context: Context) : AppCompatEditText(context) {
    val mutationLedger = ImeMutationLedger()

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val target = super.onCreateInputConnection(outAttrs) ?: return null
        return MutationTrackingInputConnection(target, mutationLedger)
    }
}

private class MutationTrackingInputConnection(
    target: InputConnection,
    private val mutationLedger: ImeMutationLedger
) : InputConnectionWrapper(target, false) {
    override fun setComposingRegion(start: Int, end: Int): Boolean {
        mutationLedger.record(ImeMutationOperation.SET_COMPOSING_REGION)
        return super.setComposingRegion(start, end)
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        mutationLedger.record(ImeMutationOperation.SET_COMPOSING_TEXT)
        return super.setComposingText(text, newCursorPosition)
    }

    override fun finishComposingText(): Boolean {
        mutationLedger.record(ImeMutationOperation.FINISH_COMPOSING_TEXT)
        return super.finishComposingText()
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        mutationLedger.record(ImeMutationOperation.COMMIT_TEXT)
        return super.commitText(text, newCursorPosition)
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        mutationLedger.record(ImeMutationOperation.DELETE_SURROUNDING_TEXT)
        return super.deleteSurroundingText(beforeLength, afterLength)
    }

    override fun deleteSurroundingTextInCodePoints(
        beforeLength: Int,
        afterLength: Int
    ): Boolean {
        mutationLedger.record(ImeMutationOperation.DELETE_SURROUNDING_TEXT_IN_CODE_POINTS)
        return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
    }

    override fun setSelection(start: Int, end: Int): Boolean {
        mutationLedger.record(ImeMutationOperation.SET_SELECTION)
        return super.setSelection(start, end)
    }
}

class FaultInjectingEditText(context: Context) : AppCompatEditText(context) {
    @Volatile
    var faultMode: ImeFaultMode = ImeFaultMode.NONE

    val connectionCreations = AtomicInteger()
    val contextSnapshotReads = AtomicInteger()

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val target = super.onCreateInputConnection(outAttrs) ?: return null
        connectionCreations.incrementAndGet()
        return FaultInputConnection(
            target = target,
            mode = { faultMode },
            contextSnapshotReads = contextSnapshotReads
        )
    }
}

private class FaultInputConnection(
    target: InputConnection,
    private val mode: () -> ImeFaultMode,
    private val contextSnapshotReads: AtomicInteger
) : InputConnectionWrapper(target, false) {
    override fun getTextBeforeCursor(maxChars: Int, flags: Int): CharSequence? =
        read { super.getTextBeforeCursor(maxChars, flags) }?.let(::maybeUnstyle)

    override fun getTextAfterCursor(maxChars: Int, flags: Int): CharSequence? =
        read { super.getTextAfterCursor(maxChars, flags) }?.let(::maybeUnstyle)

    override fun getSurroundingText(
        beforeLength: Int,
        afterLength: Int,
        flags: Int
    ): SurroundingText? {
        if (
            beforeLength == CONTEXT_SNAPSHOT_BEFORE &&
            afterLength == CONTEXT_SNAPSHOT_AFTER
        ) {
            contextSnapshotReads.incrementAndGet()
        }
        if (mode() == ImeFaultMode.UNKNOWN_SURROUNDING_OFFSET) {
            return read { defaultSurroundingText(beforeLength, afterLength, flags) }
        }
        val result = read { super.getSurroundingText(beforeLength, afterLength, flags) }
        if (result == null || mode() != ImeFaultMode.PLAIN_TEXT_READS) return result
        return SurroundingText(
            result.text.toString(),
            result.selectionStart,
            result.selectionEnd,
            result.offset
        )
    }

    /**
     * The framework's own default implementation of
     * [InputConnection.getSurroundingText], reproduced: concatenate the three
     * cursor-relative reads and report offset -1, because nothing in them says
     * where the window sits in the document.
     */
    private fun defaultSurroundingText(
        beforeLength: Int,
        afterLength: Int,
        flags: Int
    ): SurroundingText? {
        val before = super.getTextBeforeCursor(beforeLength, flags) ?: return null
        val after = super.getTextAfterCursor(afterLength, flags) ?: return null
        val selected = super.getSelectedText(flags) ?: ""
        return SurroundingText(
            "$before$selected$after",
            before.length,
            before.length + selected.length,
            -1
        )
    }

    override fun getSelectedText(flags: Int): CharSequence? =
        read { super.getSelectedText(flags) }?.let(::maybeUnstyle)

    override fun getExtractedText(
        request: ExtractedTextRequest?,
        flags: Int
    ): ExtractedText? = read { super.getExtractedText(request, flags) }?.also {
        if (mode() == ImeFaultMode.PLAIN_TEXT_READS) it.text = it.text?.toString()
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean =
        mutate { super.setComposingText(text, newCursorPosition) }

    override fun setComposingRegion(start: Int, end: Int): Boolean =
        when (mode()) {
            ImeFaultMode.REJECT_COMPOSING_REGION -> false
            ImeFaultMode.THROW_COMPOSING_REGION ->
                throw RuntimeException("Injected composing region failure")
            ImeFaultMode.ACCEPT_COMPOSING_REGION_WITHOUT_SPAN -> true
            else -> mutate { super.setComposingRegion(start, end) }
        }

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

    /**
     * Drops spans -- including SPAN_COMPOSING -- the way an editor that returns
     * plain text does, so the IME can't see its own composing region echoed back.
     */
    private fun maybeUnstyle(value: CharSequence): CharSequence =
        if (mode() == ImeFaultMode.PLAIN_TEXT_READS) value.toString() else value

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
        const val CONTEXT_SNAPSHOT_BEFORE = 256
        const val CONTEXT_SNAPSHOT_AFTER = 128
    }
}
