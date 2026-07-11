# tools

## `build_amharic_dict.py`

Regenerates `app/src/main/assets/amharic_words.dat`, the Amharic suggestion
dictionary loaded by `suggestion/WordDictionary.kt`, from a corpus
term-frequency dump (one `frequency<TAB>token` line, single header line):

```sh
python3 tools/build_amharic_dict.py Term_Frequency.txt
```

- Keeps pure Ethiopic-syllable words with frequency ≥ 2 (singletons in
  scraped corpora are disproportionately typos) and common abbreviations
  (`ዓ.ም`, `ዶ/ር`, `ት/ቤት`, …) with frequency ≥ 50; drops punctuation tokens,
  numbers, Latin fragments, and anything containing digits.
- Output: gzip, `word<TAB>frequency` per line, **sorted by the word**
  (UTF-16 code-unit order) — required by `WordTrie.build`'s streaming
  flat-array construction, which throws on unsorted input. ~411k entries.
- The source dump itself is gitignored (17MB); keep it wherever convenient
  and pass its path.

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
