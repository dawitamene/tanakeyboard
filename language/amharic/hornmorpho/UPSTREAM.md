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

Every control file above is an unmodified copy from commit
`7e3d93af760e27ea6dbc3b7a078d2d9c3335f618`. The cascade files establish
that nominal analysis composes `misc`, `n_aff_bound`, and `n.mtx` in that
order, with the corresponding `M` files used for multiword analysis. The
boundary string sets are defined in `a.lg`; no rule is inferred from a newer
upstream branch.

`tools/build_amharic_dict.py` transforms this snapshot into three deterministic
inputs:

- `amharic_lexemes.dat`: 18,867 unique HornMorpho lexical records, including
  1,832 verb-root records retained for the morphology generator
- `amharic_words.dat`: 18,251 displayable, homoglyph-folded base lemmas used by
  the current completion runtime
- `amharic_ngrams.dat`: an intentionally empty model until a clean prediction
  corpus is rebuilt against the new lexeme set

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
