# HornMorpho build-time oracle

These tools reproduce build-time analysis and generation with HornMorpho
5.3.6 at commit `7e3d93af760e27ea6dbc3b7a078d2d9c3335f618`. They are developer tools,
not Android runtime dependencies.

`bootstrap_oracle.py` creates an isolated virtual environment, installs the
pinned wheel and dependencies, downloads the pinned Amharic artifact, verifies
its SHA-256, and installs it into that environment. Use a Python 3.9+ build
that includes `tkinter`; HornMorpho imports its GUI module even for headless
analysis.

```sh
python3 /Users/dev/code/addiyon-keyboard/tools/hornmorpho/bootstrap_oracle.py \
  --venv /private/tmp/addiyon-hornmorpho-oracle
```

For a previously downloaded archive, pass `--archive /path/to/a.tgz`.

`generate_nominal_oracle.py` accepts deterministic JSONL requests and emits
deterministic JSONL results. Analysis requests use `surface`; generation
requests use `lemma` and either a HornMorpho feature block or a list of
feature atoms.

```json
{"id":"house-analysis","operation":"analyze","surface":"የቤታችን"}
{"id":"house-plural","operation":"generate","lemma":"ቤት","features":["+pl"]}
```

```sh
/private/tmp/addiyon-hornmorpho-oracle/bin/python \
  /Users/dev/code/addiyon-keyboard/tools/hornmorpho/generate_nominal_oracle.py \
  --input /path/to/requests.jsonl \
  --output /path/to/results.jsonl
```

The output retains HornMorpho's original surface, segmentation, roots, and
analyses while adding the same normalized comparison key used by Addiyon.
`compare_nominal_runtime.py` compares a runtime result JSONL file with the
checked-in nominal golden TSV by stable case id.

`generate_nominal_phase4.py` analyzes the practical Phase 4 inventory and
rebuilds the checked-in positive, explicit-exclusion, and negative fixture:

```sh
/private/tmp/addiyon-hornmorpho-oracle/bin/python \
  /Users/dev/code/addiyon-keyboard/tools/hornmorpho/generate_nominal_phase4.py
```

The default output is
`language/amharic/src/test/resources/hornmorpho_nominal_phase4.tsv`. Python and
HornMorpho are used only by this developer-side verification workflow; the
Android runtime uses the generated SQLite lexicon and pure Kotlin rule graph.

`build_nominal_surface_stats.py` builds the bounded Phase 5 ranking-statistics
asset from the analyzer-supported Phase 3 and Phase 4 oracle forms. Corpus
counts come from the archived legacy surface-frequency file, but a key is
eligible only after the pinned oracle has validated it and base lemmas are
excluded:

```sh
python3 /Users/dev/code/addiyon-keyboard/tools/hornmorpho/build_nominal_surface_stats.py
```

The output is
`language/amharic/src/dictionary/amharic_surface_stats.dat`. It is capped at
2,048 rows, prunes frequencies below two, and is written as deterministic gzip
with `mtime=0`. It contains ranking evidence only: it is not a dictionary and
cannot make a surface valid. Only this compact generated asset is an Android
build input; the archived corpus is never packaged or read by the app.

`../build_ngrams.py` uses the pinned Phase 3 and Phase 4 oracle fixtures as a
developer-side validity gate for Phase 6 prediction surfaces. The checked-in
project-authored training and held-out inputs are:

- `fixtures/amharic_phase6_corpus.txt`
- `fixtures/amharic_phase6_held_out.txt`

The build emits a sparse bigram model plus
`language/amharic/src/dictionary/amharic_ngram_audit.tsv` and
`amharic_ngram_quality.json`, plus `amharic_ngram_review.tsv` for the fluent
context review. See
`language/amharic/hornmorpho/PHASE6_PREDICTION.md` for the exact command,
checksums, provenance decision, quality metric, and fluent-review gate. This
workflow remains developer-only; Android loads a generated SQLite database and
does not run Python or HornMorpho.

`verb_spike.py` implements the Phase 7 verb architecture gate. The `oracle`
command selects 100 regular roots, 12 irregular roots, and 20 light-verb
lexemes, then requires every selected feature row to survive a HornMorpho
generate-and-analyze round trip. `artifacts` writes the full-coverage `AHVA`
version-1 automaton and the pruned Option B comparison. `verify` requires a
byte-identical rebuild and all three source classes. `benchmark` records the
same-workload size, graph, cold-open, exact, prefix, allocation, and coverage
measurements in `verb_spike_metrics.json`.

```sh
/private/tmp/addiyon-hornmorpho-oracle/bin/python \
  /Users/dev/code/addiyon-keyboard/tools/hornmorpho/verb_spike.py oracle
/opt/homebrew/bin/python3 \
  /Users/dev/code/addiyon-keyboard/tools/hornmorpho/verb_spike.py artifacts
/opt/homebrew/bin/python3 \
  /Users/dev/code/addiyon-keyboard/tools/hornmorpho/verb_spike.py verify
/opt/homebrew/bin/python3 \
  /Users/dev/code/addiyon-keyboard/tools/hornmorpho/verb_spike.py benchmark
```

The selected artifact is a test resource during Phase 7. The Android app does
not load it until Phase 8 provides the production feature slice and runtime
integration. See `docs/adr/amharic-verb-morphology-artifact.md` for the
decision, schema, measurements, and fallback behavior.

`verb_spike.py production-oracle` expands the accepted representation into
the reviewed Phase 8 feature slice using the pinned generator. It writes a
deterministic gzipped developer corpus and a bounded JVM golden fixture.
`production-artifact` emits the shipped version-2 automaton, manifest, and
metrics; `verify-production` rebuilds it in memory and checks byte identity,
manifest integrity, class/feature coverage, and systematic negative exact
lookups. `phase9-review` creates the grouped fluent-review worksheet.

```sh
/private/tmp/addiyon-hornmorpho-oracle/bin/python \
  /Users/dev/code/addiyon-keyboard/tools/hornmorpho/verb_spike.py production-oracle
/opt/homebrew/bin/python3 \
  /Users/dev/code/addiyon-keyboard/tools/hornmorpho/verb_spike.py production-artifact
/opt/homebrew/bin/python3 \
  /Users/dev/code/addiyon-keyboard/tools/hornmorpho/verb_spike.py verify-production
/opt/homebrew/bin/python3 \
  /Users/dev/code/addiyon-keyboard/tools/hornmorpho/verb_spike.py phase9-review
```

Only `amharic_verbs.ahva` and its integrity manifest are Android runtime
inputs. The Python environment, oracle corpora, review sheet, and HornMorpho
transducer are developer-side inputs and are never opened by the app.
