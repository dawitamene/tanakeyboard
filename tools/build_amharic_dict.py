#!/usr/bin/env python3
"""
Regenerates app/src/main/assets/amharic_words.dat -- the Amharic suggestion
dictionary consumed by suggestion/WordDictionary.kt -> WordTrie.kt.

Sources (three, playing two distinct roles):
  - CORPORA (frequency evidence): the term-frequency dump passed on the
    command line (the CACO Term_Frequency.txt, `frequency<TAB>token` with a
    header line) plus the vendored term_frequency_yididiyan.txt.gz
    (`token<SPACE>frequency`, from yididiyan/amharic_spell_corrector, a
    similar-scale corpus -- ነው counts within 15% of CACO's, so summing the
    two is sane). Both are raw tokenizer output mixing real words with
    punctuation tokens, numbers, Latin fragments, and one-off typos; both
    show the same typo signature (~97% of freq-50+ tokens are
    wordlist-attested vs ~4-12% of the rarest bucket), hence the shared
    validation rule below.
  - WORDLISTS (attestation + enrichment): am_et_hunspell_words.txt.gz
    (Hunspell am_ET expansion) and wordlist_abdulmunim.txt.gz (curated, from
    abdulmunimjemal/AmharicSpellCheckerEngine). Both validate rare corpus
    words; the abdulmunim list is additionally merged IN as rank-floor
    entries (see enrichment below).

Output format:
    - one `word<TAB>frequency` line per entry, UTF-8
    - SORTED BY THE HOMOGLYPH-FOLDED KEY (see below), in UTF-16 code-unit
      order -- WordTrie.build streams the asset into a flat-array trie keyed
      by the folded form and requires input sorted by that key (it throws on
      out-of-order lines). All kept chars are BMP, so Python's code-point sort
      is identical to UTF-16 code-unit order (asserted below).
    - gzip-compressed, written with a `.dat` extension (NOT `.gz`) because the
      Android Gradle Plugin auto-decompresses `.gz` assets at build time.

Homoglyph folding:
  Amharic spells the same word with interchangeable homophone characters
  (ሀ/ሃ/ሐ/ኀ, ሰ/ሠ, አ/ኣ/ዐ, ጸ/ፀ ...), so a raw corpus splits one word's
  frequency across several spellings. Tokens that are identical after folding
  are MERGED: frequencies summed, and the most frequent surface spelling kept
  as the display form. FOLD below is a hand-mirrored copy of the Kotlin
  transliteration/EthiopicNormalizer.kt table -- keep the two in sync
  (BundledAssetTest rebuilds the real asset through the Kotlin side and
  catches drift).

Filtering:
  - words: every char an Ethiopic syllable, and EITHER combined corpus
    frequency >= UNCONDITIONAL_KEEP_FREQ (frequent enough that the corpora
    attest it on their own) OR frequency >= MIN_WORD_FREQ *and* the folded
    form appears in one of the validation wordlists. Rationale: rare tokens
    in a scraped corpus are disproportionately typos, so a rare word needs
    independent attestation from a second source to be kept, while the
    wordlists alone can't carry the dictionary (spellcheck-oriented: few
    proper nouns, limited inflection coverage), which is why frequent corpus
    words pass without them.
  - abbreviations: Ethiopic runs joined by '.' or '/' with an optional
    trailing '.' (ዓ.ም, ዶ/ር, ት/ቤት, እ.ኤ.አ.), frequency >= MIN_ABBREV_FREQ,
    no wordlist check (they have no such entries).
  - everything else is dropped.

Enrichment:
  Words in the abdulmunim wordlist whose folded form wasn't already kept
  are appended at frequency ENRICH_FREQ (= 1, below MIN_WORD_FREQ): they
  complete and are fuzzy-matchable, but rank below every corpus-attested
  word. The Hunspell list is deliberately NOT merged in this way -- it is
  machine-expanded (single letters, odd forms) and was the old, low-quality
  dictionary; it only serves as attestation.

Run: `python3 tools/build_amharic_dict.py Term_Frequency.txt`
(the vendored gzipped sources next to this script are picked up automatically)
"""

import gzip
import io
import os
import re
import sys
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, ".."))
OUT = os.path.join(REPO, "app", "src", "main", "assets", "amharic_words.dat")

MIN_WORD_FREQ = 2
MIN_ABBREV_FREQ = 50
UNCONDITIONAL_KEEP_FREQ = 10
ENRICH_FREQ = 1

EXTRA_CORPORA = [os.path.join(HERE, "term_frequency_yididiyan.txt.gz")]
VALIDATION_LISTS = [
    os.path.join(HERE, "am_et_hunspell_words.txt.gz"),
    os.path.join(HERE, "wordlist_abdulmunim.txt.gz"),
]
ENRICH_LIST = os.path.join(HERE, "wordlist_abdulmunim.txt.gz")

# Ethiopic syllables: base block minus punctuation/numerals (U+1360-U+137C),
# plus the Extended / Supplement / Extended-A blocks. Deliberately excludes
# the combining gemination marks U+135D-U+135F, which are stripped instead.
SYLLABLE = r"ሀ-ፚᎀ-ᎏⶀ-ⷞꬁ-ꬮ"
WORD_RE = re.compile(rf"^[{SYLLABLE}]+$")
ABBREV_RE = re.compile(rf"^[{SYLLABLE}]+(?:[./][{SYLLABLE}]+)+\.?$")
GEMINATION_RE = re.compile(r"[፝-፟]")

# Mirror of transliteration/EthiopicNormalizer.kt -- keep in sync.
FOLD = {}


def _fold_series(variants, canonical):
    assert len(variants) == len(canonical)
    FOLD.update(zip(variants, canonical))


# ሃ folds with ሀ (order-1/order-4 laryngeal merge), hence the repeated ሀ in
# the 4th slot; same for ዓ -> አ.
_fold_series("ሐሑሒሓሔሕሖ", "ሀሁሂሀሄህሆ")
_fold_series("ኀኁኂኃኄኅኆ", "ሀሁሂሀሄህሆ")
FOLD["ሃ"] = "ሀ"
FOLD["ሗ"] = "ኋ"
_fold_series("ሠሡሢሣሤሥሦሧ", "ሰሱሲሳሴስሶሷ")
_fold_series("ዐዑዒዓዔዕዖ", "አኡኢአኤእኦ")
FOLD["ኣ"] = "አ"
_fold_series("ፀፁፂፃፄፅፆ", "ጸጹጺጻጼጽጾ")


def fold(token):
    return "".join(FOLD.get(c, c) for c in token)


def read_words(path):
    """One word per line, plain or gzipped, original spellings."""
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as f:
        return [line.strip() for line in f if line.strip()]


def iter_freq_pairs(path):
    """(token, frequency) pairs from a term-frequency dump, plain or gzipped.
    Accepts both line formats found in the wild -- `frequency<TAB>token`
    (CACO, with a header line that parses as junk and is skipped) and
    `token<SPACE>frequency` (yididiyan) -- keyed off which side is numeric."""
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as f:
        for line in f:
            parts = line.split()
            if len(parts) != 2:
                continue
            if parts[0].isdigit() and not parts[1].isdigit():
                yield parts[1], int(parts[0])
            elif parts[1].isdigit() and not parts[0].isdigit():
                yield parts[0], int(parts[1])


def main():
    if len(sys.argv) != 2:
        sys.exit(f"usage: {sys.argv[0]} <Term_Frequency.txt>")
    corpora = [sys.argv[1]] + EXTRA_CORPORA

    # Folding both sides means a variant spelling on either side still
    # counts as attestation.
    validated_words = set()
    for path in VALIDATION_LISTS:
        validated_words.update(fold(w) for w in read_words(path))

    # folded key -> {surface spelling -> summed frequency}. NFC normalization /
    # mark-stripping and homoglyph folding all merge source tokens; the
    # surface spellings under one key stay separate here so the most frequent
    # one can be picked as the display form below.
    merged = {}
    total = dropped = 0
    for path in corpora:
        for raw_token, freq in iter_freq_pairs(path):
            total += 1
            token = GEMINATION_RE.sub("", unicodedata.normalize("NFC", raw_token))
            if WORD_RE.match(token) or ABBREV_RE.match(token):
                variants = merged.setdefault(fold(token), {})
                variants[token] = variants.get(token, 0) + freq
            else:
                dropped += 1

    words = abbrevs = unvalidated_dropped = 0
    rows = []
    for key, variants in merged.items():
        freq = sum(variants.values())
        if WORD_RE.match(key):
            if freq < MIN_WORD_FREQ:
                continue
            if freq < UNCONDITIONAL_KEEP_FREQ and key not in validated_words:
                unvalidated_dropped += 1
                continue
            words += 1
        else:
            if freq < MIN_ABBREV_FREQ:
                continue
            abbrevs += 1
        # Display form: most frequent surface spelling; spelling order breaks
        # ties so regeneration is deterministic.
        display = max(variants, key=lambda v: (variants[v], v))
        assert all(ord(c) <= 0xFFFF for c in key), f"non-BMP char in {key!r}"
        rows.append((key, display, freq))

    # Enrichment: abdulmunim words not already kept above, at rank-floor
    # frequency. Spellings folding onto one key collapse here too -- prefer
    # the spelling that IS the folded key, else the smallest, for determinism.
    kept_keys = {key for key, _, _ in rows}
    enrich = {}
    for word in read_words(ENRICH_LIST):
        word = GEMINATION_RE.sub("", unicodedata.normalize("NFC", word))
        key = fold(word)
        if not WORD_RE.match(word) or key in kept_keys:
            continue
        best = enrich.get(key)
        if best is None:
            enrich[key] = word
        elif best != key and (word == key or word < best):
            enrich[key] = word
    enriched = len(enrich)
    for key, display in enrich.items():
        assert all(ord(c) <= 0xFFFF for c in key), f"non-BMP char in {key!r}"
        rows.append((key, display, ENRICH_FREQ))

    # Sorted by the FOLDED key (what WordTrie sorts/matches on), not the
    # display form. Code-point sort == UTF-16 code-unit sort, given the BMP
    # assert above.
    rows.sort(key=lambda r: r[0])
    rows = [(display, freq) for _, display, freq in rows]

    buf = io.BytesIO()
    # mtime=0 keeps the gzip output byte-stable across runs for the same input.
    with gzip.GzipFile(fileobj=buf, mode="wb", compresslevel=9, mtime=0) as gz:
        gz.write("".join(f"{w}\t{c}\n" for w, c in rows).encode("utf-8"))
    data = buf.getvalue()

    with open(OUT, "wb") as f:
        f.write(data)

    print(f"wrote {OUT}")
    print(f"  source tokens: {total:,}  (non-Amharic dropped: {dropped:,})")
    print(f"  kept: {len(rows):,}  (corpus words: {words:,}, abbreviations: {abbrevs:,}, "
          f"wordlist-enriched: {enriched:,})")
    print(f"  rare words dropped for lacking wordlist attestation: {unvalidated_dropped:,}")
    folded = sum(1 for w, _ in rows if w != fold(w))
    print(f"  entries whose display form differs from the folded key: {folded:,}")
    print(f"  gzip size: {len(data):,} bytes")


if __name__ == "__main__":
    main()
