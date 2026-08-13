#!/usr/bin/env python3
"""
Regenerates language/amharic/src/dictionary/amharic_ngrams.dat, the sparse
Amharic surface n-gram model loaded into the generated SQLite database.

Source: one or more explicitly selected, documented Amharic corpora, one
sentence-ish line each; counts from all corpora are summed. Pre-tokenized text
(punctuation as its own token) and raw text (punctuation attached to words)
both work. The checked-in Phase 6 baseline uses only the project-authored mini
corpus documented in language/amharic/hornmorpho/PHASE6_PREDICTION.md. Local
corpora with unclear redistribution or acquisition provenance are not used.

Tokenization mirrors build_amharic_dict.py: NFC-normalize, strip the
combining gemination marks U+135D-U+135F. A whitespace token fully made of
Ethiopic syllables (WORD_RE) is a word. A token with leading/trailing
punctuation around a syllabic core ("በቴሌግራም።", "«ሰላም»") contributes the core
as a word plus an n-gram BOUNDARY on the punctuation side(s). Everything else
-- standalone punctuation, digits, Latin, mixed abbreviations like "ዓ.ም" or
"በ2007" -- is wholly a boundary: word runs split there and at line ends, so
no bigram or trigram ever spans punctuation or a number.

FOLDING + MORPHOLOGY GATING: every word is homoglyph-folded and accepted only
when it is an exact base lexeme or a surface recognized by the checked-in,
pinned HornMorpho oracle fixtures and permitted by the runtime subset. Exact
lexemes use the dictionary display; productive surfaces retain their canonical
surface display plus lemma/analysis, ambiguity, and Phase 5 frequency metadata
in amharic_ngram_audit.tsv. Variant spellings pool evidence without destroying
display. An unknown word is skipped and breaks pair adjacency, so no n-gram is
created across it. Regenerate the dictionary and oracle fixtures first.

Counting is at most two passes per corpus:
  1. bigram counts over accepted surfaces; prune to contexts with total count
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
           are unique -- one admitted display surface per key); word id =
           index
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

Version history: v2 = folded-key vocab order + gated display forms;
v3 = v2 plus per-successor casing flags (English proper-noun casing).
The build logic parses v2 and v3 and rejects v1 assets loudly.

Phase 6 baseline:
  python3 tools/build_ngrams.py --bigram-min-context 1 --bigram-min-succ 1 \
      --k-bigram 8 --no-trigrams \
      --held-out tools/hornmorpho/fixtures/amharic_phase6_held_out.txt \
      tools/hornmorpho/fixtures/amharic_phase6_corpus.txt

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
          apps/textrevamp/src/test/resources/ngram_mini_corpus.txt \
          apps/textrevamp/src/test/resources/ngram_fixture.dat
"""

import argparse
import csv
import gzip
import io
import json
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
DEFAULT_OUT = os.path.join(REPO, "language", "amharic", "src", "dictionary", "amharic_ngrams.dat")
DICT_ASSET = os.path.join(REPO, "language", "amharic", "src", "dictionary", "amharic_words.dat")
LEXEME_ASSET = os.path.join(REPO, "language", "amharic", "src", "dictionary", "amharic_lexemes.dat")
SURFACE_STATS_ASSET = os.path.join(REPO, "language", "amharic", "src", "dictionary", "amharic_surface_stats.dat")
DEFAULT_AUDIT_OUT = os.path.join(REPO, "language", "amharic", "src", "dictionary", "amharic_ngram_audit.tsv")
DEFAULT_QUALITY_OUT = os.path.join(REPO, "language", "amharic", "src", "dictionary", "amharic_ngram_quality.json")
DEFAULT_REVIEW_OUT = os.path.join(REPO, "language", "amharic", "src", "dictionary", "amharic_ngram_review.tsv")
ORACLE_FIXTURES = (
    os.path.join(REPO, "language", "amharic", "src", "test", "resources", "hornmorpho_nominal_golden.tsv"),
    os.path.join(REPO, "language", "amharic", "src", "test", "resources", "hornmorpho_nominal_phase4.tsv"),
)

# English uses per-char lowercase as its fold -- the analogue of the Amharic
# homoglyph fold, and identical to WordDictionary's default Char::lowercaseChar
# keying and build_english_dict.py's sort key (so the vocab order the Kotlin
# side binary-searches agrees). A word is letters with optional internal
# apostrophes ("don't", "o'clock"); dictionary entries are already clean.
ENGLISH_OUT = os.path.join(REPO, "language", "english", "src", "dictionary", "english_ngrams.dat")
ENGLISH_DICT_ASSET = os.path.join(REPO, "language", "english", "src", "dictionary", "english_words.dat")
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


def load_lexeme_evidence(asset=LEXEME_ASSET):
    evidence = {}
    with gzip.open(asset, "rt", encoding="utf-8") as source:
        for line in source:
            fields = line.rstrip("\n").split("\t", 2)
            if len(fields) != 3:
                continue
            surface = GEMINATION_RE.sub("", fields[1].replace("/", ""))
            if WORD_RE.match(surface):
                evidence.setdefault(fold(surface), set()).add(tuple(fields))
    return evidence


def load_surface_frequencies(asset=SURFACE_STATS_ASSET):
    frequencies = {}
    with gzip.open(asset, "rt", encoding="utf-8") as source:
        for line in source:
            surface, separator, raw_frequency = line.rstrip("\n").rpartition("\t")
            if separator and raw_frequency.isdigit():
                frequencies[fold(surface)] = int(raw_frequency)
    return frequencies


def load_oracle_surfaces(paths=ORACLE_FIXTURES):
    evidence = {}
    for path in paths:
        with open(path, encoding="utf-8", newline="") as source:
            for row in csv.DictReader(source, delimiter="\t"):
                supported = (
                    row.get("classification") == "supported"
                    if "classification" in row
                    else row.get("should_suggest") == "true"
                )
                if not supported or row.get("oracle_recognized") != "true":
                    continue
                key = fold(row["surface"])
                item = evidence.setdefault(key, {
                    "surfaces": set(),
                    "lemmas": set(),
                    "analyses": set(),
                    "analysis_count": 0,
                })
                item["surfaces"].add(row["surface"])
                if row.get("lemma"):
                    item["lemmas"].add(GEMINATION_RE.sub("", row["lemma"].replace("/", "")))
                item["analyses"].add(
                    (
                        row.get("kind", ""),
                        row.get("raw_form", ""),
                        row.get("source_features", ""),
                    )
                )
                item["analysis_count"] = max(
                    item["analysis_count"],
                    int(row.get("oracle_analysis_count") or 0),
                )
    return {
        key: {
            "display": sorted(item["surfaces"], key=lambda value: (fold(value), value))[0],
            "preferred_lemma": next(iter(item["lemmas"])) if len(item["lemmas"]) == 1 else "",
            "ambiguity_count": max(1, item["analysis_count"]),
            "preferred_analysis": (
                analysis_text(next(iter(item["analyses"])))
                if item["analysis_count"] == 1 and len(item["analyses"]) == 1
                else ""
            ),
        }
        for key, item in evidence.items()
    }


def analysis_text(analysis):
    kind, form, features = analysis
    return f"kind={kind};form={form};features={features}"


class AmharicTokenGate:
    def __init__(self):
        self.base = load_display_map()
        self.lexeme_evidence = load_lexeme_evidence()
        self.oracle = load_oracle_surfaces()
        self.surface_frequencies = load_surface_frequencies()
        self.metadata = {}
        self.rejected = set()

    def canonical(self, token):
        key = fold(token)
        display = self.base.get(key)
        if display is not None:
            evidence = self.lexeme_evidence.get(key, set())
            ambiguity_count = max(1, len(evidence))
            self.metadata.setdefault(display, {
                "normalized_key": key,
                "validity_source": "exact_lexeme",
                "preferred_lemma": display if ambiguity_count == 1 else "",
                "preferred_analysis": (
                    analysis_text(next(iter(evidence))) if len(evidence) == 1 else ""
                ),
                "ambiguity_count": ambiguity_count,
                "surface_frequency": self.surface_frequencies.get(key, 0),
            })
            return display
        oracle = self.oracle.get(key)
        if oracle is None:
            self.rejected.add(key)
            return None
        display = oracle["display"]
        self.metadata.setdefault(display, {
            "normalized_key": key,
            "validity_source": "oracle_surface",
            "preferred_lemma": (
                oracle["preferred_lemma"] if oracle["ambiguity_count"] == 1 else ""
            ),
            "preferred_analysis": oracle["preferred_analysis"],
            "ambiguity_count": oracle["ambiguity_count"],
            "surface_frequency": self.surface_frequencies.get(key, 0),
        })
        return display

    def write_audit(self, path, vocab):
        with open(path, "w", encoding="utf-8", newline="\n") as output:
            output.write(
                "display\tnormalized_key\tvalidity_source\tpreferred_lemma\t"
                "preferred_analysis\tambiguity_count\tsurface_frequency\n"
            )
            for display in sorted(vocab, key=fold):
                item = self.metadata[display]
                output.write(
                    f"{display}\t{item['normalized_key']}\t{item['validity_source']}\t"
                    f"{item['preferred_lemma']}\t{item['preferred_analysis']}\t"
                    f"{item['ambiguity_count']}\t"
                    f"{item['surface_frequency']}\n"
                )


def word_runs(path, canonical):
    """Yields lists over each boundary-free stretch of the corpus, one entry
    per word: the word's canonical form per `canonical(normalized token)`, or
    None for a word the validity gate rejects (it blocks pair adjacency
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
        top = sorted(
            ((word, count) for word, count in succs.items() if count >= min_succ),
            key=lambda item: (-item[1], fold(item[0])),
        )[:top_k]
        if top:
            kept[ctx] = sorted(
                ((word, quantize(count)) for word, count in top),
                key=lambda item: (-item[1], fold(item[0])),
            )
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
    return buf.getvalue(), len(vocab), len(out.getvalue()), vocab


def evaluate_top3(paths, canonical, bigrams, trigrams):
    hits = 0
    total = 0
    for path in paths:
        for run in word_runs(path, canonical):
            for index in range(1, len(run)):
                previous = run[index - 1]
                expected = run[index]
                if previous is None or expected is None:
                    continue
                candidates = []
                if index >= 2 and run[index - 2] is not None:
                    candidates.extend(
                        word for word, _ in trigrams.get((run[index - 2], previous), [])
                    )
                candidates.extend(word for word, _ in bigrams.get(previous, []))
                top3 = list(dict.fromkeys(candidates))[:3]
                total += 1
                if expected in top3:
                    hits += 1
    return {
        "baseline_top3_hits": 0,
        "evaluated_transitions": total,
        "model_top3_hits": hits,
        "model_top3_accuracy": round(hits / total, 6) if total else 0.0,
    }


def write_review(path, bigrams, trigrams):
    with open(path, "w", encoding="utf-8", newline="\n") as output:
        output.write("order\tcontext\trank\tsuccessor\tweight\n")
        for context in sorted(bigrams, key=fold):
            for rank, (successor, weight) in enumerate(bigrams[context], start=1):
                output.write(f"bigram\t{context}\t{rank}\t{successor}\t{weight}\n")
        for context in sorted(trigrams, key=lambda value: (fold(value[0]), fold(value[1]))):
            for rank, (successor, weight) in enumerate(trigrams[context], start=1):
                output.write(
                    f"trigram\t{context[0]} {context[1]}\t{rank}\t{successor}\t{weight}\n"
                )


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

    data, vocab_count, raw_size, _ = build_model(
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
    parser.add_argument("--bigram-min-context", type=int, default=10)
    parser.add_argument("--bigram-min-succ", type=int, default=8)
    parser.add_argument("--k-bigram", type=int, default=8)
    parser.add_argument("--trigram-min-succ", type=int, default=8)
    parser.add_argument("--k-trigram", type=int, default=6)
    parser.add_argument("--no-trigrams", action="store_true",
                        help="emit bigrams only until trigram quality is reviewed")
    parser.add_argument("--audit-out",
                        help="Amharic vocabulary validity audit TSV")
    parser.add_argument("--held-out", action="append", default=[],
                        help="held-out Amharic corpus used for top-3 evaluation")
    parser.add_argument("--quality-out",
                        help="Amharic held-out quality JSON")
    parser.add_argument("--review-out",
                        help="Amharic fluent-review context TSV")
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
        gate = AmharicTokenGate()
        print(
            f"gate: {len(gate.base):,} exact lexemes + "
            f"{len(gate.oracle):,} pinned-oracle surfaces",
            file=sys.stderr,
        )
        canonical = gate.canonical

    # Pass 1: bigrams over admitted surfaces.
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
    kept_pairs = {(ctx, w) for ctx, succs in bigrams.items() for w, _ in succs}
    trigram_counts = {}
    if not args.no_trigrams:
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

    data, vocab_count, raw_size, vocab = build_model(bigrams, trigrams)
    with open(out, "wb") as f:
        f.write(data)
    if not args.test_fixture:
        audit_out = args.audit_out or DEFAULT_AUDIT_OUT
        gate.write_audit(audit_out, vocab)
        review_out = args.review_out or DEFAULT_REVIEW_OUT
        write_review(review_out, bigrams, trigrams)
        print(
            f"gate: rejected {len(gate.rejected):,} unknown normalized tokens; "
            f"audit {audit_out}; review {review_out}",
            file=sys.stderr,
        )
        if args.held_out:
            quality = evaluate_top3(args.held_out, canonical, bigrams, trigrams)
            quality_out = args.quality_out or DEFAULT_QUALITY_OUT
            with open(quality_out, "w", encoding="utf-8", newline="\n") as output:
                json.dump(quality, output, sort_keys=True, separators=(",", ":"))
                output.write("\n")
            print(f"quality: {quality} -> {quality_out}", file=sys.stderr)
    print(f"wrote {out}")
    print(f"  vocab: {vocab_count:,}")
    print(f"  raw: {raw_size:,} bytes, gzip: {len(data):,} bytes")


if __name__ == "__main__":
    main()
