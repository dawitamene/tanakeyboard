#!/usr/bin/env python3
"""
Regenerates app/src/main/assets/english_words.dat -- the English suggestion
dictionary consumed by suggestion/WordDictionary.kt -> WordTrie.kt.

Output format (unchanged, so the loader needs no changes):
    - one `word<TAB>frequency` line per entry, UTF-8
    - sorted by descending frequency
    - gzip-compressed, written with a `.dat` extension (NOT `.gz`) because the
      Android Gradle Plugin auto-decompresses `.gz` assets at build time.

What's new vs. the old asset: entries now carry CANONICAL CASING so proper
nouns suggest capitalized ("england" typed -> "England" suggested). The trie
matches case-insensitively and returns the stored casing (see WordTrie.kt).

Pipeline:
  1. Base vocabulary + real frequencies: hermitdave FrequencyWords English full
     OpenSubtitles list (MIT). Top TARGET_WORDS tokens by frequency. Lowercase.
  2. Casing overlay: a lowercase->canonical map built from
       - tools/proper_nouns.txt  (curated, vendored, ALWAYS force-applied)
       - downloaded first-name and world-city lists (best-effort; applied only
         to words that are NOT common ordinary words, so "may"/"mark"/"will"
         stay lowercase).
  3. Contractions (don't, I'm, it's, ...) re-added with heuristic frequencies.

Sources are cached under tools/.cache/ (gitignored). Requires internet the
first time. Run: `python3 tools/build_english_dict.py`
"""

import csv
import gzip
import io
import os
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, ".cache")
REPO = os.path.abspath(os.path.join(HERE, ".."))
OUT = os.path.join(REPO, "app", "src", "main", "assets", "english_words.dat")
CURATED = os.path.join(HERE, "proper_nouns.txt")

TARGET_WORDS = 250_000
# Words among the top COMMON_GUARD by frequency are treated as ordinary
# vocabulary: the fuzzy (downloaded) name/city casing is NOT applied to them,
# so common words that happen to be names ("may", "mark", "will", "rose")
# stay lowercase. The curated list overrides this guard unconditionally.
COMMON_GUARD = 20_000

BASE_URL = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_full.txt"
NAMES_URL = "https://raw.githubusercontent.com/dominictarr/random-name/master/first-names.txt"
CITIES_URL = "https://raw.githubusercontent.com/datasets/world-cities/master/data/world-cities.csv"

# Reconstructed contractions with heuristic frequencies (the source tokenizer
# splits "don't" into "don" + "t"). Values roughly track the old asset.
CONTRACTIONS = {
    "it's": 4_500_000, "i'm": 4_386_306, "don't": 4_100_000, "that's": 3_500_000,
    "you're": 2_600_000, "he's": 2_200_000, "can't": 1_400_000, "didn't": 1_300_000,
    "i've": 1_200_000, "i'll": 1_150_000, "she's": 1_100_000, "there's": 1_000_000,
    "we're": 800_000, "isn't": 700_000, "won't": 690_000, "let's": 650_000,
    "wasn't": 600_000, "doesn't": 560_000, "you've": 520_000, "we've": 470_000,
    "they're": 450_000, "aren't": 380_000, "i'd": 360_000, "you'll": 340_000,
    "haven't": 320_000, "wouldn't": 300_000, "couldn't": 280_000, "we'll": 260_000,
    "what's": 900_000, "who's": 300_000, "here's": 320_000, "they'll": 180_000,
    "he'll": 150_000, "she'll": 140_000, "you'd": 160_000, "they've": 150_000,
    "hasn't": 130_000, "weren't": 150_000, "shouldn't": 140_000, "would've": 90_000,
    "could've": 80_000, "should've": 80_000, "they'd": 90_000, "he'd": 90_000,
    "she'd": 80_000, "we'd": 90_000, "ain't": 220_000,
}

# Frequency assigned to a curated/name-only proper noun that never appears in
# the base corpus -- present and suggestable, but ranked below common words.
EXTRA_NOUN_FREQ = 1_000


def fetch(url, name):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, name)
    if not os.path.exists(path):
        print(f"downloading {name} ...", file=sys.stderr)
        req = urllib.request.Request(url, headers={"User-Agent": "tana-dict-build"})
        with urllib.request.urlopen(req, timeout=120) as r, open(path, "wb") as f:
            f.write(r.read())
    return path


def load_base():
    """Returns list[(lower_word, freq)] for the top TARGET_WORDS tokens."""
    path = fetch(BASE_URL, "en_full.txt")
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            parts = line.split(" ")
            if len(parts) != 2:
                continue
            word, freq = parts[0].strip(), parts[1].strip()
            if not word or not freq.isdigit():
                continue
            # Keep letters + apostrophes only; drop numeric/garbage tokens.
            if not all(ch.isalpha() or ch == "'" for ch in word):
                continue
            out.append((word.lower(), int(freq)))
            if len(out) >= TARGET_WORDS:
                break
    return out


def load_curated():
    """Curated canonical proper nouns. Always force-applied. Returns dict."""
    force = {}
    with open(CURATED, encoding="utf-8") as f:
        for line in f:
            w = line.split("#", 1)[0].strip()
            if w:
                force[w.lower()] = w
    return force


def load_fuzzy_casing():
    """Best-effort lowercase->canonical from downloaded name/city lists."""
    casing = {}

    def add(word):
        word = word.strip()
        # single-token, alphabetic, has an uppercase letter to be worth casing
        if not word or " " in word or not word.isalpha():
            return
        if word == word.lower():
            return
        casing.setdefault(word.lower(), word)

    try:
        with open(fetch(NAMES_URL, "first-names.txt"), encoding="utf-8") as f:
            for line in f:
                add(line)
    except Exception as e:  # noqa: BLE001 - sources are optional
        print(f"warning: names source skipped ({e})", file=sys.stderr)

    try:
        with open(fetch(CITIES_URL, "world-cities.csv"), encoding="utf-8") as f:
            for row in csv.DictReader(f):
                add(row.get("name", ""))
    except Exception as e:  # noqa: BLE001 - sources are optional
        print(f"warning: cities source skipped ({e})", file=sys.stderr)

    return casing


def main():
    base = load_base()
    force = load_curated()
    fuzzy = load_fuzzy_casing()

    common = {w for w, _ in base[:COMMON_GUARD]}

    # word(canonical) -> freq, keyed for de-dup by lowercase.
    out = {}

    def put(canonical, freq):
        key = canonical.lower()
        prev = out.get(key)
        if prev is None or freq > prev[1]:
            out[key] = (canonical, freq)

    for lower, freq in base:
        if lower in force:
            canonical = force[lower]
        elif lower in fuzzy and lower not in common:
            canonical = fuzzy[lower]
        else:
            canonical = lower
        put(canonical, freq)

    # Curated proper nouns absent from the corpus: add so they're suggestable.
    for lower, canonical in force.items():
        if lower not in out:
            put(canonical, EXTRA_NOUN_FREQ)

    # Contractions (override any partial base entry).
    for word, freq in CONTRACTIONS.items():
        put(word, freq)

    rows = sorted(out.values(), key=lambda wf: (-wf[1], wf[0]))

    buf = io.BytesIO()
    # mtime=0 keeps the gzip output byte-stable across runs for the same input.
    with gzip.GzipFile(fileobj=buf, mode="wb", compresslevel=9, mtime=0) as gz:
        gz.write("".join(f"{w}\t{c}\n" for w, c in rows).encode("utf-8"))
    data = buf.getvalue()

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "wb") as f:
        f.write(data)

    caps = sum(1 for w, _ in rows if w[:1].isupper())
    print(f"wrote {OUT}")
    print(f"  entries: {len(rows):,}  (capitalized: {caps:,})")
    print(f"  gzip size: {len(data):,} bytes")


if __name__ == "__main__":
    main()
