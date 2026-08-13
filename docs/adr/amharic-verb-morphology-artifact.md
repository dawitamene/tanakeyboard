# ADR: Amharic verb morphology artifact

- Status: accepted and implemented in Phase 8
- Date: 2026-08-13
- Review: automated architecture, format, reproducibility, and budget gates passed; fluent Amharic review remains a Phase 9 release gate
- Decision owner: Addiyon Keyboard
- Artifact schema: `AHVA` version 1 prototype; version 2 production

## Context

HornMorpho 5.3.6 preserves 1,832 Amharic verb-root records and a compiled
root-and-pattern cascade. Extending Addiyon's noun suffix rules would lose the
verb cascade's tense/aspect/mood, agreement, polarity, derivation, irregular,
and light-verb behavior. Phase 7 therefore evaluates mechanically derived
representations before any verb suggestions are added to the Android runtime.

The pinned oracle is HornMorpho commit
`7e3d93af760e27ea6dbc3b7a078d2d9c3335f618`. The 407-row spike corpus is an
oracle round-trip fixture: HornMorpho must generate each ordinary form and
then analyze it back to the expected root. Light verbs are analyzed in
multiword mode. The corpus contains 100 regular roots, 12 irregular roots,
and 20 light-verb lexemes across perfective third-person masculine singular,
imperfective third-person masculine singular, and imperative second-person
masculine singular.

## Options

### A. Compact compiled runtime automaton

Compile oracle-approved forms offline into a minimized deterministic acyclic
automaton. The checked-in `AHVA` prototype supports exact traversal, prefix
enumeration, terminal surface strings, stable root IDs, feature IDs, source
class, lexeme identity, and root identity. Tables are offset-based and
big-endian so a Kotlin reader can use a mapped buffer without materializing
the graph.

### B. Pruned generated-surface DAFSA

Keep a single surface for each lexeme and feature combination before applying
the same minimization. This reduces the prototype by only 971 installed bytes
while dropping 11 oracle rows and five distinct terminal surfaces. Its 97.3%
coverage is an avoidable loss, particularly because generated alternatives
can encode legitimate orthographic or morphological ambiguity.

### C. Mechanically derived Kotlin rule interpreter

Parse the vendored cascade, morphotactics, root declarations, and transition
files into a dependency graph. The feasibility parser resolves 34 source
files, 20 cascade components, 268 explicit transition lines, and six
lexical declarations. It does not implement HornMorpho's unification engine,
therefore it has no valid exact or prefix execution path and zero measured
oracle coverage. An informal Kotlin grammar is not an acceptable substitute.

## Benchmark

Benchmarks ran on the same 407 oracle rows. Latencies are reference Python
reader measurements on the development Mac and are a representation gate,
not a substitute for the Android benchmark required in Phase 8.

| Measure | A: compact automaton | B: pruned DAFSA | C: rule graph |
|---|---:|---:|---:|
| Installed bytes | 39,394 | 38,423 | 242,349 source bytes |
| Deterministic gzip bytes | 12,006 | 11,685 | 34,631 |
| States / transitions | 922 / 921 | 896 / 895 | 268 parsed transition lines |
| Terminal surfaces / analyses | 402 / 407 | 391 / 396 | not executable |
| Cold-open p50 / p95 | 0.034 / 0.042 ms | 0.032 / 0.043 ms | not executable |
| Exact p50 / p95 | 0.0036 / 0.0052 ms | 0.0036 / 0.0055 ms | not executable |
| Prefix p50 / p95 | 0.0052 / 0.0141 ms | 0.0053 / 0.0145 ms | not executable |
| Reference peak allocation | 171,320 bytes | 170,285 bytes | not measured |
| Oracle-row coverage | 100% | 97.3% | 0% |
| Byte-identical rebuild | yes | yes | source snapshot is pinned |
| Migration complexity | low, append versioned reader | low, same format | high, unification semantics |

Option A is 0.38% of the 3 MiB compressed budget, its reference reader stays
well below the 5 MiB eager-heap budget, and lookup percentiles are far below
the 10 ms exact and 25 ms prefix gates.

## Decision

Use Option A, the compact compiled runtime automaton, for Phase 8. Retain
Option B only as a measured comparison artifact and reject Option C unless a
future compiler can export HornMorpho's complete unification transition graph
mechanically and prove oracle equivalence.

The version-1 prototype remains test-only. Phase 8 implements the production
version-2 artifact and a fail-closed Kotlin reader behind the Amharic
suggestion boundary. The shipped slice and its deliberate limitations are
recorded in `language/amharic/hornmorpho/PHASE8_VERBS.md`; linguistic release
approval remains external to this architecture decision.

## Versioning and validation

Both `AHVA` versions use:

1. a 48-byte header with magic, schema version, counts, table offsets, and a
   CRC-32 of every byte after the header;
2. fixed-width state and transition tables with sorted UTF-16 code-unit labels;
3. terminal ranges containing one display surface and one or more analyses;
4. fixed-width analysis records pointing into a shared UTF-8 string pool.

Production version 2 extends each analysis with HornMorpho root-frequency
evidence and replaces prototype sequence IDs with collision-checked IDs from
the first 32 bits of SHA-256 over lexeme identity. Feature IDs use the first
16 bits of SHA-256 over the sorted oracle feature signature.

A reader accepts only known magic and schema versions, checks the checksum and
all derived offsets, bounds-checks counts and string ranges, and fails closed.
Schema-breaking changes increment the version and ship a parallel reader;
older artifacts are never interpreted as the new schema. Stable root IDs are
assigned by sorted lexeme identity during deterministic compilation.

## Failure and fallback behavior

If the production artifact is absent, has an unsupported version, fails its
checksum, has invalid bounds, or cannot be mapped, the verb provider returns
no verb candidates. Base lexical completion, nominal morphology, contextual
prediction, and transliteration continue unchanged. Runtime code must never
fall back to an unvalidated handcrafted verb generator.

## Licensing and provenance

The source cascade, oracle corpus, and derived automata remain GPL-3.0-derived
HornMorpho material. Keep the pinned upstream commit, HornMorpho version,
archive and wheel checksums, unmodified sources, transformation commands, and
`language/amharic/hornmorpho/LICENSE.txt` together. The Amharic module must
continue packaging `hornmorpho_LICENSE.txt`. Rebuilding from a newer upstream
revision requires a new provenance record, regenerated corpus and artifacts,
byte-reproducibility check, benchmark, and ADR review.

## Reproduction and raw commands

Run from any directory with the full workspace paths:

```sh
/usr/bin/python3 /Users/dev/code/addiyon-keyboard/tools/hornmorpho/bootstrap_oracle.py --venv /private/tmp/addiyon-hornmorpho-phase7 --archive /private/tmp/addiyon-hornmorpho-a.tgz
/private/tmp/addiyon-hornmorpho-phase7/bin/python /Users/dev/code/addiyon-keyboard/tools/hornmorpho/verb_spike.py oracle --repo /Users/dev/code/addiyon-keyboard
/opt/homebrew/bin/python3 /Users/dev/code/addiyon-keyboard/tools/hornmorpho/verb_spike.py artifacts --repo /Users/dev/code/addiyon-keyboard
/opt/homebrew/bin/python3 /Users/dev/code/addiyon-keyboard/tools/hornmorpho/verb_spike.py verify --repo /Users/dev/code/addiyon-keyboard
/opt/homebrew/bin/python3 /Users/dev/code/addiyon-keyboard/tools/hornmorpho/verb_spike.py benchmark --repo /Users/dev/code/addiyon-keyboard
/private/tmp/addiyon-hornmorpho-phase7/bin/python /Users/dev/code/addiyon-keyboard/tools/hornmorpho/verb_spike.py production-oracle --repo /Users/dev/code/addiyon-keyboard
/opt/homebrew/bin/python3 /Users/dev/code/addiyon-keyboard/tools/hornmorpho/verb_spike.py production-artifact --repo /Users/dev/code/addiyon-keyboard
/opt/homebrew/bin/python3 /Users/dev/code/addiyon-keyboard/tools/hornmorpho/verb_spike.py verify-production --repo /Users/dev/code/addiyon-keyboard
/Users/dev/code/addiyon-keyboard/gradlew :language:amharic:testDebugUnitTest --tests "com.addiyon.keyboard.suggestion.VerbAutomatonPrototypeTest"
```

The corpus SHA-256 is
`9d48b215d264ade3d2bb7c67a9e868ddc31586f9c1cc4f98c2f22971bb789110`.
The selected artifact SHA-256 is
`a72950884fdfa6c76cd7a9f6e37e05bf7ca2f51b4ddd67e6386f17433feee963`.
Detailed raw metrics are checked in at
`tools/hornmorpho/verb_spike_metrics.json`.

The production artifact SHA-256 is
`8a1f683873f9972d6444f4b969a1acb3af0fc86d4f1d5e945601bbcf94456927`.
It contains 176 roots, 7,666 terminal surfaces, and 7,931 analyses in 770,172
installed bytes or 194,597 deterministic gzip bytes. Production counts and
budgets are checked in at `tools/hornmorpho/verb_phase8_metrics.json`.
