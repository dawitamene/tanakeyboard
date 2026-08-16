#!/usr/bin/env python3

import argparse
import contextlib
import csv
import gzip
import hashlib
import importlib
import io
import json
import mmap
from pathlib import Path
import re
import statistics
import struct
import sys
import tempfile
import time
import tracemalloc
import zlib

MAGIC = b"AHVA"
VERSION = 1
PRODUCTION_VERSION = 2
HEADER = struct.Struct(">4sHH10I")
STATE = struct.Struct(">IHiH")
TRANSITION = struct.Struct(">HIH")
TERMINAL = struct.Struct(">IHIH")
ANALYSIS = struct.Struct(">IHBBIHIH")
PRODUCTION_ANALYSIS = struct.Struct(">IHBBIHIHI")
FEATURES = (
    ("perfective_3ms", "[t=p,sp=3,sn=1,sg=m]"),
    ("imperfective_3ms", "[t=i,sp=3,sn=1,sg=m]"),
    ("imperative_2ms", "[t=j,sp=2,sn=1,sg=m]"),
)
CLASSES = {"regular": 0, "irregular": 1, "light": 2}
PRODUCTION_TENSES = (
    ("perfective", "p"),
    ("imperfective", "i"),
    ("jussive_imperative", "j"),
)
PRODUCTION_REGULAR_LIMIT = 100
PRODUCTION_LIGHT_LIMIT = 64
PRODUCTION_CANDIDATE_LIMIT = 128
VERB_SOURCE_PATHS = (
    "a.lg",
    "cas/v.cas",
    "cas/v0.cas",
    "cas/vM.cas",
    "cas/vMG.cas",
    "cas/v_stem.cas",
    "cas/v_stem0.cas",
    "cas/v_stemM.cas",
    "cas/v_light_stem.cas",
    "fst/aff_bound.fst",
    "fst/iya.fst",
    "fst/kh_sS.fst",
    "fst/misc.fst",
    "fst/miscM.fst",
    "fst/misc_v.fst",
    "fst/tt.fst",
    "fst/v.mtx",
    "fst/v.root",
    "fst/v.tmp",
    "fst/v0.mtx",
    "fst/vM.mtx",
    "fst/vMG.mtx",
    "fst/v_irr.root",
    "fst/v_light.root",
    "fst/v_light.tmp",
    "fst/v_light_aff_bound.fst",
    "fst/v_light_irr.root",
    "fst/v_stem.lextr",
    "fst/y2i.fst",
    "fst/y2iM.fst",
    "lex/irr_vstem.lex",
    "lex/v_light.lex",
    "lex/vroot.lex",
    "lex/vroot0.lex",
)
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


def sha256_bytes(data):
    return hashlib.sha256(data).hexdigest()


def gzip_bytes(data):
    output = io.BytesIO()
    with gzip.GzipFile(fileobj=output, mode="wb", mtime=0) as compressed:
        compressed.write(data)
    return output.getvalue()


def load_hornmorpho():
    try:
        with contextlib.redirect_stdout(sys.stderr):
            hm = importlib.import_module("hm")
    except Exception as error:
        raise RuntimeError(
            "HornMorpho 5.3.6 is unavailable; run bootstrap_oracle.py with a Python interpreter that includes tkinter"
        ) from error
    if hm.__version__ != "5.3.6":
        raise RuntimeError(f"expected HornMorpho 5.3.6, found {hm.__version__}")
    return hm


def root_headers(path):
    pattern = re.compile(r"^(\*)?<([^>]+)>\s*(.*)$")
    rows = []
    for line in path.read_text(encoding="utf-8").splitlines():
        match = pattern.match(line.strip())
        if match is None:
            continue
        root = "".join(match.group(2).split())
        class_match = re.search(r"(?:^|,)\s*c=([A-Za-z]+)", match.group(3))
        rows.append((root, class_match.group(1) if class_match else "", match.group(1) == "*"))
    return rows


def clean_light_lexeme(line):
    if not line.strip() or line.lstrip().startswith("#") or line[0].isspace():
        return None
    raw = line.split()[0]
    surface = raw.replace("/", "").replace("-", "")
    return surface if surface and "<" not in surface else None


def generated_surfaces(hm, root, features):
    with contextlib.redirect_stdout(sys.stderr):
        values = hm.gen("a", root, pos="v", features=features, guess=False) or []
    return sorted(set(values), key=lambda value: (normalize(value), value))


def generated_paradigm(hm, root, tense):
    with contextlib.redirect_stdout(io.StringIO()):
        values = hm.gen(
            "a",
            root,
            pos="v",
            features=f"[t={tense}]",
            del_feats=["sp", "sn", "sg", "neg"],
            report_um=True,
            guess=False,
        ) or []
    pairs = {(surface, tags) for surface, tags in values}
    return sorted(pairs, key=lambda value: (normalize(value[0]), value[0], value[1]))


def ud_feature_signature(analysis):
    tags = []
    udfeats = str(analysis.get("udfeats", ""))
    if udfeats:
        tags.extend(token for token in udfeats.split("|") if token)
    if analysis.get("neg") is True and "Polarity=Neg" not in tags:
        tags.append("Polarity=Neg")
    return "|".join(sorted(set(tags)))


def analyzed_paradigm(hm, root, tense, mwe=False):
    generated = generated_surfaces(hm, root, f"[t={tense},sp=3,sn=1,sg=m]")
    generated += generated_surfaces(hm, root, f"[t={tense},sp=3,sn=1,sg=m,+neg]")
    result = set()
    for surface in generated:
        with contextlib.redirect_stdout(io.StringIO()):
            analyses = hm.anal("a", surface, guess=False, mwe=mwe) or []
        matching = [
            item
            for item in analyses
            if item.get("pos") in {"V", "v"} and
                str(item.get("root", "")).split(":", 1)[0] == root
        ]
        for analysis in matching:
            result.add((surface, ud_feature_signature(analysis)))
    return sorted(result, key=lambda value: (normalize(value[0]), value[0], value[1]))


def recognized_analysis(hm, surface, mwe=False, expected_root=None):
    with contextlib.redirect_stdout(sys.stderr):
        analyses = hm.anal("a", surface, guess=False, mwe=mwe) or []
    recognized = [item for item in analyses if item.get("pos") in {"V", "v"}]
    if expected_root is not None:
        exact = [item for item in recognized if str(item.get("root", "")).split(":", 1)[0] == expected_root]
        if exact:
            recognized = exact
    if not recognized:
        return None
    recognized.sort(
        key=lambda item: (
            str(item.get("root", "")),
            str(item.get("lemma", "")),
            str(item.get("udfeats", "")),
        )
    )
    item = recognized[0]
    return {
        "oracle_lemma": str(item.get("lemma", "")),
        "oracle_root": str(item.get("root", "")).split(":", 1)[0],
        "oracle_udfeats": str(item.get("udfeats", "")),
    }


def ordinary_rows(hm, source_class, roots, minimum):
    rows = []
    accepted = 0
    for root, root_class in roots:
        root_rows = []
        complete = True
        for feature_id, (feature_name, features) in enumerate(FEATURES):
            feature_rows = []
            for surface in generated_surfaces(hm, root, features):
                analysis = recognized_analysis(hm, surface, expected_root=root)
                if analysis is None:
                    continue
                feature_rows.append(
                    {
                        "source_class": source_class,
                        "lexeme_id": f"{source_class}:{root}:{root_class}",
                        "root": root,
                        "root_class": root_class,
                        "feature_id": feature_id,
                        "feature_name": feature_name,
                        "features": features,
                        "surface": surface,
                        "normalized_key": normalize(surface),
                        **analysis,
                    }
                )
            if not feature_rows:
                complete = False
                break
            root_rows.extend(feature_rows)
        if complete:
            rows.extend(root_rows)
            accepted += 1
        if accepted == minimum:
            break
    if accepted < minimum:
        raise RuntimeError(f"only {accepted} {source_class} roots generated all feature slices")
    return rows


def light_rows(hm, lexemes, minimum):
    auxiliaries = (("ብእል", "A"), ("ድርግ", "A"))
    rows = []
    accepted = 0
    for index, preverb in enumerate(lexemes):
        auxiliary, root_class = auxiliaries[index % len(auxiliaries)]
        lexeme_rows = []
        complete = True
        for feature_id, (feature_name, features) in enumerate(FEATURES):
            feature_rows = []
            for auxiliary_surface in generated_surfaces(hm, auxiliary, features):
                surface = f"{preverb} {auxiliary_surface}"
                analysis = recognized_analysis(hm, surface, mwe=True, expected_root=auxiliary)
                if analysis is None:
                    continue
                feature_rows.append(
                    {
                        "source_class": "light",
                        "lexeme_id": f"light:{preverb}:{auxiliary}",
                        "root": auxiliary,
                        "root_class": root_class,
                        "feature_id": feature_id,
                        "feature_name": feature_name,
                        "features": features,
                        "surface": surface,
                        "normalized_key": normalize(surface),
                        **analysis,
                    }
                )
            if not feature_rows:
                complete = False
                break
            lexeme_rows.extend(feature_rows)
        if complete:
            rows.extend(lexeme_rows)
            accepted += 1
        if accepted == minimum:
            break
    if accepted < minimum:
        raise RuntimeError(f"only {accepted} light-verb entries generated all feature slices")
    return rows


def generate_oracle(repo, output):
    hm = load_hornmorpho()
    horn = repo / "language/amharic/hornmorpho"
    irregular_headers = root_headers(horn / "fst/v_irr.root")
    irregular_roots = []
    irregular_set = set()
    for root, root_class, _ in irregular_headers:
        if root not in irregular_set:
            irregular_roots.append((root, root_class))
            irregular_set.add(root)
    regular_candidates = []
    seen = set()
    for root, root_class, _ in root_headers(horn / "lex/vroot.lex"):
        if root not in irregular_set and root not in seen:
            regular_candidates.append((root, root_class))
            seen.add(root)
    light_candidates = []
    seen_light = set()
    for line in (horn / "lex/v_light.lex").read_text(encoding="utf-8").splitlines():
        surface = clean_light_lexeme(line)
        if surface and surface not in seen_light:
            light_candidates.append(surface)
            seen_light.add(surface)
    rows = ordinary_rows(hm, "regular", regular_candidates, 100)
    rows.extend(ordinary_rows(hm, "irregular", irregular_roots, min(12, len(irregular_roots))))
    rows.extend(light_rows(hm, light_candidates, 20))
    rows.sort(
        key=lambda row: (
            CLASSES[row["source_class"]],
            row["lexeme_id"],
            row["feature_id"],
            row["normalized_key"],
            row["surface"],
        )
    )
    fieldnames = (
        "source_class",
        "lexeme_id",
        "root",
        "root_class",
        "feature_id",
        "feature_name",
        "features",
        "surface",
        "normalized_key",
        "oracle_root",
        "oracle_lemma",
        "oracle_udfeats",
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=fieldnames, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    counts = {}
    for row in rows:
        counts.setdefault(row["source_class"], set()).add(row["lexeme_id"])
    print(json.dumps({key: len(value) for key, value in counts.items()}, sort_keys=True))
    print(f"rows={len(rows)} sha256={sha256_bytes(output.read_bytes())}")


def frequency_table(path):
    result = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        key, separator, raw_frequency = line.rpartition("\t")
        if not separator:
            continue
        try:
            result[key] = int(raw_frequency)
        except ValueError:
            continue
    return result


def stable_id(value, bits):
    size = bits // 8
    return int.from_bytes(hashlib.sha256(value.encode("utf-8")).digest()[:size], "big")


def ranked_roots(candidates, frequencies, limit):
    scored = []
    seen = set()
    for root, root_class in candidates:
        identity = (root, root_class)
        if identity in seen:
            continue
        seen.add(identity)
        score = max(
            (frequency for key, frequency in frequencies.items() if key.startswith(f"{root}:{root_class}")),
            default=0,
        )
        scored.append((score, root, root_class))
    scored.sort(key=lambda value: (-value[0], value[1], value[2]))
    return [(root, root_class, score) for score, root, root_class in scored[:limit]]


def production_ordinary_rows(hm, source_class, roots, limit=None, full_agreement=False):
    rows = []
    accepted = 0
    for root, root_class, root_frequency in roots:
        lexeme_id = f"{source_class}:{root}:{root_class}"
        lexeme_rows = []
        complete = True
        for feature_name, tense in PRODUCTION_TENSES:
            paradigm = (
                generated_paradigm(hm, root, tense)
                if full_agreement
                else analyzed_paradigm(hm, root, tense)
            )
            if not paradigm:
                complete = False
                break
            for surface, tags in paradigm:
                lexeme_rows.append(
                    {
                        "source_class": source_class,
                        "lexeme_id": lexeme_id,
                        "root": root,
                        "root_class": root_class,
                        "feature_name": feature_name,
                        "features": f"[t={tense},del=sp|sn|sg|neg]",
                        "oracle_udfeats": tags,
                        "surface": surface,
                        "normalized_key": normalize(surface),
                        "root_frequency": root_frequency,
                    }
                )
        if complete:
            rows.extend(lexeme_rows)
            accepted += 1
            if limit is not None and accepted >= limit:
                break
    return rows


def production_light_rows(hm, lexemes, frequencies):
    auxiliaries = ("ብእል", "ድርግ")
    generated = {
        (auxiliary, feature_name): generated_paradigm(hm, auxiliary, tense)
        for auxiliary in auxiliaries
        for feature_name, tense in PRODUCTION_TENSES
    }
    ranked = sorted(
        set(lexemes),
        key=lambda value: (-frequencies.get(value, 0), value),
    )[:PRODUCTION_LIGHT_LIMIT]
    rows = []
    for index, preverb in enumerate(ranked):
        auxiliary = auxiliaries[index % len(auxiliaries)]
        root_frequency = frequencies.get(preverb, 0) + max(
            (value for key, value in frequencies.items() if key.startswith(f"{auxiliary}:")),
            default=0,
        )
        lexeme_id = f"light:{preverb}:{auxiliary}"
        for feature_name, _ in PRODUCTION_TENSES:
            for auxiliary_surface, tags in generated[(auxiliary, feature_name)]:
                surface = f"{preverb} {auxiliary_surface}"
                rows.append(
                    {
                        "source_class": "light",
                        "lexeme_id": lexeme_id,
                        "root": auxiliary,
                        "root_class": "A",
                        "feature_name": feature_name,
                        "features": f"[t={dict(PRODUCTION_TENSES)[feature_name]},del=sp|sn|sg|neg]",
                        "oracle_udfeats": tags,
                        "surface": surface,
                        "normalized_key": normalize(surface),
                        "root_frequency": root_frequency,
                    }
                )
    return rows


def write_rows(path, rows, fieldnames):
    path.parent.mkdir(parents=True, exist_ok=True)
    opener = gzip.open if path.suffix == ".gz" else open
    kwargs = {"encoding": "utf-8", "newline": ""}
    if path.suffix == ".gz":
        with path.open("wb") as raw:
            with gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as compressed:
                with io.TextIOWrapper(compressed, encoding="utf-8", newline="") as target:
                    writer = csv.DictWriter(target, fieldnames=fieldnames, delimiter="\t", lineterminator="\n")
                    writer.writeheader()
                    writer.writerows(rows)
        return
    with opener(path, "w", **kwargs) as target:
        writer = csv.DictWriter(target, fieldnames=fieldnames, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def generate_production_oracle(repo, output, golden_output):
    hm = load_hornmorpho()
    horn = repo / "language/amharic/hornmorpho"
    frequencies = frequency_table(horn / "stat/root.frq")
    irregular_headers = root_headers(horn / "fst/v_irr.root")
    irregular_set = {root for root, _, _ in irregular_headers}
    regular_candidates = [
        (root, root_class)
        for root, root_class, _ in root_headers(horn / "lex/vroot.lex")
        if root not in irregular_set
    ]
    irregular_candidates = [(root, root_class) for root, root_class, _ in irregular_headers]
    light_candidates = [
        surface
        for line in (horn / "lex/v_light.lex").read_text(encoding="utf-8").splitlines()
        if (surface := clean_light_lexeme(line)) is not None
    ]
    spike_rows = load_rows(repo / "tools/hornmorpho/fixtures/amharic_verb_spike.tsv")
    seed_regular = []
    seed_irregular = []
    seen_seed = set()
    for row in spike_rows:
        identity = (row["root"], row["root_class"])
        if identity in seen_seed or row["source_class"] == "light":
            continue
        seen_seed.add(identity)
        score = max(
            (
                frequency
                for key, frequency in frequencies.items()
                if key.startswith(f"{row['root']}:{row['root_class']}")
            ),
            default=0,
        )
        target = seed_irregular if row["source_class"] == "irregular" else seed_regular
        target.append((row["root"], row["root_class"], score))
    regular = seed_regular + [
        row
        for row in ranked_roots(regular_candidates, frequencies, PRODUCTION_CANDIDATE_LIMIT)
        if (row[0], row[1]) not in {(root, root_class) for root, root_class, _ in seed_regular}
    ]
    irregular = seed_irregular
    rows = production_ordinary_rows(
        hm,
        "regular",
        regular,
        limit=PRODUCTION_REGULAR_LIMIT,
        full_agreement=True,
    )
    rows.extend(production_ordinary_rows(hm, "irregular", irregular, full_agreement=False))
    rows.extend(production_light_rows(hm, light_candidates, frequencies))
    feature_ids = {}
    for tags in sorted({row["oracle_udfeats"] for row in rows}):
        feature_id = stable_id(tags, 16)
        if feature_id in feature_ids.values():
            raise RuntimeError(f"production feature id collision for {tags}")
        feature_ids[tags] = feature_id
    for row in rows:
        row["feature_id"] = feature_ids[row["oracle_udfeats"]]
    rows.sort(
        key=lambda row: (
            CLASSES[row["source_class"]],
            row["lexeme_id"],
            row["feature_id"],
            row["normalized_key"],
            row["surface"],
        )
    )
    fieldnames = (
        "source_class",
        "lexeme_id",
        "root",
        "root_class",
        "root_frequency",
        "feature_id",
        "feature_name",
        "features",
        "surface",
        "normalized_key",
        "oracle_udfeats",
    )
    write_rows(output, rows, fieldnames)
    golden = []
    grouped = {}
    for row in rows:
        grouped.setdefault((row["source_class"], row["feature_name"], row["oracle_udfeats"]), []).append(row)
    for key in sorted(grouped):
        golden.extend(grouped[key][:3])
    write_rows(golden_output, golden, fieldnames)
    counts = {}
    for row in rows:
        counts.setdefault(row["source_class"], set()).add(row["lexeme_id"])
    print(json.dumps({key: len(value) for key, value in counts.items()}, sort_keys=True))
    print(f"rows={len(rows)} features={len(feature_ids)} sha256={sha256_bytes(output.read_bytes())}")


def load_rows(path):
    opener = gzip.open if path.suffix == ".gz" else open
    with opener(path, "rt", encoding="utf-8", newline="") as source:
        return list(csv.DictReader(source, delimiter="\t"))


class Node:
    __slots__ = ("children", "analyses", "surface")

    def __init__(self):
        self.children = {}
        self.analyses = []
        self.surface = ""


def minimize(root):
    registry = {}

    def intern(node):
        children = tuple((label, intern(child)) for label, child in sorted(node.children.items()))
        signature = (tuple(node.analyses), node.surface, tuple((label, id(child)) for label, child in children))
        existing = registry.get(signature)
        if existing is not None:
            return existing
        node.children = dict(children)
        registry[signature] = node
        return node

    return intern(root)


def artifact_rows(rows, pruned):
    if not pruned:
        return rows
    selected = {}
    for row in rows:
        key = (row["lexeme_id"], int(row["feature_id"]))
        current = selected.get(key)
        candidate = (row["normalized_key"], row["surface"], row)
        if current is None or candidate[:2] < current[:2]:
            selected[key] = candidate
    return [selected[key][2] for key in sorted(selected)]


def build_artifact(rows, pruned=False, version=VERSION):
    selected = artifact_rows(rows, pruned)
    identities = sorted({row["lexeme_id"] for row in selected})
    if version == VERSION:
        root_ids = {value: index for index, value in enumerate(identities)}
    elif version == PRODUCTION_VERSION:
        root_ids = {value: stable_id(value, 32) for value in identities}
        if len(set(root_ids.values())) != len(root_ids):
            raise RuntimeError("production root id collision")
    else:
        raise ValueError(f"unsupported artifact version {version}")
    root = Node()
    for row in selected:
        node = root
        for character in row["normalized_key"]:
            if ord(character) > 0xFFFF:
                raise ValueError(f"non-BMP character in {row['surface']!r}")
            node = node.children.setdefault(character, Node())
        analysis = (
            root_ids[row["lexeme_id"]],
            int(row["feature_id"]),
            CLASSES[row["source_class"]],
            row["lexeme_id"],
            row["root"],
            int(row.get("root_frequency", 0)),
        )
        if analysis not in node.analyses:
            node.analyses.append(analysis)
            node.analyses.sort()
        if not node.surface or row["surface"] < node.surface:
            node.surface = row["surface"]
    root = minimize(root)
    states = []
    ids = {}
    queue = [root]
    while queue:
        node = queue.pop(0)
        if id(node) in ids:
            continue
        ids[id(node)] = len(states)
        states.append(node)
        for child in node.children.values():
            if id(child) not in ids:
                queue.append(child)
    strings = bytearray()
    string_index = {}

    def add_string(value):
        encoded = value.encode("utf-8")
        cached = string_index.get(encoded)
        if cached is not None:
            return cached
        result = (len(strings), len(encoded))
        strings.extend(encoded)
        string_index[encoded] = result
        return result

    transitions = []
    terminals = []
    analyses = []
    state_records = []
    for node in states:
        first_transition = len(transitions)
        for label, child in sorted(node.children.items()):
            transitions.append((ord(label), ids[id(child)]))
        terminal_index = -1
        if node.analyses:
            terminal_index = len(terminals)
            first_analysis = len(analyses)
            for root_id, feature_id, class_id, lexeme, verb_root, root_frequency in node.analyses:
                lexeme_offset, lexeme_length = add_string(lexeme)
                root_offset, root_length = add_string(verb_root)
                analyses.append(
                    (
                        root_id,
                        feature_id,
                        class_id,
                        lexeme_offset,
                        lexeme_length,
                        root_offset,
                        root_length,
                        root_frequency,
                    )
                )
            surface_offset, surface_length = add_string(node.surface)
            terminals.append(
                (first_analysis, len(node.analyses), surface_offset, surface_length)
            )
        state_records.append((first_transition, len(node.children), terminal_index))
    state_offset = HEADER.size
    transition_offset = state_offset + len(state_records) * STATE.size
    terminal_offset = transition_offset + len(transitions) * TRANSITION.size
    analysis_record = ANALYSIS if version == VERSION else PRODUCTION_ANALYSIS
    analysis_offset = terminal_offset + len(terminals) * TERMINAL.size
    string_offset = analysis_offset + len(analyses) * analysis_record.size
    payload = bytearray()
    for first, count, terminal_index in state_records:
        payload.extend(STATE.pack(first, count, terminal_index, 0))
    for label, target in transitions:
        payload.extend(TRANSITION.pack(label, target, 0))
    for first, count, surface_offset, surface_length in terminals:
        payload.extend(TERMINAL.pack(first, count, surface_offset, surface_length))
    for root_id, feature_id, class_id, lexeme_offset, lexeme_length, root_offset, root_length, root_frequency in analyses:
        payload.extend(
            analysis_record.pack(
                *(
                    root_id,
                    feature_id,
                    class_id,
                    0,
                    lexeme_offset,
                    lexeme_length,
                    root_offset,
                    root_length,
                    *(() if version == VERSION else (root_frequency,)),
                )
            )
        )
    payload.extend(strings)
    header = HEADER.pack(
        MAGIC,
        version,
        HEADER.size,
        len(state_records),
        len(transitions),
        len(terminals),
        len(analyses),
        state_offset,
        transition_offset,
        terminal_offset,
        analysis_offset,
        string_offset,
        zlib.crc32(payload),
    )
    data = header + payload
    return data, {
        "schema_version": version,
        "states": len(state_records),
        "transitions": len(transitions),
        "terminal_surfaces": len(terminals),
        "terminal_analyses": len(analyses),
        "root_entries": len(root_ids),
        "source_rows": len(selected),
    }


def write_production_artifact(repo, corpus, output, manifest_output, metrics_output):
    rows = load_rows(corpus)
    data, counts = build_artifact(rows, version=PRODUCTION_VERSION)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(data)
    digest = sha256_bytes(data)
    manifest_output.write_text(
        "schemaVersion=2\n"
        f"asset=amharic_verbs.ahva\nlength={len(data)}\nsha256={digest}\n"
        "hornmorphoVersion=5.3.6\n"
        "hornmorphoCommit=7e3d93af760e27ea6dbc3b7a078d2d9c3335f618\n",
        encoding="utf-8",
    )
    metrics = {
        **counts,
        "compressed_bytes": len(gzip_bytes(data)),
        "installed_bytes": len(data),
        "sha256": digest,
        "corpus_sha256": sha256_bytes(corpus.read_bytes()),
        "feature_signatures": len({row["feature_id"] for row in rows}),
        "lexemes_by_class": {
            source_class: len({row["lexeme_id"] for row in rows if row["source_class"] == source_class})
            for source_class in sorted(CLASSES)
        },
        "budgets": {
            "compressed_bytes": 3 * 1024 * 1024,
            "eager_heap_bytes": 5 * 1024 * 1024,
        },
    }
    metrics_output.write_text(
        json.dumps(metrics, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(metrics, ensure_ascii=False, sort_keys=True))


def verify_production(corpus, output, manifest_output):
    rows = load_rows(corpus)
    rebuilt, counts = build_artifact(rows, version=PRODUCTION_VERSION)
    if output.read_bytes() != rebuilt:
        raise RuntimeError("production artifact is not reproducible")
    manifest = {}
    for line in manifest_output.read_text(encoding="utf-8").splitlines():
        key, separator, value = line.partition("=")
        if separator:
            manifest[key] = value
    if manifest.get("length") != str(len(rebuilt)) or manifest.get("sha256") != sha256_bytes(rebuilt):
        raise RuntimeError("production artifact manifest mismatch")
    if counts["root_entries"] < PRODUCTION_REGULAR_LIMIT:
        raise RuntimeError("production artifact root coverage is below the declared slice")
    if set(CLASSES) != {row["source_class"] for row in rows}:
        raise RuntimeError("production artifact does not span all verb source classes")
    if {row["feature_name"] for row in rows} != {value[0] for value in PRODUCTION_TENSES}:
        raise RuntimeError("production artifact feature slice is incomplete")
    negatives = 0
    automaton = Automaton(output, expected_version=PRODUCTION_VERSION)
    try:
        keys = {row["normalized_key"] for row in rows}
        for key in sorted(keys):
            if automaton.exact(key) is None:
                raise RuntimeError(f"production artifact missed {key!r}")
        for key in sorted(keys)[:1000]:
            near_miss = key + "ሃ"
            if near_miss not in keys and automaton.exact(near_miss) is not None:
                raise RuntimeError(f"production artifact accepted negative {near_miss!r}")
            negatives += near_miss not in keys
    finally:
        automaton.close()
    print(f"Phase 8 production artifact is byte-identical; negative_exact_cases={negatives}")


def write_phase9_review(repo, corpus, output):
    rows = load_rows(corpus)
    selected = []
    for source_class in sorted(CLASSES, key=CLASSES.get):
        source_rows = [row for row in rows if row["source_class"] == source_class]
        selected.extend(sorted(source_rows, key=lambda row: (-int(row["root_frequency"]), row["surface"]))[:30])
    review_rows = []
    for row in selected:
        review_rows.append(
            {
                "source_group": "generated_verb",
                "surface": row["surface"],
                "context_or_analysis": f"{row['feature_name']} {row['oracle_udfeats']}",
                "provenance": f"HornMorpho 5.3.6 {row['lexeme_id']}",
                "reviewer": "",
                "verdict": "",
                "canonicality": "",
                "notes": "",
            }
        )
    def add_review(source_group, surface, context, provenance):
        review_rows.append(
            {
                "source_group": source_group,
                "surface": surface,
                "context_or_analysis": context,
                "provenance": provenance,
                "reviewer": "",
                "verdict": "",
                "canonicality": "",
                "notes": "",
            }
        )

    words_relative = "language/amharic/src/dictionary/amharic_words.dat"
    with gzip.open(repo / words_relative, "rt", encoding="utf-8") as source:
        for line in list(source)[:20]:
            surface, frequency = line.rstrip("\n").split("\t")
            add_review("exact_base", surface, f"frequency={frequency}", words_relative)

    nominal_relative = "language/amharic/src/test/resources/hornmorpho_nominal_phase4.tsv"
    with (repo / nominal_relative).open(encoding="utf-8", newline="") as source:
        nominal_rows = list(csv.DictReader(source, delimiter="\t"))
    supported_nominals = [row for row in nominal_rows if row["classification"] == "supported"]
    for row in supported_nominals[:20]:
        add_review(
            "generated_nominal",
            row["surface"],
            f"{row['reason']} | {row['source_features']}",
            nominal_relative,
        )

    stats_relative = "language/amharic/src/dictionary/amharic_surface_stats.dat"
    with gzip.open(repo / stats_relative, "rt", encoding="utf-8") as source:
        surface_stats = [line.rstrip("\n").split("\t") for line in source]
    for surface, frequency in sorted(surface_stats, key=lambda item: (-int(item[1]), item[0]))[:20]:
        add_review("surface_stat", surface, f"frequency={frequency}", stats_relative)

    ngram_relative = "language/amharic/src/dictionary/amharic_ngram_review.tsv"
    with (repo / ngram_relative).open(encoding="utf-8", newline="") as source:
        for row in list(csv.DictReader(source, delimiter="\t"))[:20]:
            add_review(
                "ngram_prediction",
                row["successor"],
                f"{row['order']} context={row['context']} rank={row['rank']}",
                ngram_relative,
            )

    alternates = [row for row in supported_nominals if "alternate" in row["reason"]]
    for row in alternates[:20]:
        add_review(
            "orthographic_alternate",
            row["surface"],
            f"{row['reason']} | lemma={row['lemma']}",
            nominal_relative,
        )
    fuzzy_surfaces = sorted({row["surface"] for row in selected})[:20]
    for surface in fuzzy_surfaces:
        review_rows.append(
            {
                "source_group": "fuzzy_correction",
                "surface": surface,
                "context_or_analysis": "analyzer-backed target; reviewer supplies natural typo context",
                "provenance": "amharic_verbs.ahva",
                "reviewer": "",
                "verdict": "",
                "canonicality": "",
                "notes": "",
            }
        )
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as target:
        fieldnames = (
            "source_group",
            "surface",
            "context_or_analysis",
            "provenance",
            "reviewer",
            "verdict",
            "canonicality",
            "notes",
        )
        writer = csv.DictWriter(target, fieldnames=fieldnames, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(review_rows)
    print(f"review_rows={len(review_rows)} sha256={sha256_bytes(output.read_bytes())}")


class Automaton:
    def __init__(self, path, expected_version=VERSION):
        self.file = path.open("rb")
        self.data = mmap.mmap(self.file.fileno(), 0, access=mmap.ACCESS_READ)
        values = HEADER.unpack_from(self.data, 0)
        if values[0] != MAGIC or values[1] != expected_version or values[2] != HEADER.size:
            raise ValueError("unsupported verb automaton")
        (
            self.state_count,
            self.transition_count,
            self.terminal_count,
            self.analysis_count,
            self.state_offset,
            self.transition_offset,
            self.terminal_offset,
            self.analysis_offset,
            self.string_offset,
            checksum,
        ) = values[3:]
        if zlib.crc32(self.data[HEADER.size:]) != checksum:
            raise ValueError("verb automaton checksum mismatch")

    def close(self):
        self.data.close()
        self.file.close()

    def state(self, index):
        return STATE.unpack_from(self.data, self.state_offset + index * STATE.size)

    def transition(self, state_index, character):
        first, count, _, _ = self.state(state_index)
        target_label = ord(character)
        low = 0
        high = count
        while low < high:
            middle = (low + high) // 2
            label, target, _ = TRANSITION.unpack_from(
                self.data,
                self.transition_offset + (first + middle) * TRANSITION.size,
            )
            if label < target_label:
                low = middle + 1
            elif label > target_label:
                high = middle
            else:
                return target
        return None

    def terminal(self, state_index):
        terminal_index = self.state(state_index)[2]
        if terminal_index < 0:
            return None
        first, count, surface_offset, surface_length = TERMINAL.unpack_from(
            self.data,
            self.terminal_offset + terminal_index * TERMINAL.size,
        )
        surface = self.data[
            self.string_offset + surface_offset:self.string_offset + surface_offset + surface_length
        ].decode("utf-8")
        return first, count, surface

    def exact(self, key):
        state = 0
        for character in key:
            state = self.transition(state, character)
            if state is None:
                return None
        return self.terminal(state)

    def prefix(self, prefix, limit=8):
        state = 0
        for character in prefix:
            state = self.transition(state, character)
            if state is None:
                return []
        results = []
        stack = [state]
        while stack and len(results) < limit:
            current = stack.pop()
            terminal = self.terminal(current)
            if terminal is not None:
                results.append(terminal[2])
            first, count, _, _ = self.state(current)
            children = []
            for index in range(count):
                _, target, _ = TRANSITION.unpack_from(
                    self.data,
                    self.transition_offset + (first + index) * TRANSITION.size,
                )
                children.append(target)
            stack.extend(reversed(children))
        return results


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, max(0, int(len(ordered) * fraction) - 1))]


def benchmark_automaton(path, rows, counts):
    keys = sorted({row["normalized_key"] for row in rows})
    prefixes = sorted({key[: max(1, min(3, len(key) - 1))] for key in keys})
    cold = []
    for _ in range(25):
        started = time.perf_counter_ns()
        automaton = Automaton(path)
        cold.append((time.perf_counter_ns() - started) / 1_000_000)
        automaton.close()
    tracemalloc.start()
    automaton = Automaton(path)
    _, peak = tracemalloc.get_traced_memory()
    tracemalloc.stop()
    for key in keys:
        if automaton.exact(key) is None:
            raise RuntimeError(f"artifact missed exact key {key!r}")
    exact_times = []
    prefix_times = []
    for index in range(10_000):
        key = keys[index % len(keys)]
        started = time.perf_counter_ns()
        automaton.exact(key)
        exact_times.append((time.perf_counter_ns() - started) / 1_000_000)
        prefix = prefixes[index % len(prefixes)]
        started = time.perf_counter_ns()
        automaton.prefix(prefix)
        prefix_times.append((time.perf_counter_ns() - started) / 1_000_000)
    automaton.close()
    data = path.read_bytes()
    return {
        **counts,
        "installed_bytes": len(data),
        "compressed_bytes": len(gzip_bytes(data)),
        "sha256": sha256_bytes(data),
        "cold_open_ms_p50": round(statistics.median(cold), 6),
        "cold_open_ms_p95": round(percentile(cold, 0.95), 6),
        "exact_ms_p50": round(statistics.median(exact_times), 6),
        "exact_ms_p95": round(percentile(exact_times, 0.95), 6),
        "prefix_ms_p50": round(statistics.median(prefix_times), 6),
        "prefix_ms_p95": round(percentile(prefix_times, 0.95), 6),
        "reference_peak_allocated_bytes": peak,
    }


def source_prototype(repo):
    horn = repo / "language/amharic/hornmorpho"
    paths = [horn / relative for relative in VERB_SOURCE_PATHS]
    missing_paths = [path for path in paths if not path.is_file()]
    if missing_paths:
        raise RuntimeError(f"missing verb source dependencies: {missing_paths}")
    available = {value for path in paths for value in (path.name, path.stem)}
    cascade_references = set()
    for path in paths:
        if path.suffix == ".cas":
            text = path.read_text(encoding="utf-8")
            cascade_references.update(re.findall(r">([^<\n]+)<", text))
            cascade_references.update(re.findall(r"\+([^+\n]+)\+", text))
    unresolved = sorted(reference for reference in cascade_references if reference not in available)
    if unresolved:
        raise RuntimeError(f"unresolved verb cascade components: {unresolved}")
    combined = bytearray()
    transitions = 0
    declarations = 0
    for path in paths:
        relative = path.relative_to(horn).as_posix()
        content = path.read_bytes()
        combined.extend(relative.encode("utf-8") + b"\0" + struct.pack(">I", len(content)) + content)
        text = content.decode("utf-8")
        transitions += sum("->" in line for line in text.splitlines())
        declarations += sum(line.lstrip().startswith("+") for line in text.splitlines())
    return {
        "source_files": len(paths),
        "installed_bytes": len(combined),
        "compressed_bytes": len(gzip_bytes(combined)),
        "parsed_transition_lines": transitions,
        "lexical_declarations": declarations,
        "resolved_cascade_components": len(cascade_references),
        "exact_ms_p50": None,
        "exact_ms_p95": None,
        "prefix_ms_p50": None,
        "prefix_ms_p95": None,
        "coverage": 0.0,
        "status": "dependency-complete parser only; unification execution not implemented",
    }


def write_artifacts(repo, corpus, selected_output, pruned_output):
    rows = load_rows(corpus)
    selected_data, selected_counts = build_artifact(rows)
    pruned_data, pruned_counts = build_artifact(rows, pruned=True)
    selected_output.parent.mkdir(parents=True, exist_ok=True)
    pruned_output.parent.mkdir(parents=True, exist_ok=True)
    selected_output.write_bytes(selected_data)
    pruned_output.write_bytes(pruned_data)
    print(f"selected {selected_counts} sha256={sha256_bytes(selected_data)}")
    print(f"pruned {pruned_counts} sha256={sha256_bytes(pruned_data)}")


def benchmark(repo, corpus, selected_output, pruned_output, metrics_output):
    rows = load_rows(corpus)
    selected_data, selected_counts = build_artifact(rows)
    pruned_data, pruned_counts = build_artifact(rows, pruned=True)
    if selected_output.read_bytes() != selected_data or pruned_output.read_bytes() != pruned_data:
        raise RuntimeError("checked-in artifact differs from deterministic rebuild")
    counts_by_class = {}
    for row in rows:
        counts_by_class.setdefault(row["source_class"], set()).add(row["lexeme_id"])
    selected = benchmark_automaton(selected_output, rows, selected_counts)
    pruned_selected_rows = artifact_rows(rows, True)
    pruned = benchmark_automaton(pruned_output, pruned_selected_rows, pruned_counts)
    selected["coverage"] = 1.0
    pruned["coverage"] = round(len(pruned_selected_rows) / len(rows), 6)
    metrics = {
        "schema_version": 1,
        "oracle": {
            "hornmorpho_version": "5.3.6",
            "commit": "7e3d93af760e27ea6dbc3b7a078d2d9c3335f618",
            "corpus_sha256": sha256_bytes(corpus.read_bytes()),
            "rows": len(rows),
            "lexemes_by_class": {key: len(value) for key, value in sorted(counts_by_class.items())},
        },
        "budgets": {
            "compressed_bytes": 3 * 1024 * 1024,
            "eager_heap_bytes": 5 * 1024 * 1024,
            "exact_ms_p95": 10,
            "prefix_ms_p95": 25,
        },
        "option_a_compact_automaton": selected,
        "option_b_pruned_dafsa": pruned,
        "option_c_mechanical_rule_graph": source_prototype(repo),
        "decision": "option_a_compact_automaton",
    }
    metrics_output.parent.mkdir(parents=True, exist_ok=True)
    metrics_output.write_text(json.dumps(metrics, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(metrics_output)


def verify(corpus, selected_output, pruned_output):
    rows = load_rows(corpus)
    selected_data, _ = build_artifact(rows)
    pruned_data, _ = build_artifact(rows, pruned=True)
    if selected_output.read_bytes() != selected_data:
        raise RuntimeError("selected artifact is not reproducible")
    if pruned_output.read_bytes() != pruned_data:
        raise RuntimeError("pruned artifact is not reproducible")
    if len({row["lexeme_id"] for row in rows}) < 100:
        raise RuntimeError("oracle corpus has fewer than 100 lexeme entries")
    if set(CLASSES) != {row["source_class"] for row in rows}:
        raise RuntimeError("oracle corpus does not span regular, irregular, and light classes")
    print("Phase 7 verb artifacts are byte-identical and the corpus class gate passes")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "command",
        choices=(
            "oracle",
            "artifacts",
            "benchmark",
            "verify",
            "production-oracle",
            "phase9-review",
        ),
    )
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--corpus", type=Path)
    parser.add_argument("--selected-output", type=Path)
    parser.add_argument("--pruned-output", type=Path)
    parser.add_argument("--metrics-output", type=Path)
    parser.add_argument("--manifest-output", type=Path)
    parser.add_argument("--golden-output", type=Path)
    args = parser.parse_args()
    corpus = args.corpus or args.repo / "tools/hornmorpho/fixtures/amharic_verb_spike.tsv"
    selected_output = args.selected_output or args.repo / "language/amharic/src/test/resources/amharic_verb_automaton_v1.bin"
    pruned_output = args.pruned_output or args.repo / "tools/hornmorpho/fixtures/amharic_verb_pruned_dafsa_v1.bin"
    metrics_output = args.metrics_output or args.repo / "tools/hornmorpho/verb_spike_metrics.json"
    production_corpus = args.repo / "tools/hornmorpho/fixtures/amharic_verb_phase8.tsv.gz"
    production_golden = args.repo / "language/amharic/src/test/resources/hornmorpho_verb_phase8.tsv"
    phase9_review = args.repo / "tools/hornmorpho/fixtures/amharic_phase9_review.tsv"
    if args.command == "oracle":
        generate_oracle(args.repo, corpus)
    elif args.command == "artifacts":
        write_artifacts(args.repo, corpus, selected_output, pruned_output)
    elif args.command == "benchmark":
        benchmark(args.repo, corpus, selected_output, pruned_output, metrics_output)
    elif args.command == "verify":
        verify(corpus, selected_output, pruned_output)
    elif args.command == "production-oracle":
        generate_production_oracle(
            args.repo,
            args.corpus or production_corpus,
            args.golden_output or production_golden,
        )
    elif args.command == "phase9-review":
        write_phase9_review(
            args.repo,
            args.corpus or production_corpus,
            phase9_review,
        )


if __name__ == "__main__":
    main()
