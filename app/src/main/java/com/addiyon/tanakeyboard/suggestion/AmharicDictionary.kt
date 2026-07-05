package com.addiyon.tanakeyboard.suggestion

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.zip.GZIPInputStream

/**
 * Android-facing loader for the bundled Amharic word dictionary -- the thin
 * wrapper around the pure-Kotlin [WordTrie], mirroring how
 * [com.addiyon.tanakeyboard.transliteration.AmharicComposer] wraps the pure
 * [com.addiyon.tanakeyboard.transliteration.Transliterator].
 *
 * Reads `assets/amharic_words.dat` -- gzip-compressed, one
 * `word<TAB>frequency` pair per line once decompressed. Current bundled
 * asset is the Hunspell `am_ET` word list (public domain, Ge'ez Frontier
 * Foundation), ~182k words; since it carries no real frequency data,
 * frequency here is a rough starter heuristic (shorter words rank higher).
 * Swapping in a proper frequency-ranked list later (e.g. built from
 * Wikipedia + news corpora) is just replacing this one asset file --
 * nothing in this class or [WordTrie] needs to change.
 *
 * NOTE ON THE FILE EXTENSION: deliberately `.dat`, not `.gz` -- the Android
 * Gradle Plugin silently auto-decompresses `.gz` assets and strips the
 * extension at build time (confirmed by inspecting the packaged APK), which
 * would leave a source file named `amharic_words.txt.gz` bundled as a
 * *plain, uncompressed* `amharic_words.txt` instead. `.dat` sidesteps that.
 *
 * Parsing ~182k lines and building a trie is fast in absolute terms but
 * still real work, so it happens on a background thread rather than
 * blocking the IME's `onCreate` -- [loadAsync] posts the result back to the
 * main thread when ready, since [suggestions] and every UI read of
 * [isReady] must only ever happen on the main thread (this class does no
 * locking of its own for that reason).
 */
class AmharicDictionary(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var trie: WordTrie? = null

    /** True once the background load has finished and [suggestions] has real data. */
    val isReady: Boolean
        get() = trie != null

    /**
     * Kicks off the one-time background load. [onReady] is invoked on the
     * main thread once the trie is built. No-op-safe to call [suggestions]
     * before that -- it just returns an empty list until then.
     */
    fun loadAsync(onReady: () -> Unit) {
        Thread {
            val loaded = load()
            mainHandler.post {
                trie = loaded
                onReady()
            }
        }.start()
    }

    private fun load(): WordTrie {
        val words = mutableListOf<Pair<String, Int>>()
        appContext.assets.open(ASSET_NAME).use { raw ->
            GZIPInputStream(raw).bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    val word = line.substring(0, tab)
                    val frequency = line.substring(tab + 1).toIntOrNull() ?: continue
                    words.add(word to frequency)
                }
            }
        }
        return WordTrie.build(words)
    }

    /** Empty (not an error) if the dictionary hasn't finished loading yet. */
    fun suggestions(prefix: String, limit: Int = 3): List<String> =
        trie?.suggestions(prefix, limit) ?: emptyList()

    private companion object {
        const val ASSET_NAME = "amharic_words.dat"
    }
}
