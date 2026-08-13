# Phase 6 Amharic prediction baseline

## Scope and corpus decision

The enabled Phase 6 baseline is deliberately small. It uses only the
project-authored UTF-8 fixtures checked into this repository:

- `tools/hornmorpho/fixtures/amharic_phase6_corpus.txt`
  - created: 2026-08-13
  - 31 sentence or phrase lines
  - SHA-256: `2ced671710b0bbea85951cc9b6de26be5b173a9fa2aac77acc8b80dbb3859847`
- `tools/hornmorpho/fixtures/amharic_phase6_held_out.txt`
  - created: 2026-08-13
  - 7 held-out lines chosen before model tuning
  - SHA-256: `94d9b8053745c3f106ca3603ad6ad069144403d66cad290b700cf9239de8c7ee`

These fixtures contain no private user text and no third-party corpus content.
They are included as project test data. The repository currently has no
top-level license, so this statement records provenance but does not imply an
external redistribution grant.

The large historical corpus files present in some developer workspaces and
the retired model under `archive/legacy-amharic-dictionary` are not inputs.
Their acquisition and redistribution provenance is not sufficiently clear for
a production rebuild. They must not be enabled silently.

## Build and validity gate

The baseline is regenerated from the workspace root with:

```sh
python3 /Users/dev/code/addiyon-keyboard/tools/build_ngrams.py \
  --bigram-min-context 1 \
  --bigram-min-succ 1 \
  --k-bigram 8 \
  --no-trigrams \
  --held-out /Users/dev/code/addiyon-keyboard/tools/hornmorpho/fixtures/amharic_phase6_held_out.txt \
  /Users/dev/code/addiyon-keyboard/tools/hornmorpho/fixtures/amharic_phase6_corpus.txt
```

The low count thresholds are appropriate only for this tiny, authored
fixture. A larger approved corpus should use the conservative defaults and
must pass the same audit and review gates before replacing the baseline.

Each corpus token is normalized and admitted only when it is either:

1. an exact normalized base entry in `amharic_words.dat`; or
2. an analyzer-recognized, permitted surface in the pinned Phase 3 or Phase 4
   HornMorpho oracle fixture.

Unknown tokens break adjacency. Punctuation, digits, Latin text, and mixed
tokens remain hard boundaries. The build audit preserves canonical display,
normalized key, preferred unambiguous lemma and source analysis, ambiguity
count, validity source, and Phase 5 surface frequency. Build-time SQLite validation independently
requires the audit and binary vocabulary to contain exactly the same normalized
keys and verifies every audit row's validity source and metadata shape.

Python and HornMorpho are developer-side generation and verification tools.
They are not packaged, started, or interpreted by Android. The app uses pure
Kotlin and the generated read-only SQLite database.

## Baseline artifact and quality

The checked-in model contains:

- 40 surface vocabulary entries;
- 33 bigram contexts and 50 successor rows;
- zero trigrams;
- 988 raw binary bytes and 454 deterministic gzip bytes;
- model SHA-256:
  `6f189e93939f6a2501cea58db3596bb962659b195f09b2e0af10387ee7973732`;
- vocabulary audit SHA-256:
  `bae9be7a9978a4042ba242db60417755b0ec2253c71b227b12e65905509b6cb3`;
- quality report SHA-256:
  `c334d54fcc4037de10ed5c2ca6b3986a4393e0c2e196c0de8a1103ab2a35c111`.
- fluent-review TSV SHA-256:
  `b0f0ef120bd61858c30869befc281da5ab677f7a3bdbf93d0a27210196a5f661`.

The preselected metric is held-out next-surface top-3 accuracy. On 14 eligible
transitions, the model hits 11 (`0.785714`); the empty-model baseline hits
zero. This fixture result is a regression signal, not an estimate of
real-world keyboard quality.

Schema 11 stores prediction vocabulary separately in `ngram_vocab`. This is a
critical validity boundary: productive forms such as `ቤቶች`, `መምህራን`, and
`የሰው` can be predicted without being added to the base `words` dictionary.
The generated Amharic database is 2,506,752 bytes.

## Review status and expansion gate

Automated coverage includes common noun/adjective sequences, inflected
surfaces, punctuation and unknown boundaries, homoglyph folding with canonical
display, invalid `የነው`, legacy-garbage sentinels, sparse bigram behavior,
indexed lookup plans, and warm query latency.

A fluent Amharic review of the top 500 contexts and representative top-10
successors is still required before importing a broader production corpus or
enabling trigrams. The current project-authored fixture was reviewed during
construction, and it has only 33 contexts, so every context is directly
covered by this initial review plus the automated assertions. Corpus expansion
is a product decision and must add corpus name, version/date, license,
acquisition command, checksum, cleanup procedure, held-out result, context
review record, and new size and latency measurements to this document.
