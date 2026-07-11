#!/usr/bin/env python3
"""
Regenerates app/src/main/assets/amharic_words.dat -- the Amharic suggestion
dictionary consumed by suggestion/WordDictionary.kt -> WordTrie.kt.

Source: a corpus term-frequency dump (e.g. Term_Frequency.txt), one
`frequency<TAB>token` line per entry with a single header line. The dump is
raw tokenizer output, so it mixes real words with punctuation tokens,
numbers/percentages, Latin fragments, and one-off typos.

Output format:
    - one `word<TAB>frequency` line per entry, UTF-8
    - SORTED BY THE WORD, in UTF-16 code-unit order -- WordTrie.build streams
      the asset into a flat-array trie and requires sorted input (it throws on
      out-of-order lines). All kept chars are BMP, so Python's code-point sort
      is identical to UTF-16 code-unit order (asserted below).
    - gzip-compressed, written with a `.dat` extension (NOT `.gz`) because the
      Android Gradle Plugin auto-decompresses `.gz` assets at build time.

Filtering:
  - words: every char an Ethiopic syllable, corpus frequency >= MIN_WORD_FREQ
    (singletons are disproportionately typos in scraped corpora);
  - abbreviations: Ethiopic runs joined by '.' or '/' with an optional
    trailing '.' (ዓ.ም, ዶ/ር, ት/ቤት, እ.ኤ.አ.), frequency >= MIN_ABBREV_FREQ.
    They can't be *composed* (period/slash commit the word) but still appear
    as tap-to-complete suggestions for their fidel prefix.
  - everything else is dropped.

Run: `python3 tools/build_amharic_dict.py Term_Frequency.txt`
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

# Ethiopic syllables: base block minus punctuation/numerals (U+1360-U+137C),
# plus the Extended / Supplement / Extended-A blocks. Deliberately excludes
# the combining gemination marks U+135D-U+135F, which are stripped instead.
SYLLABLE = r"ሀ-ፚᎀ-ᎏⶀ-ⷞꬁ-ꬮ"
WORD_RE = re.compile(rf"^[{SYLLABLE}]+$")
ABBREV_RE = re.compile(rf"^[{SYLLABLE}]+(?:[./][{SYLLABLE}]+)+\.?$")
GEMINATION_RE = re.compile(r"[፝-፟]")


def main():
    if len(sys.argv) != 2:
        sys.exit(f"usage: {sys.argv[0]} <Term_Frequency.txt>")
    src = sys.argv[1]

    # token -> summed frequency (NFC normalization / mark-stripping can merge
    # several source tokens into one).
    merged = {}
    total = dropped = 0
    with open(src, encoding="utf-8") as f:
        f.readline()  # header: "Frequency Token"
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) != 2 or not parts[0].isdigit():
                continue
            total += 1
            freq = int(parts[0])
            token = GEMINATION_RE.sub("", unicodedata.normalize("NFC", parts[1]))
            if WORD_RE.match(token) or ABBREV_RE.match(token):
                merged[token] = merged.get(token, 0) + freq
            else:
                dropped += 1

    words = abbrevs = 0
    rows = []
    for token, freq in merged.items():
        if WORD_RE.match(token):
            if freq < MIN_WORD_FREQ:
                continue
            words += 1
        else:
            if freq < MIN_ABBREV_FREQ:
                continue
            abbrevs += 1
        assert all(ord(c) <= 0xFFFF for c in token), f"non-BMP char in {token!r}"
        rows.append((token, freq))

    # Code-point sort == UTF-16 code-unit sort, given the BMP assert above.
    rows.sort(key=lambda wf: wf[0])

    buf = io.BytesIO()
    # mtime=0 keeps the gzip output byte-stable across runs for the same input.
    with gzip.GzipFile(fileobj=buf, mode="wb", compresslevel=9, mtime=0) as gz:
        gz.write("".join(f"{w}\t{c}\n" for w, c in rows).encode("utf-8"))
    data = buf.getvalue()

    with open(OUT, "wb") as f:
        f.write(data)

    print(f"wrote {OUT}")
    print(f"  source tokens: {total:,}  (non-Amharic dropped: {dropped:,})")
    print(f"  kept: {len(rows):,}  (words: {words:,}, abbreviations: {abbrevs:,})")
    print(f"  gzip size: {len(data):,} bytes")


if __name__ == "__main__":
    main()
