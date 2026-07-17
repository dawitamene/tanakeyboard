package com.addiyon.keyboard.suggestion

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.util.zip.GZIPInputStream

/**
 * Android-facing loader for a bundled n-gram model -- the same thin-wrapper
 * shape as [WordDictionary], but around [NgramModel]. Reads its asset
 * (`amharic_ngrams.dat` / `english_ngrams.dat`; binary, gzip; built by
 * `tools/build_ngrams.py`, `.dat` not `.gz` for the same AGP reason documented
 * on [WordDictionary]) on a background thread and posts readiness to the main
 * thread; all reads must stay on the main thread, so no locking here either.
 *
 * [normalize] is the fold the asset's vocab was sorted by and that
 * [NgramModel.wordId] binary-searches with -- Amharic homoglyph fold for
 * `amharic_ngrams.dat`, per-char lowercase for `english_ngrams.dat`.
 */
class NgramDictionary(
    context: Context,
    private val assetName: String,
    private val normalize: (String) -> String
) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var model: NgramModel? = null
    private var loadStarted = false

    /** True once the background load has finished and [predict] has real data. */
    val isReady: Boolean
        get() = model != null

    /**
     * Kicks off the one-time background load; [onReady] runs on the main
     * thread. Safe to call more than once -- only the first call starts a
     * thread. [predict] just returns empty until then.
     */
    fun loadAsync(onReady: () -> Unit) {
        if (loadStarted) return
        loadStarted = true
        Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            val loaded = appContext.assets.open(assetName).use { raw ->
                GZIPInputStream(raw).use { NgramModel.parse(it, normalize) }
            }
            mainHandler.post {
                model = loaded
                onReady()
            }
        }.start()
    }

    /** Empty (not an error) if the model hasn't finished loading yet. */
    fun predict(prev2: String?, prev1: String, limit: Int): List<NgramModel.Prediction> =
        model?.predict(prev2, prev1, limit) ?: emptyList()
}
