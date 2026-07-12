# tools

## `build_amharic_dict.py`

Regenerates `app/src/main/assets/amharic_words.dat`, the Amharic suggestion
dictionary loaded by `suggestion/WordDictionary.kt`, from a corpus
term-frequency dump (one `frequency<TAB>token` line, single header line):

```sh
python3 tools/build_amharic_dict.py Term_Frequency.txt
```

- **Homoglyph folding**: spelling variants of the same word (ሀ/ሃ/ሐ/ኀ, ሰ/ሠ,
  አ/ኣ/ዐ, ጸ/ፀ …) are merged — frequencies summed, the most frequent spelling
  kept as the display form. The fold table is a hand-mirrored copy of
  `transliteration/EthiopicNormalizer.kt` (keep in sync; `BundledAssetTest`
  catches drift).
- **Corpora** (frequency evidence, counts summed after folding): the CACO
  dump passed on the command line plus the vendored
  `term_frequency_yididiyan.txt.gz` (from
  [yididiyan/amharic_spell_corrector](https://github.com/yididiyan/amharic_spell_corrector),
  a similar-scale corpus; the upstream file's accidentally-duplicated tail
  block was deduplicated when vendoring). Keeps pure Ethiopic-syllable words
  with combined frequency ≥ 10 on corpus evidence alone; rarer words
  (frequency 2–9) only when a validation wordlist also attests their folded
  form — rare tokens in a scraped corpus are disproportionately typos
  (~97% of freq-50+ tokens are wordlist-attested vs ~12% of freq-2–4).
- **Validation wordlists**: `am_et_hunspell_words.txt.gz` (Hunspell am_ET
  expansion, recovered from this repo's git history) and
  `wordlist_abdulmunim.txt.gz` (curated, from
  [abdulmunimjemal/AmharicSpellCheckerEngine](https://github.com/abdulmunimjemal/AmharicSpellCheckerEngine)).
- **Enrichment**: abdulmunim words not already kept are added at frequency 1
  (rank floor: they complete, but below every corpus-attested word). The
  Hunspell list is attestation-only — it's machine-expanded and was the old,
  low-quality dictionary.
- Common abbreviations (`ዓ.ም`, `ዶ/ር`, `ት/ቤት`, …) with frequency ≥ 50 are kept
  without a wordlist check; drops punctuation tokens, numbers, Latin
  fragments, and anything containing digits.
- Output: gzip, `word<TAB>frequency` per line, **sorted by the folded key**
  (UTF-16 code-unit order) — required by `WordTrie.build`'s streaming
  flat-array construction, which throws on unsorted input. ~254k entries.
- The CACO dump itself is gitignored (17MB); keep it wherever convenient
  and pass its path.

## `build_ngrams.py`

Regenerates `app/src/main/assets/amharic_ngrams.dat`, the Amharic bigram /
trigram next-word model loaded by `suggestion/NgramDictionary.kt` →
`suggestion/NgramModel.kt`, from one or more raw corpora (counts summed;
pre-tokenized like `CACO_TEXT.txt` and raw text like the abdulmunim corpus
both work):

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
- Pruning (all tunable via flags): bigram contexts with total count ≥ 4,
  successors count ≥ 3, top 8 per context; trigrams only where the (w1, w2)
  context survived as a bigram, successors count ≥ 3, top 6, and dropped when
  identical to their bigram backoff prefix.
- Output: a binary word-ID model, **format v2** (vocab table sorted by folded
  key + sorted context arrays + offset/successor/weight arrays, big-endian,
  weights log-quantized to a byte), gzipped with `mtime=0` for byte-stable
  output. `NgramModel.kt` rejects v1 assets loudly.
- The abdulmunim corpus re-downloads from that repo's LFS media URL (see
  `.gitignore`); like `CACO_TEXT.txt` it stays out of the repo.
- `--test-fixture` builds the tiny JVM-test model from the checked-in mini
  corpus:

```sh
python3 tools/build_ngrams.py --test-fixture \
    app/src/test/resources/ngram_mini_corpus.txt \
    app/src/test/resources/ngram_fixture.dat
```

- The corpus itself is gitignored (249MB); keep it wherever convenient and
  pass its path.

## `build_english_dict.py`

Regenerates `app/src/main/assets/english_words.dat`, the English suggestion
dictionary loaded by `suggestion/WordDictionary.kt`.

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
    add/fix proper nouns. Keep entries single-token and unambiguous.
  - downloaded first-name + world-city lists (best-effort) — applied only to
    words outside the common-word band, so homographs like "may"/"mark"/"will"
    stay lowercase.

### Known limitation

Ambiguous words (march/March, us/US, china/China, may/May) get a single casing
per lowercased key and bias toward lowercase unless force-listed in
`proper_nouns.txt`. True disambiguation would need surrounding context the
keyboard doesn't track.
