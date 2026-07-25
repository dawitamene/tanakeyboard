package com.addiyon.keyboard.emoji

import android.content.Context
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.addiyon.keyboard.SafeLog
import com.addiyon.keyboard.safeApply
import java.util.zip.GZIPInputStream

/**
 * Android-facing loader for the bundled emoji picker data -- the thin wrapper
 * around the pure-Kotlin [EmojiData], exactly the shape of
 * [com.addiyon.keyboard.suggestion.WordDictionary] (same `.dat`-not-`.gz`
 * asset rationale, same background-Thread + main-Handler contract, same
 * "main-thread-only reads, no locking" rule).
 *
 * The load pass also runs [Paint.hasGlyph] on every base emoji and every
 * skin-tone variant, so devices whose font predates part of the emoji set
 * (minSdk 24 ships Unicode 8-era glyphs) never see tofu: unrenderable bases
 * are dropped, unrenderable variants of a renderable base just shrink the
 * tone popup. ~6.4k hasGlyph calls cost tens of milliseconds -- noise next
 * to the gzip parse, and it's all off the main thread.
 */
class EmojiRepository(context: Context, private val assetName: String = "emoji.dat") {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Null until the background load finishes. Main-thread reads only.
     * Compose state (not a plain var) so a panel opened before the load
     * completes recomposes from its loading placeholder on its own.
     */
    var data: EmojiData? by mutableStateOf(null)
        private set

    private var loadStarted = false
    private var generation = 0L

    val isReady: Boolean
        get() = data != null

    /**
     * Kicks off the one-time background load. [onReady] runs on the main
     * thread once [data] is set. Safe to call more than once (the startup
     * chain and a first panel-open can race) -- only the first call starts
     * a thread.
     */
    fun loadAsync(onReady: () -> Unit = {}) {
        if (loadStarted) return
        loadStarted = true
        val loadGeneration = ++generation
        Thread {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            } catch (oom: OutOfMemoryError) {
                SafeLog.e(oom, "EmojiRepository setThreadPriority OOM")
            } catch (t: Throwable) {
                SafeLog.e(t, "EmojiRepository setThreadPriority")
            }
            val loaded = try {
                load()
            } catch (oom: OutOfMemoryError) {
                SafeLog.e(oom, "EmojiRepository load OOM")
                null
            } catch (t: Throwable) {
                SafeLog.e(t, "EmojiRepository load")
                null
            }
            mainHandler.post {
                safeApply {
                    if (loadGeneration != generation) return@safeApply
                    data = loaded
                    onReady()
                }
            }
        }.start()
    }

    fun release() {
        generation += 1
        loadStarted = false
        data = null
    }

    private fun load(): EmojiData {
        val paint = Paint()
        appContext.assets.open(assetName).use { raw ->
            GZIPInputStream(raw).bufferedReader(Charsets.UTF_8).useLines { lines ->
                return EmojiData.parse(lines, isRenderable = paint::hasGlyph)
            }
        }
    }
}
