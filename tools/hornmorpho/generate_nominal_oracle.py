#!/usr/bin/env python3

import argparse
import contextlib
import importlib
import json
from pathlib import Path
import sys

FOLD = {}


def fold_series(variants, canonical):
    FOLD.update(zip(variants, canonical))


fold_series("ሐሑሒሓሔሕሖ", "ሀሁሂሀሄህሆ")
fold_series("ኀኁኂኃኄኅኆ", "ሀሁሂሀሄህሆ")
FOLD["ሃ"] = "ሀ"
FOLD["ሗ"] = "ኋ"
fold_series("ሠሡሢሣሤሥሦሧ", "ሰሱሲሳሴስሶሷ")
fold_series("ዐዑዒዓዔዕዖ", "አኡኢአኤእኦ")
FOLD["ኣ"] = "አ"
fold_series("ፀፁፂፃፄፅፆ", "ጸጹጺጻጼጽጾ")


def normalize(value):
    return "".join(FOLD.get(character, character) for character in value)


def load_hornmorpho():
    try:
        with contextlib.redirect_stdout(sys.stderr):
            hm = importlib.import_module("hm")
    except Exception as error:
        raise RuntimeError(
            "HornMorpho 5.3.6 is unavailable; run bootstrap_oracle.py with a Python 3.9+ interpreter that includes tkinter"
        ) from error
    if hm.__version__ != "5.3.6":
        raise RuntimeError(f"expected HornMorpho 5.3.6, found {hm.__version__}")
    return hm


def stable(value):
    if isinstance(value, dict):
        return {key: stable(value[key]) for key in sorted(value)}
    if isinstance(value, (list, tuple)):
        return [stable(item) for item in value]
    if value is None or isinstance(value, (str, int, float, bool)):
        return value
    return str(value)


def analyze(hm, request):
    surface = request["surface"]
    with contextlib.redirect_stdout(sys.stderr):
        raw = hm.anal("a", surface, guess=False)
    analyses = [stable(item) for item in raw]
    analyses.sort(key=lambda item: json.dumps(item, ensure_ascii=False, sort_keys=True))
    return {
        "analyses": analyses,
        "normalized_key": normalize(surface),
        "operation": "analyze",
        "recognized": any(item.get("pos") != "UNK" for item in analyses),
        "surface": surface,
    }


def generate(hm, request):
    lemma = request["lemma"]
    features = request.get("features", [])
    if isinstance(features, str):
        feature_block = features
    else:
        feature_block = "[{}]".format(",".join(features))
    with contextlib.redirect_stdout(sys.stderr):
        generated = hm.gen(
            "a",
            lemma,
            pos="n",
            features=feature_block,
            guess=False,
        ) or []
    surfaces = sorted(set(generated), key=lambda value: (normalize(value), value))
    return {
        "features": feature_block,
        "lemma": lemma,
        "operation": "generate",
        "surfaces": [
            {"normalized_key": normalize(surface), "surface": surface}
            for surface in surfaces
        ],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", default="-")
    parser.add_argument("--output", default="-")
    args = parser.parse_args()
    hm = load_hornmorpho()
    source = sys.stdin if args.input == "-" else open(args.input, encoding="utf-8")
    output = sys.stdout if args.output == "-" else open(args.output, "w", encoding="utf-8", newline="\n")
    try:
        for line_number, line in enumerate(source, start=1):
            if not line.strip():
                continue
            request = json.loads(line)
            operation = request.get("operation")
            if operation == "analyze":
                result = analyze(hm, request)
            elif operation == "generate":
                result = generate(hm, request)
            else:
                raise ValueError(f"line {line_number}: unsupported operation {operation!r}")
            if "id" in request:
                result["id"] = request["id"]
            output.write(json.dumps(result, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n")
    finally:
        if source is not sys.stdin:
            source.close()
        if output is not sys.stdout:
            output.close()


if __name__ == "__main__":
    main()
