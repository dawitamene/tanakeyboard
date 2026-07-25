package com.addiyon.keyboard.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.addiyon.keyboard.AddiyonKeyboardService

class LanguageToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Handler(Looper.getMainLooper()).post {
            AddiyonKeyboardService.currentInstance?.toggleLanguage()
        }
    }
}
