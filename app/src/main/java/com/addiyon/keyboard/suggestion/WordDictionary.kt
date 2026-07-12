package com.addiyon.keyboard.suggestion

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.util.zip.GZIPInputStream

/**
 * Android-facing loader for a bundled word-frequency dictionary -- the thin
 * wrapper around the pure-Kotlin [WordTrie], mirroring how the composing
 * layer wraps the pure [com.addiyon.keyboard.transliteration.Transliterator].
 * One instance per bundled asset (currently Amharic and English).
 *
 * Reads a gzip-compressed asset, one `word<TAB>frequency` pair per line
 * once decompressed, SORTED by the word's normalized key ([keyChar]) in
 * UTF-16 code-unit order -- [WordTrie.build] streams the lines into its
 * flat-array trie and throws on unsorted input, so the `tools/` scripts that
 * generate the assets sort them at build time (a unit test loads the real
 * assets to catch drift). The bundled assets:
 *
 *   - `amharic_words.dat`: built by `tools/build_amharic_dict.py` from two
 *     corpus term-frequency dumps plus curated wordlists (see
 *     tools/README.md), ~254k entries: pure-fidel words (frequent ones kept
 *     on corpus evidence alone; rare ones only when a validation wordlist
 *     also attests them -- rare tokens in a scraped corpus are
 *     disproportionately typos; wordlist words the corpora never saw ride
 *     along at frequency 1 as rank-floor completions)
 *     plus common abbreviations like ዓ.ም / ዶ/ር (frequency >= 50), which
 *     can't be composed (period/slash commit the word) but still surface as
 *     tap-to-complete suggestions for their fidel prefix. Homoglyph
 *     spelling variants (ሀገር / ሃገር / ሐገር / ኀገር) are merged at asset-build
 *     time -- frequencies summed, most frequent spelling kept as display
 *     form -- and matched via [EthiopicNormalizer]-folded trie keys.
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
 * Parsing a couple hundred thousand lines and building a trie is fast in
 * absolute terms but
 * still real work, so it happens on a background thread rather than
 * blocking the IME's `onCreate` -- [loadAsync] posts the result back to the
 * main thread when ready, since [suggestions] and every UI read of
 * [isReady] must only ever happen on the main thread (this class does no
 * locking of its own for that reason).
 */
class WordDictionary(
    context: Context,
    private val assetName: String,
    /** Per-character key normalizer for [WordTrie] matching. Default:
     *  case-insensitive; the Amharic dictionary injects
     *  [com.addiyon.keyboard.transliteration.EthiopicNormalizer.normalize]
     *  to fold homoglyph spelling variants. The bundled asset must be sorted
     *  by this same key (the `tools/` build script mirrors the fold table). */
    private val keyChar: (Char) -> Char = Char::lowercaseChar,
) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var trie: WordTrie? = null
    private var loadStarted = false

    /** True once the background load has finished and [suggestions] has real data. */
    val isReady: Boolean
        get() = trie != null

    /**
     * Kicks off the one-time background load. [onReady] is invoked on the
     * main thread once the trie is built. No-op-safe to call [suggestions]
     * before that -- it just returns an empty list until then. Safe to call
     * more than once (e.g. a deferred startup load racing a language-switch
     * trigger) -- only the first call actually starts a thread.
     */
    fun loadAsync(onReady: () -> Unit) {
        if (loadStarted) return
        loadStarted = true
        Thread {
            // Default thread priority competes with the UI thread for CPU; on
            // slow/low-RAM devices that (plus the GC pauses building a
            // ~200k-node trie triggers) is enough to stall the main thread and
            // read as an ANR. Background priority yields to the UI thread.
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            val loaded = load()
            mainHandler.post {
                trie = loaded
                onReady()
            }
        }.start()
    }

    private fun load(): WordTrie {
        appContext.assets.open(assetName).use { raw ->
            GZIPInputStream(raw).bufferedReader(Charsets.UTF_8).useLines { lines ->
                // Streamed straight into the trie -- no intermediate
                // mutableListOf of ~200k pairs -- so peak memory during load
                // is roughly just the trie itself, not the trie plus a full
                // copy of the source data.
                return WordTrie.build(
                    lines.mapNotNull { line ->
                        val tab = line.indexOf('\t')
                        if (tab <= 0) return@mapNotNull null
                        val word = line.substring(0, tab)
                        val frequency = line.substring(tab + 1).toIntOrNull() ?: return@mapNotNull null
                        word to frequency
                    },
                    keyChar,
                )
            }
        }
    }

    /** Empty (not an error) if the dictionary hasn't finished loading yet. */
    fun suggestions(prefix: String, limit: Int = 3): List<String> =
        trie?.suggestions(prefix, limit) ?: emptyList()

    fun suggestionEntries(prefix: String, limit: Int = 3): List<WordTrie.Suggestion> =
        trie?.suggestionEntries(prefix, limit) ?: emptyList()

    fun frequencyOf(word: String): Int? =
        trie?.frequencyOf(word)

    /**
     * Whether [word] is an exact dictionary entry. False (not an error) if
     * the dictionary hasn't finished loading yet -- see
     * [com.addiyon.keyboard.suggestion.CandidateRanker].
     */
    fun isWord(word: String): Boolean = frequencyOf(word) != null

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
