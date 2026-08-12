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
