package com.addiyon.keyboard.runtime

import android.inputmethodservice.InputMethodService
import com.addiyon.keyboard.product.KeyboardProduct

abstract class BaseKeyboardService : InputMethodService() {
    protected abstract val keyboardProduct: KeyboardProduct
}
