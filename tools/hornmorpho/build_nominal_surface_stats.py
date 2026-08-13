#!/usr/bin/env python3

import argparse
import csv
import gzip
import io
from pathlib import Path

from generate_nominal_oracle import normalize

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CORPUS = ROOT / "archive/legacy-amharic-dictionary/corpus_surface_words.dat"
DEFAULT_OUTPUT = ROOT / "language/amharic/src/dictionary/amharic_surface_stats.dat"
ORACLE_FILES = (
    ROOT / "language/amharic/src/test/resources/hornmorpho_nominal_golden.tsv",
    ROOT / "language/amharic/src/test/resources/hornmorpho_nominal_phase4.tsv",
)
MIN_FREQUENCY = 2
MAX_ROWS = 2_048


def supported(row):
    if "classification" in row:
        return row["classification"] == "supported" and row["oracle_recognized"] == "true"
    return row["should_suggest"] == "true" and row["oracle_recognized"] == "true"


def valid_inflected_keys():
    keys = set()
    for path in ORACLE_FILES:
        with path.open(encoding="utf-8", newline="") as source:
            for row in csv.DictReader(source, delimiter="\t"):
                key = normalize(row["surface"])
                if supported(row) and key != normalize(row["lemma"]):
                    keys.add(key)
    return keys


def corpus_frequencies(path):
    frequencies = {}
    with gzip.open(path, "rt", encoding="utf-8") as source:
        for line in source:
            surface, separator, raw_frequency = line.rstrip("\n").rpartition("\t")
            if not separator:
                continue
            try:
                frequency = int(raw_frequency)
            except ValueError:
                continue
            key = normalize(surface)
            frequencies[key] = frequencies.get(key, 0) + frequency
    return frequencies


def gzip_bytes(value):
    output = io.BytesIO()
    with gzip.GzipFile(fileobj=output, mode="wb", mtime=0) as archive:
        archive.write(value)
    return output.getvalue()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--corpus", type=Path, default=DEFAULT_CORPUS)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    valid = valid_inflected_keys()
    frequencies = corpus_frequencies(args.corpus)
    rows = sorted(
        (
            (key, frequencies[key])
            for key in valid
            if frequencies.get(key, 0) >= MIN_FREQUENCY
        ),
        key=lambda row: row[0],
    )
    if len(rows) > MAX_ROWS:
        selected = sorted(rows, key=lambda row: (-row[1], row[0]))[:MAX_ROWS]
        rows = sorted(selected, key=lambda row: row[0])

    keys = {key for key, _ in rows}
    assert normalize("የሰው") in keys
    assert normalize("ሰውን") in keys
    assert normalize("ቤቶች") in keys
    assert normalize("ሃሃሃ") not in keys
    text = "".join(f"{key}\t{frequency}\n" for key, frequency in rows)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(gzip_bytes(text.encode("utf-8")))
    print(f"wrote {len(rows)} analyzer-valid surface statistics to {args.output}")


if __name__ == "__main__":
    main()
