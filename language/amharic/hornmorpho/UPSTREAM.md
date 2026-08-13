# HornMorpho Amharic lexicon snapshot

This directory contains the Amharic lexical sources used to build Addiyon's
baseline dictionary.

- Upstream: <https://github.com/hltdi/HornMorpho>
- HornMorpho version: 5.3.6
- Commit: `7e3d93af760e27ea6dbc3b7a078d2d9c3335f618`
- Snapshot date: 2026-08-12
- License: GNU GPL version 3; see `LICENSE.txt`

HornMorpho states that its Amharic lexicon was extracted mainly from Amsalu
Aklilu's *Amharic-English Dictionary* (Kuraz, 2004). Addiyon therefore keeps
the upstream source, version, and license with the transformed data and also
packages the license in the Amharic Android language module.

## Included sources

- `lex/n_stem.lex`: noun, adjective, determiner, pronoun, and other stems
- `lex/n_stem_an.lex`: alternate noun-stem class
- `lex/n_name.lex`: person names
- `lex/n_place.lex`: place names
- `lex/vroot.lex`: verb roots and root classes
- `stat/root.frq`: HornMorpho's lemma/root frequency statistics
- `a.lg`: language definition, feature inventory, and nominal boundary symbol sets
- `a.um`: HornMorpho-to-UniMorph/UD feature mappings
- `cas/n.cas` and `cas/nM.cas`: nominal cascade composition order
- `fst/n.mtx` and `fst/nM.mtx`: nominal morphotactics
- `fst/n_aff_bound.fst` and `fst/n_aff_boundM.fst`: nominal boundary rewrites
- `fst/misc.fst` and `fst/miscM.fst`: nominal-cascade normalization
- `cas/v.cas`, `cas/v0.cas`, `cas/vM.cas`, and `cas/vMG.cas`: single-word and
  multiword verb cascade composition order
- `cas/v_stem.cas`, `cas/v_stem0.cas`, `cas/v_stemM.cas`, and
  `cas/v_light_stem.cas`: ordinary, alternate, multiword, and light-verb stem
  compilation order
- `fst/v.mtx`, `fst/v0.mtx`, `fst/vM.mtx`, and `fst/vMG.mtx`: verb
  morphotactics
- `fst/v.root`, `fst/v_irr.root`, `fst/v_light.root`, and
  `fst/v_light_irr.root`: regular, irregular, and light-verb root patterns
- `fst/v.tmp`, `fst/v_light.tmp`, `fst/v_stem.lextr`, `fst/tt.fst`,
  `fst/aff_bound.fst`, `fst/v_light_aff_bound.fst`, `fst/misc_v.fst`,
  `fst/y2i.fst`, `fst/y2iM.fst`, `fst/iya.fst`, and `fst/kh_sS.fst`: direct
  verb-stem and surface-cascade dependencies
- `lex/vroot0.lex`, `lex/v_light.lex`, and `lex/irr_vstem.lex`: alternate
  roots, light-verb preverbs, and irregular verb stems

Every control file above is an unmodified copy from commit
`7e3d93af760e27ea6dbc3b7a078d2d9c3335f618`. The cascade files establish
that nominal analysis composes `misc`, `n_aff_bound`, and `n.mtx` in that
order, with the corresponding `M` files used for multiword analysis. The
boundary string sets are defined in `a.lg`; no rule is inferred from a newer
upstream branch.

## Phase 7 verb architecture decision

Phase 7 vendors the complete direct verb-cascade source set above from the
same commit and license. The reproducible oracle fixture contains 407
HornMorpho-generated and analyzer-round-tripped rows: 100 regular roots, 12
irregular roots, and 20 light-verb lexemes. Its SHA-256 is
`9d48b215d264ade3d2bb7c67a9e868ddc31586f9c1cc4f98c2f22971bb789110`.

The accepted `AHVA` version-1 compact automaton prototype contains 922 states,
921 transitions, 402 terminal surfaces, and 407 terminal analyses. It is
39,394 installed bytes, 12,006 deterministic gzip bytes, and has SHA-256
`a72950884fdfa6c76cd7a9f6e37e05bf7ca2f51b4ddd67e6386f17433feee963`.
It remains a test-only Phase 7 artifact; no verb suggestions are shipped yet.
The three-option benchmark, schema, fallback policy, raw commands, and
licensing obligations are recorded in
`docs/adr/amharic-verb-morphology-artifact.md`.

## Phase 8/9 production artifact

Phase 8 promotes the selected representation to the version-2 production
artifact `src/main/assets/amharic_verbs.ahva`. The declared verb slice,
stable identity scheme, exact transformation commands, fallback behavior, and
unsupported branches are recorded in `PHASE8_VERBS.md`. The paired manifest
pins its byte length, SHA-256, HornMorpho version, and upstream commit. The
gzipped oracle corpus and JVM golden subset stay outside product assets.

Phase 9's local morphology identity, bounded analyzer-backed correction,
privacy policy, reproducibility checks, release tests, and pending fluent
review are recorded in `PHASE9_RELEASE.md`. `hornmorpho_LICENSE.txt` continues
to ship with Addiyon, while TextRevamp remains English-only and contains no
HornMorpho artifact or license asset.

`tools/build_amharic_dict.py` transforms this snapshot into two deterministic
inputs:

- `amharic_lexemes.dat`: 18,867 unique HornMorpho lexical records, including
  1,832 verb-root records retained for the morphology generator
- `amharic_words.dat`: 18,251 displayable, homoglyph-folded base lemmas used by
  the current completion runtime

`tools/build_ngrams.py` separately creates `amharic_ngrams.dat`, the Phase 6
sparse, morphology-gated bigram model. Rebuilding the base lexicon does not
erase it, and its productive surface vocabulary is stored separately from
base `words`.

The transformation removes HornMorpho's internal slash notation only for the
displayable completion list. The original notation and grammatical features
remain in `amharic_lexemes.dat` and in these source files.

## Reproducible nominal oracle

The build-time oracle under `tools/hornmorpho/` uses the upstream 5.3.6 wheel
and Amharic artifact from the same commit. Their pinned SHA-256 digests are:

- `hornmorpho-5.3.6-py3-none-any.whl`:
  `e1b0e4ed616d6640e57a440ce63051366c2aba4b29149893bf450fb6b5576fa7`
- `src/hm/languages/a.tgz`:
  `3274f71da10263acf2bcea54023e4d1088760ea07fedeb7309291d0e2c11e95c`

`bootstrap_oracle.py` verifies the archive before extracting it into an
isolated environment. `generate_nominal_oracle.py` retains HornMorpho's
original surface and analysis and adds a normalized key only for comparison.
`generate_nominal_golden.py` rebuilds the JVM fixture and accepts a positive
case only when HornMorpho returns a matching nominal analysis.

Phase 4's supported cascade, rule ordering, morphophonemics, and deliberately
excluded branches are recorded in `NOMINAL_RUNTIME.md`.
`generate_nominal_phase4.py` rebuilds the practical-parity fixture from the
pinned analyzer. The checked-in fixture contains 1,060 cases across 42 source
lexemes: 948 supported forms, 17 analyzer-recognized exclusions with reasons,
and 95 negative forms. Its SHA-256 is
`f1f297edfc0ef6b3752b8b5187433ee2a6eb712cc039a0373753397c6dbcdb54`.
The generator was run twice against HornMorpho 5.3.6 with byte-identical
output.

## Phase 5 surface-ranking statistics

`tools/hornmorpho/build_nominal_surface_stats.py` intersects the archived
legacy corpus frequencies with the analyzer-supported Phase 3 and Phase 4
oracle surfaces, removes base lemmas, prunes counts below two, and caps the
result at 2,048 rows. The checked-in
`language/amharic/src/dictionary/amharic_surface_stats.dat` currently contains
481 rows, is 2,477 bytes compressed, and has SHA-256
`25491607220c2a6ffd622e6903f835c741f3a46f9c448e685990cd9c919ae331`.

These counts are ranking evidence only. Runtime morphology must first validate
and generate a candidate before looking up its surface frequency; the table
cannot establish lexical or morphological validity. The archived legacy
corpus is a developer-side generator input only and is neither a Gradle input
nor an APK asset. The generated gzip and schema 10 SQLite database were each
regenerated twice with byte-identical output.

## Phase 6 morphology-gated prediction

The prediction builder admits a token only when it is an exact base lexeme or
an analyzer-recognized permitted surface from the pinned Phase 3/4 fixtures.
The checked-in baseline uses only project-authored mini-corpus and held-out
fixtures; historical local corpora and the retired n-gram model are excluded
because their acquisition and redistribution provenance is not clear.

The active model contains 40 vocabulary entries, 33 bigram contexts, 50
successors, and no trigrams. Its 454-byte deterministic gzip has SHA-256
`6f189e93939f6a2501cea58db3596bb962659b195f09b2e0af10387ee7973732`.
Held-out top-3 accuracy is 11/14 (`0.785714`) versus zero hits for the empty
baseline. Full provenance, artifact checksums, limitations, and the pending
fluent-speaker expansion gate are in `PHASE6_PREDICTION.md`.
