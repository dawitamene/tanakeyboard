# Phase 8 production verb slice

The Android runtime ships a deterministic `AHVA` version-2 compact automaton
at `src/main/assets/amharic_verbs.ahva`. HornMorpho 5.3.6 at commit
`7e3d93af760e27ea6dbc3b7a078d2d9c3335f618` is the build-time oracle; Python
and HornMorpho are not runtime dependencies.

The first enabled slice is deliberately narrower than complete Amharic verb
morphology:

- 100 regular roots retained from the Phase 7 oracle-spanning seed, carrying
  HornMorpho root-frequency evidence for runtime ranking;
- every Phase 7 irregular root that generates the conservative third-person
  slice;
- 64 light-verb preverbs with the HornMorpho `ብእል`/`ድርግ` auxiliaries;
- base-voice perfective and imperfective subject agreement for regular and
  light verbs;
- jussive and imperative subject agreement for regular and light verbs;
- affirmative and negative forms produced by the pinned transducer.

The irregular slice remains third-person masculine singular for this release
because the pinned generator's full deletion/unification expansion is too slow
for a reproducible build gate. Expanding irregular agreement requires a new
oracle slice and review; the runtime does not synthesize the missing forms.

Object suffixes, relative/subordinate forms, conjunction combinations, and
additional derived voices are not enabled. They require a new reviewed oracle
slice, regenerated IDs/artifact, tests, and a schema-compatible release.

Version 2 extends the Phase 7 record with a root-frequency field. Root IDs are
the first 32 bits of SHA-256 over the stable lexeme identity; feature IDs are
the first 16 bits of SHA-256 over the sorted oracle feature signature. The
compiler fails on an ID collision. Every terminal contains a canonical display
surface and at least one analysis with its stable IDs, source class, raw root,
lexeme identity, and root ranking evidence.

The production loader validates the manifest length and SHA-256, format magic,
version, CRC-32, offsets, records, sorted transitions, and UTF-8 string bounds.
Any failure disables verb suggestions and leaves dictionary, nominal,
transliteration, and prediction behavior available.

Reproduce from any terminal:

```sh
/private/tmp/addiyon-hornmorpho-phase7/bin/python /Users/dev/code/addiyon-keyboard/tools/hornmorpho/verb_spike.py production-oracle --repo /Users/dev/code/addiyon-keyboard
/opt/homebrew/bin/python3 /Users/dev/code/addiyon-keyboard/tools/hornmorpho/verb_spike.py production-artifact --repo /Users/dev/code/addiyon-keyboard
/opt/homebrew/bin/python3 /Users/dev/code/addiyon-keyboard/tools/hornmorpho/verb_spike.py verify-production --repo /Users/dev/code/addiyon-keyboard
```

The manifest and `tools/hornmorpho/verb_phase8_metrics.json` record the final
checksums and size counts. The gzipped oracle corpus and reviewed golden subset
are developer/test inputs and are absent from product APKs.
