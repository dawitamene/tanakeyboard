#!/usr/bin/env python3

import argparse
import csv
import json


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--golden", required=True)
    parser.add_argument("--actual", required=True)
    args = parser.parse_args()
    with open(args.golden, encoding="utf-8", newline="") as source:
        expected = {row["id"]: row["should_suggest"] == "true" for row in csv.DictReader(source, delimiter="\t")}
    actual = {}
    with open(args.actual, encoding="utf-8") as source:
        for line in source:
            if line.strip():
                row = json.loads(line)
                actual[row["id"]] = bool(row["suggested"])
    missing = sorted(set(expected) - set(actual))
    unexpected = sorted(set(actual) - set(expected))
    mismatches = sorted(key for key in expected.keys() & actual.keys() if expected[key] != actual[key])
    if missing or unexpected or mismatches:
        raise SystemExit(
            f"nominal comparison failed: missing={missing}, unexpected={unexpected}, mismatches={mismatches}"
        )
    print(f"matched {len(expected)} nominal oracle cases")


if __name__ == "__main__":
    main()
