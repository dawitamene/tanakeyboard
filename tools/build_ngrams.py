#!/usr/bin/env python3
"""
Regenerates app/src/main/assets/amharic_ngrams.dat -- the Amharic bigram /
trigram next-word model consumed by suggestion/NgramModel.kt (via
suggestion/NgramDictionary.kt).

Source: one or more raw Amharic corpora (e.g. CACO_TEXT.txt plus the
abdulmunim web/social-media corpus), one sentence-ish line each; counts from
all corpora are summed. Pre-tokenized text (punctuation as its own token) and
raw text (punctuation attached to words) both work -- see tokenization.

Tokenization mirrors build_amharic_dict.py: NFC-normalize, strip the
combining gemination marks U+135D-U+135F. A whitespace token fully made of
Ethiopic syllables (WORD_RE) is a word. A token with leading/trailing
punctuation around a syllabic core ("በቴሌግራም።", "«ሰላም»") contributes the core
as a word plus an n-gram BOUNDARY on the punctuation side(s). Everything else
-- standalone punctuation, digits, Latin, mixed abbreviations like "ዓ.ም" or
"በ2007" -- is wholly a boundary: word runs split there and at line ends, so
no bigram or trigram ever spans punctuation or a number.

FOLDING + DICTIONARY GATING (this is what ties the model to the dictionary):
every word is homoglyph-FOLDED (table imported from build_amharic_dict.py)
and must resolve to an entry of the built amharic_words.dat; it is then
counted AS THAT ENTRY'S DISPLAY FORM. So variant spellings merge into one
context (ሀገር/ሃገር/ሐገር evidence pools), every prediction the model can emit
is a real, canonically spelled dictionary word, and prediction strings agree
exactly with dictionary suggestion strings (the n-gram boost in
CandidateRanker matches on normalized keys). Corpus junk never enters the
model: a token whose folded form isn't in the dictionary is skipped -- it
breaks pair adjacency (no bigram is counted across it) without acting as a
sentence boundary. Regenerate the dictionary BEFORE the n-gram model.

Counting is two passes per corpus to keep memory bounded (~330MB total):
  1. bigram counts over dictionary words; prune to contexts with total count
     >= --bigram-min-context, successors with count >= --bigram-min-succ,
     top --k-bigram successors per context;
  2. trigram counts ONLY where (w1, w2) survived as a bigram context (a
     useful trigram context is by definition a frequent bigram); prune
     successors with count >= --trigram-min-succ, top --k-trigram.

Output: a binary model, gzip-compressed (mtime=0 -> byte-stable), written
with a `.dat` extension (NOT `.gz`; AGP auto-decompresses `.gz` assets).
All integers big-endian to match Kotlin's DataInputStream:

    magic "ANGM" (4 bytes), version u8 = 2 (Amharic, caseless) or 3 (English)
    vocabCount: int32
    vocab: vocabCount x (u16 UTF-8 byte length + bytes), DISPLAY forms
           sorted by their FOLDED key in UTF-16 code-unit order (folded keys
           are unique -- one dictionary entry per key), so the Kotlin side
           can binary-search by folded key; word id = index
    bigramContextCount: int32
    bigramContexts:   int32[n]    context word ids, ascending
    bigramOffsets:    int32[n+1]  into the successor arrays
    bigramSuccessors: int32[...]  per-context sorted by count desc
    bigramWeights:    u8[...]     min(255, round(ln(count) * 24))
    bigramCasing:     u8[...]     (v3 ONLY) per-successor casing flag:
                                  0=as-is, 1=capitalize first, 2=all caps
    trigramContextCount: int32
    trigramContexts:  int64[m]    (id1 << 32) | id2, ascending
    trigramOffsets / trigramSuccessors / trigramWeights [/ trigramCasing]:
                                  as the bigram section

Version history: v2 = folded-key vocab order + dictionary-gated display forms;
v3 = v2 plus per-successor casing flags (English proper-noun casing).
NgramModel.kt parses v2 and v3; it rejects v1 assets loudly.

Run:  python3 tools/build_ngrams.py CACO_TEXT.txt amharic_corpus_abdulmunim.txt

ENGLISH (--lang english): instead of counting a raw corpus, read PRE-COMPILED
word n-gram frequency lists and gate them through english_words.dat. Emits the
v3 format (per-successor casing flags); the fold is per-char lowercase (matching
WordDictionary's default keying) and words are Latin letters + apostrophes.
Sources:
  - bigrams: Norvig count_2w.txt (Google Web Trillion Word Corpus),
    '<w1> <w2>\\t<count>' per line -- https://www.norvig.com/ngrams/count_2w.txt
  - trigrams: orgtre/google-books-ngram-frequency 3grams_english.csv,
    '<w1> <w2> <w3>,<freq>' with a header line.
      python3 tools/build_ngrams.py --lang english \
          --bigram-file tools/.cache/count_2w.txt \
          --trigram-file tools/.cache/3grams_english.csv

Test fixture (tiny model for JVM unit tests; no dictionary gating, display =
folded form):
      python3 tools/build_ngrams.py --test-fixture \
          app/src/test/resources/ngram_mini_corpus.txt \
          app/src/test/resources/ngram_fixture.dat
"""

import argparse
import gzip
import io
import math
import os
import re
import struct
import sys
import unicodedata
from collections import Counter

# Shared with the dictionary build: the homoglyph fold table (hand-mirror of
# transliteration/EthiopicNormalizer.kt) and the word/character classes.
from build_amharic_dict import fold, SYLLABLE, WORD_RE, GEMINATION_RE

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, ".."))
DEFAULT_OUT = os.path.join(REPO, "app", "src", "main", "assets", "amharic_ngrams.dat")
DICT_ASSET = os.path.join(REPO, "app", "src", "main", "assets", "amharic_words.dat")

# English uses per-char lowercase as its fold -- the analogue of the Amharic
# homoglyph fold, and identical to WordDictionary's default Char::lowercaseChar
# keying and build_english_dict.py's sort key (so the vocab order the Kotlin
# side binary-searches agrees). A word is letters with optional internal
# apostrophes ("don't", "o'clock"); dictionary entries are already clean.
ENGLISH_OUT = os.path.join(REPO, "app", "src", "main", "assets", "english_ngrams.dat")
ENGLISH_DICT_ASSET = os.path.join(REPO, "app", "src", "main", "assets", "english_words.dat")
EN_WORD_RE = re.compile(r"^[A-Za-z]+(?:'[A-Za-z]+)*$")


def en_fold(s):
    return "".join(c.lower() for c in s)

# A syllabic core with optional junk on either edge -- but a digit or Latin
# letter anywhere disqualifies the token entirely (matching the old
# whole-token rule for "በ2007"-style tokens, where the Ethiopic fragment is
# not a free-standing word).
EDGE_RE = re.compile(rf"^([^{SYLLABLE}0-9A-Za-z]*)([{SYLLABLE}]+)([^{SYLLABLE}0-9A-Za-z]*)$")

MAGIC = b"ANGM"
VERSION = 2


def load_display_map(asset=DICT_ASSET, fold_fn=fold, word_re=WORD_RE):
    """folded key -> display form, from the BUILT dictionary asset -- the
    single source of truth for what a real word is and how it's spelled."""
    display = {}
    with gzip.open(asset, "rt", encoding="utf-8") as f:
        for line in f:
            word = line.rsplit("\t", 1)[0]
            if word_re.match(word):  # abbreviations can't be n-gram tokens
                display[fold_fn(word)] = word
    return display


def word_runs(path, canonical):
    """Yields lists over each boundary-free stretch of the corpus, one entry
    per word: the word's canonical form per `canonical(normalized token)`, or
    None for a word the dictionary doesn't know (it blocks pair adjacency
    but, unlike punctuation, does not end the run)."""
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            run = []
            for token in line.split():
                token = GEMINATION_RE.sub(
                    "", unicodedata.normalize("NFC", token)
                )
                if WORD_RE.match(token):
                    run.append(canonical(token))
                    continue
                m = EDGE_RE.match(token)
                if m is None:
                    # Wholly non-word token: hard boundary.
                    if run:
                        yield run
                        run = []
                    continue
                lead, core, trail = m.groups()
                if lead and run:  # boundary BEFORE the core («ሰላም)
                    yield run
                    run = []
                run.append(canonical(core))
                if trail:  # boundary AFTER the core (በቴሌግራም።)
                    yield run
                    run = []
            if run:
                yield run


def quantize(count):
    return min(255, round(math.log(count) * 24))


def prune(counts, min_succ, top_k, min_context=0):
    """counts: {context: Counter(successor -> n)} ->
    {context: [(successor, weight)]} sorted by count desc."""
    kept = {}
    for ctx, succs in counts.items():
        if min_context and sum(succs.values()) < min_context:
            continue
        top = [
            (w, c) for w, c in succs.most_common(top_k) if c >= min_succ
        ]
        if top:
            kept[ctx] = [(w, quantize(c)) for w, c in top]
    return kept


def build_model(bigrams, trigrams, fold_fn=fold, bigram_flags=None, trigram_flags=None):
    """bigrams: {w1: [(succ, weight)]}, trigrams: {(w1, w2): [(succ, weight)]}
    -> gzipped binary bytes. [fold_fn] is the fold the vocab is sorted by (the
    Kotlin side binary-searches with the same one).

    When [bigram_flags]/[trigram_flags] are given (English), the model is
    written as VERSION 3: each successor gets a trailing u8 casing flag
    (0=as-is, 1=capitalize first letter, 2=all caps) so the predicted word can
    be shown in its per-context proper-noun casing. Flag maps are sparse dicts
    keyed by (context, successor_word) -> flag; a missing pair is 0. With no
    flags the output is byte-for-byte the v2 format (Amharic, caseless)."""
    include_casing = bigram_flags is not None or trigram_flags is not None
    version = 3 if include_casing else VERSION
    vocab = set()
    for ctx, succs in bigrams.items():
        vocab.add(ctx)
        vocab.update(w for w, _ in succs)
    for (w1, w2), succs in trigrams.items():
        vocab.add(w1)
        vocab.add(w2)
        vocab.update(w for w, _ in succs)
    for word in vocab:
        assert all(ord(c) <= 0xFFFF for c in word), f"non-BMP char in {word!r}"
    # Sorted by FOLDED key so the Kotlin side can binary-search a folded
    # lookup; keys must be unique for that search to be well-defined (they
    # are: one dictionary entry -- one folded key).
    vocab = sorted(vocab, key=fold_fn)
    for a, b in zip(vocab, vocab[1:]):
        assert fold_fn(a) < fold_fn(b), f"duplicate folded vocab key: {a!r} / {b!r}"
    word_id = {w: i for i, w in enumerate(vocab)}

    out = io.BytesIO()
    out.write(MAGIC)
    out.write(struct.pack(">B", version))
    out.write(struct.pack(">i", len(vocab)))
    for word in vocab:
        encoded = word.encode("utf-8")
        out.write(struct.pack(">H", len(encoded)))
        out.write(encoded)

    def write_section(entries, flags):
        """entries: sorted [(context_key_bytes, context_for_flags,
        [(succ_word, weight)])]. When casing is on, a per-successor u8 flag
        array (same order as the successor ids) follows the weights."""
        out.write(struct.pack(">i", len(entries)))
        for ctx_key, _, _ in entries:
            out.write(ctx_key)
        offset = 0
        out.write(struct.pack(">i", offset))
        for _, _, succs in entries:
            offset += len(succs)
            out.write(struct.pack(">i", offset))
        for _, _, succs in entries:
            for w, _ in succs:
                out.write(struct.pack(">i", word_id[w]))
        for _, _, succs in entries:
            for _, weight in succs:
                out.write(struct.pack(">B", weight))
        if include_casing:
            for _, ctx_flag, succs in entries:
                for w, _ in succs:
                    flag = flags.get((ctx_flag, w), 0) if flags else 0
                    out.write(struct.pack(">B", flag))

    bigram_entries = sorted(
        ((struct.pack(">i", word_id[ctx]), ctx, succs)
         for ctx, succs in bigrams.items()),
        key=lambda e: e[0],
    )
    write_section(bigram_entries, bigram_flags)

    trigram_entries = sorted(
        ((struct.pack(">q", (word_id[w1] << 32) | word_id[w2]), (w1, w2), succs)
         for (w1, w2), succs in trigrams.items()),
        key=lambda e: e[0],
    )
    write_section(trigram_entries, trigram_flags)

    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb", compresslevel=9, mtime=0) as gz:
        gz.write(out.getvalue())
    return buf.getvalue(), len(vocab), len(out.getvalue())


def parse_bigram_lines(path):
    """Norvig count_2w.txt lines: '<w1> <w2>\\t<count>' -> (w1_raw, w2_raw,
    count), tokens in their ORIGINAL casing (gating/folding is the caller's)."""
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            phrase, _, count = line.rstrip("\n").rpartition("\t")
            if not phrase or not count.isdigit():
                continue
            parts = phrase.split()
            if len(parts) == 2:
                yield parts[0], parts[1], int(count)


def parse_trigram_lines(path):
    """orgtre 3grams_english.csv lines: '<w1> <w2> <w3>,<freq>' (with a
    'ngram,freq' header, skipped since 'freq' isn't a digit) -> (w1_raw, w2_raw,
    w3_raw, count) in ORIGINAL casing. The cleaned ngram field has no commas,
    so a single rpartition splits off the count safely."""
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            phrase, _, count = line.rstrip("\n").rpartition(",")
            if not phrase or not count.isdigit():
                continue
            parts = phrase.split()
            if len(parts) == 3:
                yield parts[0], parts[1], parts[2], int(count)


def casing_flag(observations, display):
    """observations: Counter(cased_form -> weight) for a successor in a given
    context, from the cased trigram source. Returns the u8 casing flag to store
    (0=leave the dictionary display as-is, 1=capitalize first letter,
    2=all caps), read off the dominant cased form. Only ever ADDS capitals --
    a lowercase-dominant observation leaves the dictionary casing untouched, so
    genuine proper nouns already capitalized in the dictionary are never
    lowered."""
    if not observations:
        return 0
    cased = observations.most_common(1)[0][0]
    if cased == cased.lower():
        return 0
    if len(cased) > 1 and cased == cased.upper():
        return 2
    if cased[:1].isupper():
        return 1
    return 0


def build_english(args):
    """English path: read PRE-COUNTED compiled n-gram lists (no raw corpus),
    gate every token through english_words.dat, and emit the v3 binary model
    (per-successor casing flags). Unlike the Amharic corpus path there is no
    two-pass memory dance (the compiled lists are small), and trigrams are NOT
    gated on surviving bigram contexts -- they come from a separate curated
    source, so a useful trigram whose bigram prefix isn't in the top bigram
    list is still kept and served directly (falling back to bigrams only when
    its own context misses).

    Casing comes ONLY from the trigram source: the orgtre lists preserve
    proper-noun casing ("New York", "the United States"), while the Norvig
    bigram list capitalizes only sentence-initial first words (its successors
    are always lowercase), so it carries no reliable successor casing. Each
    trigram contributes its two adjacent pairs to the bigram casing evidence
    too, so "in New York" / "New York Times" teach that York is capitalized
    after New even though the count for New->York comes from Norvig."""
    if not args.bigram_file or not args.trigram_file:
        sys.exit("english mode needs --bigram-file and --trigram-file")
    out = ENGLISH_OUT if args.out == DEFAULT_OUT else args.out
    display = load_display_map(ENGLISH_DICT_ASSET, en_fold, EN_WORD_RE)
    print(f"dictionary: {len(display):,} folded keys", file=sys.stderr)
    canonical = lambda token: display.get(en_fold(token))  # noqa: E731

    bigram_counts = {}
    for r1, r2, c in parse_bigram_lines(args.bigram_file):
        w1, w2 = canonical(r1), canonical(r2)
        if w1 is not None and w2 is not None:
            bigram_counts.setdefault(w1, Counter())[w2] += c
    raw_bigram_contexts = len(bigram_counts)
    bigrams = prune(bigram_counts, args.bigram_min_succ, args.k_bigram,
                    args.bigram_min_context)
    print(f"bigrams: {raw_bigram_contexts:,} contexts, kept {len(bigrams):,} "
          f"({sum(len(s) for s in bigrams.values()):,} successors)",
          file=sys.stderr)

    # Trigram counts (gated) + casing evidence (raw, from the cased source).
    trigram_counts = {}
    pair_case = {}   # (fold_w1, fold_w2)          -> Counter(cased_w2)
    triple_case = {}  # (fold_w1, fold_w2, fold_w3) -> Counter(cased_w3)
    for r1, r2, r3, c in parse_trigram_lines(args.trigram_file):
        f1, f2, f3 = en_fold(r1), en_fold(r2), en_fold(r3)
        pair_case.setdefault((f1, f2), Counter())[r2] += c
        pair_case.setdefault((f2, f3), Counter())[r3] += c
        triple_case.setdefault((f1, f2, f3), Counter())[r3] += c
        w1, w2, w3 = canonical(r1), canonical(r2), canonical(r3)
        if w1 is not None and w2 is not None and w3 is not None:
            trigram_counts.setdefault((w1, w2), Counter())[w3] += c
    raw_trigram_contexts = len(trigram_counts)
    trigrams = prune(trigram_counts, args.trigram_min_succ, args.k_trigram)
    # A trigram context whose predictions match its bigram backoff exactly adds
    # bytes but no information; drop it (same optimization as the Amharic path).
    trigrams = {
        ctx: succs for ctx, succs in trigrams.items()
        if [w for w, _ in succs] != [w for w, _ in bigrams.get(ctx[1], [])][:len(succs)]
    }
    print(f"trigrams: {raw_trigram_contexts:,} contexts, kept {len(trigrams):,} "
          f"({sum(len(s) for s in trigrams.values()):,} successors)",
          file=sys.stderr)

    # Per-successor casing flags (sparse: only non-zero entries stored).
    bigram_flags = {}
    for ctx, succs in bigrams.items():
        for w, _ in succs:
            flag = casing_flag(pair_case.get((en_fold(ctx), en_fold(w))), w)
            if flag:
                bigram_flags[(ctx, w)] = flag
    trigram_flags = {}
    for (w1, w2), succs in trigrams.items():
        for w, _ in succs:
            obs = triple_case.get((en_fold(w1), en_fold(w2), en_fold(w))) \
                or pair_case.get((en_fold(w2), en_fold(w)))
            flag = casing_flag(obs, w)
            if flag:
                trigram_flags[((w1, w2), w)] = flag
    print(f"casing: {len(bigram_flags):,} bigram + {len(trigram_flags):,} "
          f"trigram successors recased", file=sys.stderr)

    data, vocab_count, raw_size = build_model(
        bigrams, trigrams, en_fold, bigram_flags, trigram_flags
    )
    with open(out, "wb") as f:
        f.write(data)
    print(f"wrote {out}")
    print(f"  vocab: {vocab_count:,}")
    print(f"  raw: {raw_size:,} bytes, gzip: {len(data):,} bytes")


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[1])
    parser.add_argument("corpora", nargs="*")
    parser.add_argument("--lang", choices=["amharic", "english"], default="amharic",
                        help="amharic: count from raw corpora (positional args); "
                             "english: read compiled --bigram-file/--trigram-file")
    parser.add_argument("--bigram-file",
                        help="english mode: pre-counted bigrams, "
                             "'<w1> <w2>\\t<count>' per line (Norvig count_2w.txt)")
    parser.add_argument("--trigram-file",
                        help="english mode: pre-counted trigrams, "
                             "'<w1> <w2> <w3>,<freq>' per line (orgtre CSV)")
    parser.add_argument("--out", default=DEFAULT_OUT)
    parser.add_argument("--test-fixture", action="store_true",
                        help="tiny-corpus mode: no pruning thresholds, no "
                             "dictionary gating (display = folded form); "
                             "pass <corpus> <out> as the two positionals")
    parser.add_argument("--bigram-min-context", type=int, default=4)
    parser.add_argument("--bigram-min-succ", type=int, default=3)
    parser.add_argument("--k-bigram", type=int, default=8)
    parser.add_argument("--trigram-min-succ", type=int, default=3)
    parser.add_argument("--k-trigram", type=int, default=6)
    args = parser.parse_args()
    if args.lang == "english":
        build_english(args)
        return
    if args.test_fixture:
        args.bigram_min_context = 1
        args.bigram_min_succ = 1
        args.trigram_min_succ = 1
        # Fixture mode: <corpus> <out> as positionals, dictionary-independent
        # (the fixture must not change whenever the real dictionary does).
        corpora = args.corpora[:1]
        out = args.corpora[1] if len(args.corpora) > 1 else args.out
        canonical = fold
    elif not args.corpora:
        sys.exit("amharic mode needs at least one corpus path")
    else:
        corpora = args.corpora
        out = args.out
        display = load_display_map()
        print(f"dictionary: {len(display):,} folded keys", file=sys.stderr)
        canonical = lambda token: display.get(fold(token))  # noqa: E731

    # Pass 1: bigrams over dictionary words.
    bigram_counts = {}
    for path in corpora:
        for run in word_runs(path, canonical):
            for a, b in zip(run, run[1:]):
                if a is not None and b is not None:
                    bigram_counts.setdefault(a, Counter())[b] += 1
    raw_bigram_contexts = len(bigram_counts)
    bigrams = prune(bigram_counts, args.bigram_min_succ, args.k_bigram,
                    args.bigram_min_context)
    del bigram_counts
    print(f"pass 1: {raw_bigram_contexts:,} bigram contexts, "
          f"kept {len(bigrams):,} "
          f"({sum(len(s) for s in bigrams.values()):,} successors)",
          file=sys.stderr)

    # Pass 2: trigrams gated on surviving bigram contexts.
    kept_pairs = {
        (ctx, w) for ctx, succs in bigrams.items() for w, _ in succs
    }
    trigram_counts = {}
    for path in corpora:
        for run in word_runs(path, canonical):
            for a, b, c in zip(run, run[1:], run[2:]):
                if (a, b) in kept_pairs and c is not None:
                    trigram_counts.setdefault((a, b), Counter())[c] += 1
    raw_trigram_contexts = len(trigram_counts)
    trigrams = prune(trigram_counts, args.trigram_min_succ, args.k_trigram)
    del trigram_counts
    # A trigram context whose predictions match its bigram backoff exactly
    # adds bytes but no information; drop it.
    trigrams = {
        ctx: succs for ctx, succs in trigrams.items()
        if [w for w, _ in succs] != [w for w, _ in bigrams.get(ctx[1], [])][:len(succs)]
    }
    print(f"pass 2: {raw_trigram_contexts:,} trigram contexts, "
          f"kept {len(trigrams):,} "
          f"({sum(len(s) for s in trigrams.values()):,} successors)",
          file=sys.stderr)

    data, vocab_count, raw_size = build_model(bigrams, trigrams)
    with open(out, "wb") as f:
        f.write(data)
    print(f"wrote {out}")
    print(f"  vocab: {vocab_count:,}")
    print(f"  raw: {raw_size:,} bytes, gzip: {len(data):,} bytes")


if __name__ == "__main__":
    main()
