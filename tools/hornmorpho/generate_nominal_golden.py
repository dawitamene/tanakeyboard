#!/usr/bin/env python3

import argparse
import contextlib
import csv
import re
import sys
from pathlib import Path

from generate_nominal_oracle import load_hornmorpho, normalize

ROOT = Path(__file__).resolve().parents[2]
LEXICON_PATHS = {
    0: ROOT / "language/amharic/hornmorpho/lex/n_stem.lex",
    1: ROOT / "language/amharic/hornmorpho/lex/n_stem_an.lex",
    2: ROOT / "language/amharic/hornmorpho/lex/n_name.lex",
    3: ROOT / "language/amharic/hornmorpho/lex/n_place.lex",
}
DEFAULT_OUTPUT = ROOT / "language/amharic/src/test/resources/hornmorpho_nominal_golden.tsv"
SELECTED = {
    0: [
        "ሰው", "ቤት", "ቦታ", "ሀገር", "ልጅ", "መኪና", "ስራ", "ሴት", "ቀን",
        "ትምህርት", "ወንበር", "አበባ", "ወንድ", "አመት", "መጽሀፍ", "ላም",
        "ሚስት", "እህት", "እ/ናት", "ጨረቃ", "ጸሀይ", "ራስ", "ውነት", "ገ/ዛ",
        "ጥ/ር", "ወይዘሪት", "ራቁት", "እራስ", "ም/ሳሌ",
    ],
    1: ["መምህር", "ክቡር", "ደራሲ", "ምሁር", "ቅ/ዱስ"],
    2: ["አ/በበ", "ሳራ", "ሀና", "ማርታ"],
    3: ["አዳማ", "ኢትዮጵያ", "ጎንደር", "ሀና"],
}
FAMILIES = [
    "ሀሁሂሃሄህሆ", "ሐሑሒሓሔሕሖ", "ለሉሊላሌልሎ", "መሙሚማሜምሞ",
    "ሰሱሲሳሴስሶ", "ሠሡሢሣሤሥሦ", "ሸሹሺሻሼሽሾ", "ረሩሪራሬርሮ",
    "ቀቁቂቃቄቅቆ", "ቐቑቒቓቔቕቖ", "በቡቢባቤብቦ", "ቨቩቪቫቬቭቮ",
    "ተቱቲታቴትቶ", "ቸቹቺቻቼችቾ", "ነኑኒናኔንኖ", "ኘኙኚኛኜኝኞ",
    "አኡኢኣኤእኦ", "ከኩኪካኬክኮ", "ኸኹኺኻኼኽኾ", "ወዉዊዋዌውዎ",
    "ዐዑዒዓዔዕዖ", "ዘዙዚዛዜዝዞ", "ዠዡዢዣዤዥዦ", "የዩዪያዬይዮ",
    "ደዱዲዳዴድዶ", "ጀጁጂጃጄጅጆ", "ገጉጊጋጌግጎ", "ጠጡጢጣጤጥጦ",
    "ጸጹጺጻጼጽጾ", "ፀፁፂፃፄፅፆ", "ጨጩጪጫጬጭጮ", "ጰጱጲጳጴጵጶ",
    "ፈፉፊፋፌፍፎ", "ፐፑፒፓፔፕፖ",
]
LABIALIZED = dict(zip(
    "ህልምስሽርቅብትችንኝክዝድጅግጥጭፍ",
    "ሗሏሟሷሿሯቋቧቷቿኗኟኳዟዷጇጓጧጯፏ",
))
ORDER = {character: index for family in FAMILIES for index, character in enumerate(family)}
FAMILY = {character: family for family in FAMILIES for character in family}
MARKERS = re.compile(r"[/፝-፟]")
PREFIXES = ["", "የ", "ለ", "በየ"]


def clean(value):
    return MARKERS.sub("", value)


def load_selected():
    result = []
    for kind, wanted in SELECTED.items():
        rows = {}
        with open(LEXICON_PATHS[kind], encoding="utf-8") as source:
            for line in source:
                if not line.strip() or line.lstrip().startswith("#") or line[0].isspace():
                    continue
                fields = line.strip().split(maxsplit=1)
                rows.setdefault(fields[0], " ".join(fields[1].split()) if len(fields) == 2 else "")
        for raw in wanted:
            if raw not in rows:
                raise RuntimeError(f"missing selected kind {kind} lexeme {raw}")
            features = rows[raw]
            alias = features.partition("[")[0].strip().split()
            oracle_lemmas = {clean(raw)}
            if alias and alias[0] != "''":
                oracle_lemmas.add(clean(alias[0]))
            result.append({
                "kind": kind,
                "raw": raw,
                "lemma": clean(raw),
                "oracle_lemmas": oracle_lemmas,
                "features": features,
            })
    return result


def feature_flag(features, token):
    return re.search(rf"(?:^|[,\[\s]){re.escape(token)}(?=[,\]\s]|$)", features) is not None


def feature_values(features, name):
    match = re.search(rf"(?:^|[,\[\s]){re.escape(name)}=([^,\]\s]+)", features)
    return set(match.group(1).split("|")) if match else None


def replace_last(surface, order):
    if not surface or surface[-1] not in FAMILY:
        return None
    return surface[:-1] + FAMILY[surface[-1]][order]


def plural(lexeme):
    if lexeme["kind"] in (2, 3) or feature_flag(lexeme["features"], "-pl"):
        return None
    stem = lexeme["lemma"]
    if lexeme["kind"] == 1:
        if stem.endswith("ዊ"):
            return stem[:-1] + "ውያን"
        return replace_last(stem, 3) + "ን" if ORDER.get(stem[-1]) == 5 else None
    if ORDER.get(stem[-1]) == 5:
        changed = replace_last(stem, 6)
        return changed + "ች" if changed else None
    return stem + "ዎች" if stem[-1] in ORDER else None


def definite(surface, feminine, is_plural):
    if feminine and not is_plural:
        if ORDER.get(surface[-1]) == 5 and surface[-1] in LABIALIZED:
            return surface[:-1] + LABIALIZED[surface[-1]]
        return surface + "ዋ"
    if surface.endswith("ው"):
        return None
    return replace_last(surface, 1) if ORDER.get(surface[-1]) == 5 else surface + "ው"


def transformed(surface, suffix):
    order = ORDER.get(surface[-1])
    if order == 5:
        return replace_last(surface, 3) + suffix
    if order in (2, 4):
        return surface + "ያ" + suffix
    if order in (1, 6):
        return surface + "አ" + suffix
    return surface + suffix


def first_person(surface):
    return replace_last(surface, 4) if ORDER.get(surface[-1]) == 5 else surface + "ዬ"


def allows_prefix(lexeme, prefix):
    if not prefix:
        return True
    adpositions = feature_values(lexeme["features"], "adp")
    if adpositions == {"0"}:
        return False
    adposition = prefix[:-1] if prefix in {"በየ", "ለየ", "ከየ"} else prefix
    allowed = None if adpositions is None else adpositions - {"0"}
    if allowed and adposition not in allowed:
        return False
    if prefix.endswith("የ") and feature_flag(lexeme["features"], "-gen"):
        return False
    if prefix in {"በየ", "ለየ", "ከየ"} and feature_flag(lexeme["features"], "-dis"):
        return False
    return True


def generated_forms(lexeme):
    feminine = feature_values(lexeme["features"], "g") == {"f"}
    person = feature_values(lexeme["features"], "p")
    number_forms = [(lexeme["lemma"], "SG")]
    plural_form = plural(lexeme)
    if plural_form:
        number_forms.append((plural_form, "PL"))
    forms = []
    for prefix in PREFIXES:
        if not allows_prefix(lexeme, prefix):
            continue
        prefix_label = f"ADP={prefix};" if prefix else ""
        for surface, number_label in number_forms:
            base = prefix + surface
            forms.extend([(base, prefix_label + number_label), (base + "ን", prefix_label + number_label + ";ACC")])
            is_plural = number_label == "PL"
            if lexeme["kind"] not in (2, 3) and not feature_flag(lexeme["features"], "-def"):
                value = definite(surface, feminine, is_plural)
                if value:
                    forms.extend([
                        (prefix + value, prefix_label + number_label + ";DEF"),
                        (prefix + value + "ን", prefix_label + number_label + ";DEF;ACC"),
                    ])
            if person != {"0"} and lexeme["kind"] not in (2, 3):
                possessives = [
                    (first_person(surface), "PSS1S"),
                    (surface + "ህ", "PSS2SM"),
                    (surface + "ሽ", "PSS2SF"),
                    (transformed(surface, "ችን"), "PSS1P"),
                    (transformed(surface, "ችሁ"), "PSS2P"),
                    (transformed(surface, "ቸው"), "PSS3P"),
                    (surface + "ዎ", "PSS2FORM"),
                ]
                for value, label in possessives:
                    if value:
                        forms.extend([
                            (prefix + value, prefix_label + number_label + ";" + label),
                            (prefix + value + "ን", prefix_label + number_label + ";" + label + ";ACC"),
                        ])
    return list(dict.fromkeys(forms))


def matching_analyses(hm, lexeme, surface, cache):
    if surface not in cache:
        with contextlib.redirect_stdout(sys.stderr):
            cache[surface] = list(hm.anal("a", surface, guess=False))
    keys = {normalize(value) for value in lexeme["oracle_lemmas"]}
    return [
        analysis for analysis in cache[surface]
        if normalize(clean(str(analysis.get("root") or analysis.get("lemma") or ""))) in keys
        and analysis.get("pos") in {"N", "ADJ", "PROPN"}
    ]


def record(case_id, lexeme, typed, surface, request, suggest, analyses, reason, source="hornmorpho"):
    return {
        "id": case_id,
        "source": source,
        "kind": str(lexeme["kind"]),
        "raw_form": lexeme["raw"],
        "lemma": lexeme["lemma"],
        "source_features": lexeme["features"],
        "requested_features": request,
        "typed": typed,
        "surface": surface,
        "normalized_key": normalize(surface),
        "should_suggest": str(suggest).lower(),
        "oracle_recognized": str(bool(analyses)).lower(),
        "oracle_analysis_count": str(len(analyses)),
        "reason": reason,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    hm = load_hornmorpho()
    cache = {}
    rows = []
    lexemes = load_selected()
    for lexeme_index, lexeme in enumerate(lexemes):
        positives = 0
        for surface, request in generated_forms(lexeme):
            analyses = matching_analyses(hm, lexeme, surface, cache)
            if not analyses:
                continue
            rows.append(record(
                f"p{lexeme_index:02d}-{positives:02d}", lexeme, surface, surface, request, True, analyses,
                "HornMorpho 5.3.6 nominal analysis matches the source lemma",
            ))
            positives += 1
            if positives == 8:
                break
        if positives < 1:
            raise RuntimeError(f"too few oracle-positive MVP forms for {lexeme}")

    negatives = 0
    for lexeme_index, lexeme in enumerate(lexemes):
        invalid = [
            (lexeme["lemma"] + "ንው", "invalid suffix order ACC then DEF"),
            (lexeme["lemma"] + "ዎችው", "invalid plural boundary then determiner"),
            (lexeme["lemma"] + "የ", "invalid trailing genitive marker"),
            ("የየ" + lexeme["lemma"], "duplicated genitive prefix"),
            (lexeme["lemma"] + "ንን", "duplicated accusative suffix"),
        ]
        for surface, reason in invalid:
            analyses = matching_analyses(hm, lexeme, surface, cache)
            if analyses:
                continue
            rows.append(record(
                f"n{lexeme_index:02d}-{negatives:03d}", lexeme, surface, surface, "INVALID", False, analyses, reason,
            ))
            negatives += 1
            if negatives == 120:
                break
        if negatives == 120:
            break

    country = next(lexeme for lexeme in lexemes if lexeme["kind"] == 0 and lexeme["raw"] == "ሀገር")
    country_analyses = matching_analyses(hm, country, "ሃገር", cache)
    rows.append(record(
        "normalization-homoglyph", country, "ሃገር", "ሀገር", "SG", True, country_analyses,
        "Homoglyph lookup is normalized while the HornMorpho display lemma is retained", "normalization_probe",
    ))
    house = next(lexeme for lexeme in lexemes if lexeme["kind"] == 0 and lexeme["raw"] == "ቤት")
    house_analyses = matching_analyses(hm, house, "ቤት", cache)
    marked_house = dict(house, raw="ቤ፟ት")
    rows.append(record(
        "normalization-gemination-mark", marked_house, "ቤት", "ቤት", "SG", True, house_analyses,
        "HornMorpho gemination marks are retained in provenance and removed from the display lemma",
        "normalization_probe",
    ))

    positive_count = sum(row["should_suggest"] == "true" for row in rows)
    negative_count = sum(row["should_suggest"] == "false" for row in rows)
    if positive_count < 200 or negative_count < 100 or len({(row["kind"], row["raw_form"]) for row in rows}) < 40:
        raise RuntimeError(
            f"golden coverage too small: positives={positive_count}, negatives={negative_count}, lexemes={len(lexemes)}"
        )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    fields = list(rows[0])
    with open(args.output, "w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=fields, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    print(f"wrote {len(rows)} cases: {positive_count} positive, {negative_count} negative, {len(lexemes)} lexemes")


if __name__ == "__main__":
    main()
