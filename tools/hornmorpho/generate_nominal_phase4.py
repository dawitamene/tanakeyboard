#!/usr/bin/env python3

import argparse
import csv
from pathlib import Path

from generate_nominal_golden import feature_flag, feature_values, load_selected, matching_analyses
from generate_nominal_oracle import load_hornmorpho, normalize

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUTPUT = ROOT / "language/amharic/src/test/resources/hornmorpho_nominal_phase4.tsv"
CONTRACTED = {"የ": "ያ", "ለ": "ላ", "በ": "ባ", "ከ": "ካ"}


def record(case_id, lexeme, surface, classification, analyses, reason):
    return {
        "id": case_id,
        "kind": str(lexeme["kind"]),
        "raw_form": lexeme["raw"],
        "lemma": lexeme["lemma"],
        "source_features": lexeme["features"],
        "surface": surface,
        "normalized_key": normalize(surface),
        "classification": classification,
        "oracle_recognized": str(bool(analyses)).lower(),
        "oracle_analysis_count": str(len(analyses)),
        "reason": reason,
    }


def supported_inventory(lexeme):
    lemma = lexeme["lemma"]
    forms = [
        (lemma, "base"),
        (lemma + "ን", "accusative"),
        (lemma + "ኑ", "accepted alternate accusative"),
        (lemma + "ም", "conjunctive m"),
        (lemma + "ስ", "adverbial s"),
        (lemma + "ማ", "adverbial ma"),
        (lemma + "ሳ", "adverbial sa"),
        (lemma + "ና", "conjunctive na"),
        (lemma + "ኮ", "adverbial ko"),
        (lemma + "ጋ", "postposition ga"),
        (lemma + "ንም", "accusative then conjunctive"),
        (lemma + "ንጋ", "accusative then postposition"),
    ]
    for prefix in ("የ", "ለ", "በ", "ከ", "እንደ", "ስለ", "ወደ", "እስከ"):
        forms.append((prefix + lemma, f"adposition {prefix}"))
    forms.extend([
        ("እየ" + lemma, "distributive"),
        ("በየ" + lemma, "adposition plus distributive"),
        ("እነ" + lemma, "collective"),
        ("የእነ" + lemma, "genitive plus collective"),
    ])
    if lemma.startswith("አ"):
        for prefix, contracted in CONTRACTED.items():
            forms.append((contracted + lemma[1:], f"contracted initial vowel after {prefix}"))
    return forms


def rich_inventory(lexemes):
    by_lemma = {lexeme["lemma"]: lexeme for lexeme in lexemes}
    return [
        (by_lemma["ቤት"], surface, reason)
        for surface, reason in [
            ("ቤቶች", "canonical ordinary plural"),
            ("ቤተዎች", "accepted plural boundary variant"),
            ("ቤቱ", "masculine determiner or third-person possessive"),
            ("ቤቷ", "canonical feminine determiner or possessive"),
            ("ቤትዋ", "accepted feminine boundary variant"),
            ("ቤቲቱ", "feminine itu determiner"),
            ("ቤቴ", "first-person singular possessive"),
            ("ቤታችን", "first-person plural possessive"),
            ("ቤትህ", "second masculine singular possessive"),
            ("ቤትሽ", "second feminine singular possessive"),
            ("ቤታችሁ", "second plural possessive"),
            ("ቤትዎ", "formal second-person possessive"),
            ("ቤታቸው", "third-person plural possessive"),
            ("የቤቶቹን", "prefix plural determiner accusative"),
            ("ቤታችንንም", "possessive accusative conjunctive"),
        ]
    ] + [
        (by_lemma["ሰው"], surface, reason)
        for surface, reason in [
            ("ሰውየ", "human suffix masculine"),
            ("ሰውዬ", "human suffix alternate"),
            ("ሰውዮ", "human suffix feminine"),
            ("ሰውየው", "human suffix with third-masculine possessive"),
            ("እነሰው", "collective human"),
            ("የእነሰው", "genitive collective human"),
            ("በየሰው", "adposition distributive human"),
        ]
    ] + [
        (by_lemma["ቦታ"], surface, reason)
        for surface, reason in [
            ("ቦታዎች", "canonical vowel-final plural"),
            ("ቦቶች", "accepted fused plural boundary"),
            ("ቦታው", "vowel-final masculine determiner"),
            ("ቦታዋ", "vowel-final feminine determiner"),
            ("ቦታዬ", "vowel-final first-person possessive"),
            ("ቦታያችን", "vowel-final plural-person possessive"),
        ]
    ] + [
        (by_lemma["መምህር"], "መምህራን", "alternate an plural"),
    ]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    hm = load_hornmorpho()
    cache = {}
    lexemes = load_selected()
    rows = []
    supported_index = 0
    exclusion_index = 0
    for lexeme in lexemes:
        seen = set()
        for surface, reason in supported_inventory(lexeme):
            if surface in seen:
                continue
            seen.add(surface)
            analyses = matching_analyses(hm, lexeme, surface, cache)
            if analyses:
                collective_mismatch = "collective" in reason and (
                    lexeme["kind"] == 3 or feature_flag(lexeme["features"], "-h")
                )
                allowed_adpositions = feature_values(lexeme["features"], "adp")
                genitive_mismatch = reason == "adposition የ" and (
                    feature_flag(lexeme["features"], "-gen") or
                    (allowed_adpositions is not None and "የ" not in allowed_adpositions)
                )
                if collective_mismatch or genitive_mismatch:
                    mismatch_reason = (
                        "excluded analyzer homograph from another lexical analysis"
                        if lexeme["kind"] == 3 else
                        "excluded surface conflicts with this source lexeme's restrictions"
                    )
                    rows.append(record(
                        f"e{exclusion_index:03d}", lexeme, surface, "excluded", analyses, mismatch_reason,
                    ))
                    exclusion_index += 1
                else:
                    rows.append(record(f"s{supported_index:04d}", lexeme, surface, "supported", analyses, reason))
                    supported_index += 1
    for lexeme, surface, reason in rich_inventory(lexemes):
        analyses = matching_analyses(hm, lexeme, surface, cache)
        if analyses and not any(row["kind"] == str(lexeme["kind"]) and row["raw_form"] == lexeme["raw"] and row["surface"] == surface for row in rows):
            rows.append(record(f"s{supported_index:04d}", lexeme, surface, "supported", analyses, reason))
            supported_index += 1

    exclusions = [
        ("ቤት", "ቤተዋ", "excluded epenthetic feminine analyzer variant"),
        ("ቤት", "ቤተም", "excluded epenthetic conjunctive analyzer variant"),
        ("ቤት", "ቤተጋ", "excluded epenthetic postposition analyzer variant"),
        ("ሰው", "ሰውእየ", "excluded raw human boundary variant"),
        ("ሰው", "ሰውእዬ", "excluded raw human boundary variant"),
        ("ሰው", "ሰውእዮ", "excluded raw human boundary variant"),
    ]
    by_lemma = {lexeme["lemma"]: lexeme for lexeme in lexemes}
    for lemma, surface, reason in exclusions:
        lexeme = by_lemma[lemma]
        analyses = matching_analyses(hm, lexeme, surface, cache)
        if analyses:
            rows.append(record(f"e{exclusion_index:03d}", lexeme, surface, "excluded", analyses, reason))
            exclusion_index += 1

    negative_index = 0
    for lexeme in lexemes:
        for surface, reason in [
            (lexeme["lemma"] + "ንው", "invalid accusative before determiner"),
            (lexeme["lemma"] + "ጋም", "invalid postposition before conjunctive"),
            ("የየ" + lexeme["lemma"], "duplicated genitive marker"),
        ]:
            analyses = matching_analyses(hm, lexeme, surface, cache)
            if not analyses:
                rows.append(record(f"n{negative_index:04d}", lexeme, surface, "negative", analyses, reason))
                negative_index += 1

    supported = sum(row["classification"] == "supported" for row in rows)
    excluded = sum(row["classification"] == "excluded" for row in rows)
    negative = sum(row["classification"] == "negative" for row in rows)
    if supported < 300 or negative < 90 or excluded < 3:
        raise RuntimeError(f"phase 4 corpus too small: supported={supported}, excluded={excluded}, negative={negative}")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with open(args.output, "w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=list(rows[0]), delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    print(f"wrote {len(rows)} cases: {supported} supported, {excluded} excluded, {negative} negative")


if __name__ == "__main__":
    main()
