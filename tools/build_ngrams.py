#!/usr/bin/env python3
"""
Regenerates app/src/main/assets/amharic_ngrams.dat -- the Amharic bigram /
trigram next-word model consumed by suggestion/NgramModel.kt (via
suggestion/NgramDictionary.kt).

Source: a raw Amharic corpus (e.g. CACO_TEXT.txt), one pre-tokenized sentence
per line with tokens separated by spaces (punctuation and numbers are already
their own tokens).

Tokenization mirrors build_amharic_dict.py exactly: NFC-normalize, strip the
combining gemination marks U+135D-U+135F, and keep only tokens fully made of
Ethiopic syllables (WORD_RE). Every other token -- Ethiopic punctuation,
quotes, digits, Latin, mixed abbreviations like "ዓ.ም" -- is an n-gram
BOUNDARY: word runs are split there and at line ends, so no bigram or trigram
ever spans punctuation or a number.

Counting is three passes to keep memory bounded on a ~250MB corpus:
  1. unigram counts -> kept vocabulary (count >= MIN_WORD_FREQ, same
     singleton-typo rule as the dictionary);
  2. bigram counts over kept-vocab words; prune to contexts with total count
     >= --bigram-min-context, successors with count >= --bigram-min-succ,
     top --k-bigram successors per context;
  3. trigram counts ONLY where (w1, w2) survived as a bigram context (a
     useful trigram context is by definition a frequent bigram); prune
     successors with count >= --trigram-min-succ, top --k-trigram.

Output: a binary model, gzip-compressed (mtime=0 -> byte-stable), written
with a `.dat` extension (NOT `.gz`; AGP auto-decompresses `.gz` assets).
All integers big-endian to match Kotlin's DataInputStream:

    magic "ANGM" (4 bytes), version u8 = 1
    vocabCount: int32
    vocab: vocabCount x (u16 UTF-8 byte length + bytes),
           sorted by code point; word id = index
    bigramContextCount: int32
    bigramContexts:   int32[n]    context word ids, ascending
    bigramOffsets:    int32[n+1]  into the successor arrays
    bigramSuccessors: int32[...]  per-context sorted by count desc
    bigramWeights:    u8[...]     min(255, round(ln(count) * 24))
    trigramContextCount: int32
    trigramContexts:  int64[m]    (id1 << 32) | id2, ascending
    trigramOffsets / trigramSuccessors / trigramWeights: as bigram section

Run:  python3 tools/build_ngrams.py CACO_TEXT.txt
Test fixture (tiny model for JVM unit tests):
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

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, ".."))
DEFAULT_OUT = os.path.join(REPO, "app", "src", "main", "assets", "amharic_ngrams.dat")

MIN_WORD_FREQ = 2

# Keep in sync with build_amharic_dict.py.
SYLLABLE = r"ሀ-ፚᎀ-ᎏⶀ-ⷞꬁ-ꬮ"
WORD_RE = re.compile(rf"^[{SYLLABLE}]+$")
GEMINATION_RE = re.compile(r"[፝-፟]")

MAGIC = b"ANGM"
VERSION = 1


def word_runs(path):
    """Yields lists of normalized Ethiopic words; runs break at any
    non-word token and at line ends."""
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            run = []
            for token in line.split():
                token = GEMINATION_RE.sub(
                    "", unicodedata.normalize("NFC", token)
                )
                if WORD_RE.match(token):
                    run.append(token)
                elif run:
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


def build_model(bigrams, trigrams):
    """bigrams: {w1: [(succ, weight)]}, trigrams: {(w1, w2): [(succ, weight)]}
    -> gzipped binary bytes."""
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
    vocab = sorted(vocab)
    word_id = {w: i for i, w in enumerate(vocab)}

    out = io.BytesIO()
    out.write(MAGIC)
    out.write(struct.pack(">B", VERSION))
    out.write(struct.pack(">i", len(vocab)))
    for word in vocab:
        encoded = word.encode("utf-8")
        out.write(struct.pack(">H", len(encoded)))
        out.write(encoded)

    def write_section(entries):
        """entries: sorted [(context_key, [(succ_word, weight)])]; the
        context key is written by `pack_ctx`."""
        out.write(struct.pack(">i", len(entries)))
        for ctx_key, _ in entries:
            out.write(ctx_key)
        offset = 0
        out.write(struct.pack(">i", offset))
        for _, succs in entries:
            offset += len(succs)
            out.write(struct.pack(">i", offset))
        for _, succs in entries:
            for w, _ in succs:
                out.write(struct.pack(">i", word_id[w]))
        for _, succs in entries:
            for _, weight in succs:
                out.write(struct.pack(">B", weight))

    bigram_entries = sorted(
        (struct.pack(">i", word_id[ctx]), succs)
        for ctx, succs in bigrams.items()
    )
    write_section(bigram_entries)

    trigram_entries = sorted(
        (struct.pack(">q", (word_id[w1] << 32) | word_id[w2]), succs)
        for (w1, w2), succs in trigrams.items()
    )
    write_section(trigram_entries)

    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb", compresslevel=9, mtime=0) as gz:
        gz.write(out.getvalue())
    return buf.getvalue(), len(vocab), len(out.getvalue())


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[1])
    parser.add_argument("corpus")
    parser.add_argument("out", nargs="?", default=DEFAULT_OUT)
    parser.add_argument("--test-fixture", action="store_true",
                        help="tiny-corpus mode: no pruning thresholds")
    parser.add_argument("--bigram-min-context", type=int, default=4)
    parser.add_argument("--bigram-min-succ", type=int, default=3)
    parser.add_argument("--k-bigram", type=int, default=8)
    parser.add_argument("--trigram-min-succ", type=int, default=3)
    parser.add_argument("--k-trigram", type=int, default=6)
    args = parser.parse_args()
    if args.test_fixture:
        args.bigram_min_context = 1
        args.bigram_min_succ = 1
        args.trigram_min_succ = 1
        min_word_freq = 1
    else:
        min_word_freq = MIN_WORD_FREQ

    # Pass 1: vocabulary.
    unigrams = Counter()
    for run in word_runs(args.corpus):
        unigrams.update(run)
    vocab = {w for w, c in unigrams.items() if c >= min_word_freq}
    print(f"pass 1: {len(unigrams):,} unique words, kept {len(vocab):,}",
          file=sys.stderr)
    del unigrams

    # Pass 2: bigrams over kept-vocab words.
    bigram_counts = {}
    for run in word_runs(args.corpus):
        for a, b in zip(run, run[1:]):
            if a in vocab and b in vocab:
                bigram_counts.setdefault(a, Counter())[b] += 1
    raw_bigram_contexts = len(bigram_counts)
    bigrams = prune(bigram_counts, args.bigram_min_succ, args.k_bigram,
                    args.bigram_min_context)
    del bigram_counts
    print(f"pass 2: {raw_bigram_contexts:,} bigram contexts, "
          f"kept {len(bigrams):,} "
          f"({sum(len(s) for s in bigrams.values()):,} successors)",
          file=sys.stderr)

    # Pass 3: trigrams gated on surviving bigram contexts.
    kept_pairs = {
        (ctx, w) for ctx, succs in bigrams.items() for w, _ in succs
    }
    trigram_counts = {}
    for run in word_runs(args.corpus):
        for a, b, c in zip(run, run[1:], run[2:]):
            if (a, b) in kept_pairs and c in vocab:
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
    print(f"pass 3: {raw_trigram_contexts:,} trigram contexts, "
          f"kept {len(trigrams):,} "
          f"({sum(len(s) for s in trigrams.values()):,} successors)",
          file=sys.stderr)

    data, vocab_count, raw_size = build_model(bigrams, trigrams)
    with open(args.out, "wb") as f:
        f.write(data)
    print(f"wrote {args.out}")
    print(f"  vocab: {vocab_count:,}")
    print(f"  raw: {raw_size:,} bytes, gzip: {len(data):,} bytes")


if __name__ == "__main__":
    main()
