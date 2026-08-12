# tools

## `install_debug_preserving_state.sh`

Builds and installs the Addiyon debug APK as an in-place update, preserving app data
such as keyboard preferences. It also keeps the virtual keyboard visible when
the emulator's hardware-keyboard integration is active, enables Addiyon, and
selects it as the default IME:

```sh
/Users/dev/code/addiyon-keyboard/tools/install_debug_preserving_state.sh
```

Pass an ADB serial as the first argument when more than one device is connected.
The script deliberately fails instead of uninstalling the existing package if
Android cannot perform a state-preserving update.

## `build_amharic_dict.py`

Regenerates the Amharic SQLite inputs from the pinned HornMorpho 5.3.6
lexicon snapshot under `language/amharic/hornmorpho`:

```sh
python3 /Users/dev/code/addiyon-keyboard/tools/build_amharic_dict.py
```

- `amharic_lexemes.dat` contains 18,867 unique source records, including the
  HornMorpho verb roots and grammatical classes needed by the next morphology
  phase.
- `amharic_words.dat` contains 18,067 displayable base lemmas. Internal slash
  notation is removed, spelling-equivalent keys are folded, and ranking uses
  HornMorpho's own `root.frq` statistics.
- `amharic_ngrams.dat` is intentionally empty. It prevents the prediction
  model tied to the discarded surface dictionary from leaking those forms
  back into the new database.
- The old approximately 254,000-row wordlist and its inputs are retained only
  under `archive/legacy-amharic-dictionary`; they are not build inputs.
- Full source and licensing details are in
  `language/amharic/hornmorpho/UPSTREAM.md`.

## `build_ngrams.py`

Rebuilds `language/amharic/src/dictionary/amharic_ngrams.dat` from a clean
corpus after gating every token through the HornMorpho displayable lemma set.
The checked-in baseline is empty until an appropriate corpus is selected.

```sh
python3 tools/build_ngrams.py CACO_TEXT.txt amharic_corpus_abdulmunim.txt
```

- **Regenerate the dictionary first** — every n-gram token is homoglyph-folded
  and must resolve to an `amharic_words.dat` entry, and is counted **as that
  entry's display form**. Variant spellings pool their evidence, corpus junk
  never enters the model, and prediction strings match dictionary suggestion
  strings exactly (the ranker's n-gram boost and `NgramModel`'s context
  lookup both match on folded keys). Unknown words block pair adjacency
  without acting as sentence boundaries.
- Tokenization: NFC, gemination marks stripped. A token with edge punctuation
  around an Ethiopic core (`በቴሌግራም።`, `«ሰላም»`) contributes the core plus a
  boundary on the punctuation side; standalone punctuation, numbers, Latin,
  and mixed tokens (`ዓ.ም`, `በ2007`) are wholly **boundaries**, so no
  bigram/trigram ever spans them.
- Pruning (all tunable via flags): bigram contexts with total count ≥ 10,
  successors count ≥ 8, top 8 per context; trigrams only where the (w1, w2)
  context survived as a bigram, successors count ≥ 8, top 6, and dropped when
  identical to their bigram backoff prefix.
- Output: a binary word-ID model, **format v2** (vocab table sorted by folded
  key + sorted context arrays + offset/successor/weight arrays, big-endian,
  weights log-quantized to a byte), gzipped with `mtime=0` for byte-stable
  output. `NgramModel.kt` rejects v1 assets loudly.
- `--test-fixture` builds the tiny JVM-test model from the checked-in mini
  corpus:

```sh
python3 tools/build_ngrams.py --test-fixture \
    apps/textrevamp/src/test/resources/ngram_mini_corpus.txt \
    apps/textrevamp/src/test/resources/ngram_fixture.dat
```

- Corpus files stay outside the repository and are passed explicitly.

### English (`--lang english`)

Instead of counting a raw corpus, English reads **pre-compiled word n-gram
frequency lists** and gates every token through `english_words.dat` (so every
prediction is a real, canonically-spelled dictionary word). Emits the **v3**
binary format (per-successor casing flags — see below); the fold is **per-char
lowercase** (matching `WordDictionary`'s default `Char::lowercaseChar` keying),
words are Latin letters + apostrophes, and trigrams are **not** gated on
surviving bigram contexts (they come from a separate curated source, so a useful
trigram whose bigram prefix isn't in the top bigram list is still served
directly).

```sh
python3 tools/build_ngrams.py --lang english \
    --bigram-file tools/.cache/count_2w.txt \
    --trigram-file tools/.cache/3grams_english.csv
```

- **Bigrams** — Norvig `count_2w.txt` (Google Web Trillion Word Corpus, ~286k
  pairs), `<w1> <w2><TAB><count>` per line:
  <https://www.norvig.com/ngrams/count_2w.txt>. These do the heavy lifting.
- **Trigrams** — orgtre/google-books-ngram-frequency `3grams_english.csv` (top
  ~3k cleaned word trigrams, CC BY), `<w1> <w2> <w3>,<freq>` with a header row.
  A small, top-value refinement; the model backs off to bigrams when a trigram
  context is absent.
- Downloads go in `tools/.cache/` (gitignored). **Regenerate `english_words.dat`
  first** (same dictionary-gating reason as Amharic).
- **Per-context proper-noun casing (v3)**: the dictionary stores common nouns
  lowercase, so predictions would read "united states". Casing evidence is taken
  from the *cased* trigram source (the Norvig bigram list only capitalizes
  sentence-initial first words, so its successors carry no signal), including the
  two adjacent pairs inside each trigram — so "in New York" / "New York Times"
  teach that York is capitalized after New. Each successor stores a flag
  (0 as-is / 1 capitalize-first / 2 all-caps); the fix is **per-context**, so
  "United → States" and "New York → Times/City" capitalize while "of → the"
  stays lowercase. Coverage is limited to pairs the ~3k trigrams attest.
- Output: `language/english/src/dictionary/english_ngrams.dat` (~240 KB), folded into
  the generated SQLite database with per-context casing metadata.

## `build_english_dict.py`

Regenerates `language/english/src/dictionary/english_words.dat`, the English suggestion
source folded into the generated SQLite database.

```sh
python3 tools/build_english_dict.py
```

- Needs Python 3 and internet the first time (downloads are cached in
  `tools/.cache/`, which is gitignored).
- Output: gzip, `word<TAB>frequency` per line, sorted by the lowercased word
  (UTF-16 code-unit order, same requirement as the Amharic asset above),
  ~250k entries. Entries carry **canonical casing** so proper nouns suggest
  capitalized ("england" typed → "England" suggested); `WordTrie` matches
  case-insensitively and returns the stored casing.

### Sources

- **Base vocabulary + frequencies**: hermitdave *FrequencyWords* English full
  OpenSubtitles list (MIT) — top 250k tokens.
- **Casing overlay**:
  - `proper_nouns.txt` (vendored, curated) — always force-applied. Edit this to
    add/fix proper nouns. Keep entries single-token and unambiguous. Plural and
    possessive forms of curated entries inherit the casing automatically
    ("norwegians" → "Norwegians"), guarded by a minimum stem length so "I"+s
    / "God"+s never capitalize "is" / "gods".
  - downloaded first-name + world-city lists (best-effort) — applied only to
    words outside the common-word band, so homographs like "may"/"mark"/"will"
    stay lowercase.

### Known limitation

Ambiguous words (march/March, us/US, china/China, may/May) get a single casing
per lowercased key and bias toward lowercase unless force-listed in
`proper_nouns.txt`. True disambiguation would need surrounding context the
keyboard doesn't track.
