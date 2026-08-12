#!/usr/bin/env python3
"""Build the Amharic baseline from HornMorpho's lexicon.

The runtime word list contains only displayable HornMorpho stems, names,
places, and analyzed lemma-frequency entries. HornMorpho verb roots and their
classes are retained separately in amharic_lexemes.dat for the morphology
generator; they are not guessed into surface spellings here.
"""

import gzip
import io
import os
import re
import struct
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, ".."))
HORN = os.path.join(REPO, "language", "amharic", "hornmorpho")
DICTIONARY = os.path.join(REPO, "language", "amharic", "src", "dictionary")
WORDS_OUT = os.path.join(DICTIONARY, "amharic_words.dat")
LEXEMES_OUT = os.path.join(DICTIONARY, "amharic_lexemes.dat")
NGRAMS_OUT = os.path.join(DICTIONARY, "amharic_ngrams.dat")

SURFACE_SOURCES = (
    (0, os.path.join(HORN, "lex", "n_stem.lex")),
    (1, os.path.join(HORN, "lex", "n_stem_an.lex")),
    (2, os.path.join(HORN, "lex", "n_name.lex")),
    (3, os.path.join(HORN, "lex", "n_place.lex")),
)
VERB_ROOT_SOURCE = os.path.join(HORN, "lex", "vroot.lex")
ROOT_FREQUENCIES = os.path.join(HORN, "stat", "root.frq")

SYLLABLE = r"ሀ-ፚᎀ-ᎏⶀ-ⷞꬁ-ꬮ"
WORD_RE = re.compile(rf"^[{SYLLABLE}]+$")
GEMINATION_RE = re.compile(r"[፝-፟]")
VERB_ROOT_RE = re.compile(r"^<([^>]+)>\s*(.*)$")

FOLD = {}


def _fold_series(variants, canonical):
    assert len(variants) == len(canonical)
    FOLD.update(zip(variants, canonical))


_fold_series("ሐሑሒሓሔሕሖ", "ሀሁሂሀሄህሆ")
_fold_series("ኀኁኂኃኄኅኆ", "ሀሁሂሀሄህሆ")
FOLD["ሃ"] = "ሀ"
FOLD["ሗ"] = "ኋ"
_fold_series("ሠሡሢሣሤሥሦሧ", "ሰሱሲሳሴስሶሷ")
_fold_series("ዐዑዒዓዔዕዖ", "አኡኢአኤእኦ")
FOLD["ኣ"] = "አ"
_fold_series("ፀፁፂፃፄፅፆ", "ጸጹጺጻጼጽጾ")


def fold(token):
    return "".join(FOLD.get(character, character) for character in token)


def clean_surface(raw):
    value = GEMINATION_RE.sub("", unicodedata.normalize("NFC", raw))
    value = value.replace("/", "")
    return value if WORD_RE.fullmatch(value) else None


def surface_lexemes():
    for kind, path in SURFACE_SOURCES:
        with open(path, encoding="utf-8") as source:
            for line in source:
                if not line.strip() or line.lstrip().startswith("#"):
                    continue
                if line[0].isspace():
                    continue
                fields = line.rstrip("\n").split("\t", 1)
                raw = fields[0].strip()
                features = " ".join(fields[1].split()) if len(fields) == 2 else ""
                yield kind, raw, features, clean_surface(raw)


def verb_root_lexemes():
    with open(VERB_ROOT_SOURCE, encoding="utf-8") as source:
        for line in source:
            match = VERB_ROOT_RE.match(line)
            if match is None:
                continue
            root = "".join(match.group(1).split())
            features = " ".join(match.group(2).split())
            yield 4, root, features


def root_frequency_rows():
    with open(ROOT_FREQUENCIES, encoding="utf-8") as source:
        for line in source:
            fields = line.rstrip("\n").split("\t")
            if len(fields) != 2:
                continue
            raw, count = fields
            try:
                frequency = int(count)
            except ValueError:
                continue
            surface = raw
            if ":" in raw:
                surface, analysis = raw.split(":", 1)
                if analysis not in {"PRS", "PST"}:
                    continue
            surface = clean_surface(surface)
            if surface is not None and frequency > 0:
                yield surface, frequency


def gzip_bytes(data):
    output = io.BytesIO()
    with gzip.GzipFile(fileobj=output, mode="wb", mtime=0) as compressed:
        compressed.write(data)
    return output.getvalue()


def write_if_changed(path, data):
    if os.path.exists(path):
        with open(path, "rb") as existing:
            if existing.read() == data:
                return False
    with open(path, "wb") as output:
        output.write(data)
    return True


def empty_ngram_model():
    data = io.BytesIO()
    data.write(b"ANGM")
    data.write(struct.pack(">B", 2))
    data.write(struct.pack(">i", 0))
    data.write(struct.pack(">i", 0))
    data.write(struct.pack(">i", 0))
    data.write(struct.pack(">i", 0))
    data.write(struct.pack(">i", 0))
    return gzip_bytes(data.getvalue())


def main():
    candidates = {}
    lexemes = []

    for kind, raw, features, surface in surface_lexemes():
        lexemes.append((kind, raw, features))
        if surface is not None:
            priority = 2 if kind <= 1 else 1
            variants = candidates.setdefault(fold(surface), {})
            variants[surface] = max(priority, variants.get(surface, 0))

    lexemes.extend(verb_root_lexemes())

    folded_frequencies = {}
    surface_frequencies = {}
    for surface, frequency in root_frequency_rows():
        key = fold(surface)
        folded_frequencies[key] = folded_frequencies.get(key, 0) + frequency
        surface_frequencies[surface] = surface_frequencies.get(surface, 0) + frequency
        variants = candidates.setdefault(key, {})
        variants[surface] = max(3, variants.get(surface, 0))

    rows = []
    for key, variants in candidates.items():
        display = max(
            variants,
            key=lambda value: (
                surface_frequencies.get(value, 0),
                variants[value],
                value,
            ),
        )
        rows.append((key, display, max(1, folded_frequencies.get(key, 0))))
    rows.sort(key=lambda row: row[0])

    unique_lexemes = sorted(set(lexemes))
    assert 18_000 <= len(unique_lexemes) <= 20_000, len(unique_lexemes)
    assert 10_000 <= len(rows) <= 25_000, len(rows)
    displays = {row[1] for row in rows}
    assert "ሰው" in displays
    assert "የሰው" not in displays
    assert "ሰውን" not in displays

    words = "".join(f"{display}\t{frequency}\n" for _, display, frequency in rows)
    lexeme_text = "".join(
        f"{kind}\t{form}\t{features}\n"
        for kind, form, features in unique_lexemes
    )

    os.makedirs(DICTIONARY, exist_ok=True)
    changed = []
    if write_if_changed(WORDS_OUT, gzip_bytes(words.encode("utf-8"))):
        changed.append(WORDS_OUT)
    if write_if_changed(LEXEMES_OUT, gzip_bytes(lexeme_text.encode("utf-8"))):
        changed.append(LEXEMES_OUT)
    if write_if_changed(NGRAMS_OUT, empty_ngram_model()):
        changed.append(NGRAMS_OUT)

    print(f"HornMorpho lexical records: {len(unique_lexemes):,}")
    print(f"Displayable normalized lemmas: {len(rows):,}")
    print(f"Frequency-ranked lemmas: {len(folded_frequencies):,}")
    print("Updated: " + (", ".join(changed) if changed else "nothing"))


if __name__ == "__main__":
    main()
