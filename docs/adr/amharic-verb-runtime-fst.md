# ADR: Execute HornMorpho verb rules on device

- Status: accepted and implemented
- Date: 2026-08-14
- Supersedes: `amharic-verb-morphology-artifact.md`
- Runtime schema: `AHRF` version 1

## Decision

Addiyon stores HornMorpho's 1,832 base verb roots, compiled weighted
transitions, unification constraints, and root-frequency ranking evidence. It
does not store generated inflected words. Android traverses and unifies the
weighted graph for the typed prefix and constructs candidate surfaces during
the lookup.

The production asset is `language/amharic/src/main/assets/amharic_verbs.ahrf`.
Its manifest requires `representation=weighted-runtime-fst` and
`generatedSurfaces=0`. The Addiyon APK check rejects the retired `.ahva`
surface automaton.

## Representation

The exporter serializes the pinned HornMorpho 5.3.6 ordinary-verb analysis
FST without Python objects:

- 89,365 states and 331,733 input/output transitions;
- 27,530 distinct weighted rule constraints plus the empty constraint;
- all 1,832 roots already compiled into the HornMorpho graph;
- 237 independently compressed constraint pages;
- root ranking copied from the base-root `stat/root.frq` table;
- zero terminal surface strings and zero generated-word records.

The current artifact is 5,064,738 bytes. It is packaged uncompressed so
Android can memory-map it instead of copying it into the managed heap. Only
four constraint pages are retained at once, bounding that page cache to under
240 KB of raw rule bytes. A 128-key result cache covers the alternate
transliteration readings used by the suggestion pipeline. Search is bounded
to 50,000 transition expansions and returns the best valid partial result if
that budget is reached.

## Runtime semantics

Every path performs HornMorpho feature-structure unification. Conflicting
paths are rejected; final paths expose the root and grammatical analysis used
for stable morphology identity. Completion ranking uses base-root frequency,
rule-feature cost, and paradigm/root diversity. It does not use a surface-word
frequency list to establish validity.

The production test proves `የሚከተ` produces `የሚከተሉትን` inside the actual
15-chip limit. Exact tests cover object suffixes, benefactive objects,
subordination, causative and passive voice, converbs, auxiliaries, and
agreement forms that the retired generated slice did not contain.

## Failure behavior

The loader validates the manifest contract, header, offsets, page bounds, and
format version. JVM artifact tests additionally verify the full CRC-32. A
failure disables verb morphology while dictionary, nominal morphology,
transliteration, and prediction continue to work.

## Reproduction

Run from any directory:

```sh
PYTHONDONTWRITEBYTECODE=1 /private/tmp/addiyon-hornmorpho-phase7/bin/python /Users/dev/code/addiyon-keyboard/tools/hornmorpho/export_runtime_fst.py --pickle /private/tmp/addiyon-hornmorpho-phase7/lib/python3.9/site-packages/hm/languages/a/pkl/v.pkl --root-frequencies /Users/dev/code/addiyon-keyboard/language/amharic/hornmorpho/stat/root.frq --output /Users/dev/code/addiyon-keyboard/language/amharic/src/main/assets/amharic_verbs.ahrf --manifest /Users/dev/code/addiyon-keyboard/language/amharic/src/main/assets/amharic_verbs_manifest.properties
```

The source remains HornMorpho GPL-3.0 material. The pinned upstream sources,
license, version, and commit stay vendored with the language pack, and the
HornMorpho license remains in the Addiyon APK.
