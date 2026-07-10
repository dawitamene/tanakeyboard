#!/usr/bin/env python3
"""
Regenerates app/src/main/assets/emoji.dat -- the emoji picker data consumed by
emoji/EmojiRepository.kt -> EmojiData.kt.

Output format (UTF-8, gzip-compressed, `.dat` NOT `.gz` because the Android
Gradle Plugin auto-decompresses `.gz` assets at build time -- same pitfall
documented in WordDictionary.kt). One tab-separated record per line:

    V<TAB>1<TAB>16.0                 format version, emoji version (first line)
    G<TAB>Smileys & Emotion          group header; opens a category
    E<TAB><emoji><TAB><name><TAB><kw1,kw2,...><TAB><variant variant ...>

  - E lines belong to the most recent G line.
  - The variants field lists the emoji's skin-tone variants (fully-qualified,
    emoji-test.txt order), space-separated; empty when the emoji has none.
  - Keywords are comma-joined (commas inside a CLDR keyword phrase are
    replaced with spaces at build time so the join is unambiguous).

Pipeline:
  1. Unicode emoji-test.txt (v16.0): codepoints, canonical group order
     (matches Gboard's tabs), fully-qualified variant sequences. Only
     `fully-qualified` rows are kept; the `Component` group is skipped.
  2. Skin-tone folding: rows whose name modifiers (after ": ") consist of
     skin-tone descriptors are folded into their base emoji's variants field
     rather than emitted as standalone entries. Mixed modifiers ("man: red
     hair, light skin tone") fold into the non-tone base ("man: red hair").
  3. CLDR 46 annotations (en.xml + annotationsDerived/en.xml): tts names and
     search keywords, keyed by emoji string (looked up with and without
     variation selectors). Missing annotation -> emoji-test.txt short name is
     used as both name and sole keyword.

Sources are cached under tools/.cache/ (gitignored). Requires internet the
first time. Run: `python3 tools/build_emoji_data.py`
"""

import gzip
import io
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, ".cache")
REPO = os.path.abspath(os.path.join(HERE, ".."))
OUT = os.path.join(REPO, "app", "src", "main", "assets", "emoji.dat")

EMOJI_VERSION = "16.0"
EMOJI_TEST_URL = f"https://unicode.org/Public/emoji/{EMOJI_VERSION}/emoji-test.txt"
CLDR_TAG = "release-46"
ANNOTATIONS_URL = (
    f"https://raw.githubusercontent.com/unicode-org/cldr/{CLDR_TAG}/common/annotations/en.xml"
)
DERIVED_URL = (
    f"https://raw.githubusercontent.com/unicode-org/cldr/{CLDR_TAG}/common/annotationsDerived/en.xml"
)

FORMAT_VERSION = "1"

# The five Fitzpatrick modifier descriptors as they appear in emoji-test.txt
# names. A name modifier list made up ONLY of these marks a skin-tone variant.
TONE_DESCRIPTORS = {
    "light skin tone",
    "medium-light skin tone",
    "medium skin tone",
    "medium-dark skin tone",
    "dark skin tone",
}

# Variation selector / skin tone codepoints stripped when looking up CLDR
# annotations (CLDR keys most emoji without FE0F).
VARIATION_SELECTORS = {"︎", "️"}


def fetch(url, name):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, name)
    if not os.path.exists(path):
        print(f"downloading {name} ...", file=sys.stderr)
        req = urllib.request.Request(url, headers={"User-Agent": "tana-emoji-build"})
        with urllib.request.urlopen(req, timeout=120) as r, open(path, "wb") as f:
            f.write(r.read())
    return path


def split_tone_modifiers(name):
    """
    Returns (base_name, is_variant). A variant's base name has the skin-tone
    descriptors removed from its modifier list; non-tone modifiers ("red
    hair", "man, woman, boy") are kept, so "man: red hair, light skin tone"
    folds under the base "man: red hair" while "family: man, woman, boy"
    stays a base of its own.
    """
    if ": " not in name:
        return name, False
    prefix, modifiers = name.split(": ", 1)
    parts = [p.strip() for p in modifiers.split(",")]
    kept = [p for p in parts if p not in TONE_DESCRIPTORS]
    if len(kept) == len(parts):
        return name, False  # no tone modifiers at all
    if kept:
        return f"{prefix}: {', '.join(kept)}", True
    return prefix, True


def parse_emoji_test(path):
    """
    Returns list[(group_name, entries)] where entries is a list of dicts
    {emoji, name, variants: [emoji...]} in file order, tones folded.
    """
    groups = []       # [(name, entries)]
    by_base_name = {}  # (group, base_name) -> entry, for folding variants
    group = None

    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if line.startswith("# group:"):
                name = line.split(":", 1)[1].strip()
                group = None if name == "Component" else name
                if group is not None:
                    groups.append((group, []))
                continue
            if not line or line.startswith("#"):
                continue
            if group is None:
                continue

            m = re.match(
                r"^([0-9A-F ]+?)\s*;\s*fully-qualified\s*#\s*(\S+)\s+E[\d.]+\s+(.+)$",
                line,
            )
            if not m:
                continue
            codepoints, _, name = m.groups()
            emoji = "".join(chr(int(cp, 16)) for cp in codepoints.split())

            base_name, is_variant = split_tone_modifiers(name)
            if is_variant:
                base = by_base_name.get((group, base_name))
                if base is None:
                    # Tone variant appearing before/without its base (doesn't
                    # happen in practice) -- keep it as a standalone entry.
                    entry = {"emoji": emoji, "name": name, "variants": []}
                    groups[-1][1].append(entry)
                    by_base_name[(group, name)] = entry
                else:
                    base["variants"].append(emoji)
            else:
                entry = {"emoji": emoji, "name": name, "variants": []}
                groups[-1][1].append(entry)
                by_base_name[(group, name)] = entry

    return [(g, entries) for g, entries in groups if entries]


def parse_annotations(paths):
    """emoji string -> (tts_name or None, [keywords]) merged across files."""
    names = {}
    keywords = {}
    for path in paths:
        root = ET.parse(path).getroot()
        for node in root.iter("annotation"):
            cp = node.get("cp")
            text = (node.text or "").strip()
            if not cp or not text:
                continue
            if node.get("type") == "tts":
                names.setdefault(cp, text)
            else:
                kws = [k.strip().replace(",", " ") for k in text.split("|")]
                keywords.setdefault(cp, [k for k in kws if k])
    return names, keywords


def annotation_key(emoji, table):
    if emoji in table:
        return emoji
    stripped = "".join(ch for ch in emoji if ch not in VARIATION_SELECTORS)
    if stripped in table:
        return stripped
    return None


def main():
    test_path = fetch(EMOJI_TEST_URL, f"emoji-test-{EMOJI_VERSION}.txt")
    ann_path = fetch(ANNOTATIONS_URL, f"cldr-{CLDR_TAG}-annotations-en.xml")
    der_path = fetch(DERIVED_URL, f"cldr-{CLDR_TAG}-annotationsDerived-en.xml")

    groups = parse_emoji_test(test_path)
    names, keywords = parse_annotations([ann_path, der_path])

    total = 0
    variants_total = 0
    missing_annotations = 0
    lines = [f"V\t{FORMAT_VERSION}\t{EMOJI_VERSION}"]

    for group, entries in groups:
        lines.append(f"G\t{group}")
        for e in entries:
            key = annotation_key(e["emoji"], names)
            name = names.get(key) if key else None
            kws = keywords.get(annotation_key(e["emoji"], keywords) or "", [])
            if name is None:
                # Fall back to the emoji-test.txt short name.
                name = e["name"]
                missing_annotations += 1
            if not kws:
                kws = [name]
            lines.append(
                f"E\t{e['emoji']}\t{name}\t{','.join(kws)}\t{' '.join(e['variants'])}"
            )
            total += 1
            variants_total += len(e["variants"])

    buf = io.BytesIO()
    # mtime=0 keeps the gzip output byte-stable across runs for the same input.
    with gzip.GzipFile(fileobj=buf, mode="wb", compresslevel=9, mtime=0) as gz:
        gz.write(("\n".join(lines) + "\n").encode("utf-8"))
    data = buf.getvalue()

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "wb") as f:
        f.write(data)

    print(f"wrote {OUT}")
    print(f"  groups: {len(groups)}")
    print(f"  base emoji: {total:,}  (skin-tone variants folded: {variants_total:,})")
    print(f"  missing CLDR annotations (name fallback): {missing_annotations:,}")
    print(f"  gzip size: {len(data):,} bytes")


if __name__ == "__main__":
    main()
