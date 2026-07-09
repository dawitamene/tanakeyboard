package com.addiyon.keyboard.model

/**
 * What the Enter key should do and look like, derived from the target field's
 * IME action (`EditorInfo.imeOptions`). Gboard-style: a search field shows a
 * magnifier and runs the search, a "next" field advances focus, a plain/
 * multi-line field just inserts a newline, etc. Resolved per input session in
 * [com.addiyon.keyboard.AddiyonKeyboardService.onStartInputView]; the key's
 * icon and [com.addiyon.keyboard.AddiyonKeyboardService.onEnter] both read it.
 */
enum class EnterAction { NEWLINE, GO, SEARCH, SEND, NEXT, PREVIOUS, DONE }
