package com.addiyon.tanakeyboard.suggestion

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.zip.GZIPInputStream

/**
 * Android-facing loader for a bundled word-frequency dictionary -- the thin
 * wrapper around the pure-Kotlin [WordTrie], mirroring how the composing
 * layer wraps the pure [com.addiyon.tanakeyboard.transliteration.Transliterator].
 * One instance per bundled asset (currently Amharic and English).
 *
 * Reads a gzip-compressed asset, one `word<TAB>frequency` pair per line
 * once decompressed. The bundled assets:
 *
 *   - `amharic_words.dat`: the Hunspell `am_ET` word list (public domain,
 *     Ge'ez Frontier Foundation), ~182k words; since it carries no real
 *     frequency data, frequency is a rough starter heuristic (shorter words
 *     rank higher). Swapping in a proper frequency-ranked list later (e.g.
 *     built from Wikipedia + news corpora) is just replacing the asset.
 *   - `english_words.dat`: derived from the OpenSubtitles-based
 *     FrequencyWords full list (hermitdave/FrequencyWords, MIT), ~250k
 *     words with real corpus frequencies. The source tokenizer splits
 *     contractions ("don't" -> "don" + "'t"), so common contractions were
 *     reconstructed with estimated counts at asset-build time. Unlike the
 *     old en_50k asset, entries now carry canonical casing (common words
 *     lowercase, proper nouns capitalized). The trie matches
 *     case-insensitively, so callers still lowercase the lookup prefix and
 *     reconcile it with the returned casing via [matchCase]. Rebuilt by
 *     `tools/build_english_dict.py`.
 *
 * NOTE ON THE FILE EXTENSION: deliberately `.dat`, not `.gz` -- the Android
 * Gradle Plugin silently auto-decompresses `.gz` assets and strips the
 * extension at build time (confirmed by inspecting the packaged APK), which
 * would leave a source file named `words.txt.gz` bundled as a *plain,
 * uncompressed* `words.txt` instead. `.dat` sidesteps that.
 *
 * Parsing ~182k lines and building a trie is fast in absolute terms but
 * still real work, so it happens on a background thread rather than
 * blocking the IME's `onCreate` -- [loadAsync] posts the result back to the
 * main thread when ready, since [suggestions] and every UI read of
 * [isReady] must only ever happen on the main thread (this class does no
 * locking of its own for that reason).
 */
class WordDictionary(context: Context, private val assetName: String) {

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
        appContext.assets.open(assetName).use { raw ->
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

    /**
     * Fuzzy / typo-tolerant completions of [prefix] -- see
     * [WordTrie.fuzzySuggestions]. Empty until the dictionary has loaded. The
     * Amharic caller injects a script-aware [substitutionCost]; English uses
     * the uniform default.
     */
    fun fuzzySuggestions(
        prefix: String,
        maxEdits: Int,
        limit: Int = 3,
        substitutionCost: WordTrie.SubstitutionCost =
            WordTrie.SubstitutionCost { a, b -> if (a == b) 0 else 1 },
        insertCost: Int = 1,
        deleteCost: Int = 1,
    ): List<WordTrie.FuzzyMatch> =
        trie?.fuzzySuggestions(prefix, maxEdits, limit, substitutionCost, insertCost, deleteCost)
            ?: emptyList()
}
