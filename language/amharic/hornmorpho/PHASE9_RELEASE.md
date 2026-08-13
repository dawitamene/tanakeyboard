# Phase 9 adaptive morphology and release gates

Personal dictionary encoding version 3 retains the exact committed surface,
count, recency order, and optional stable lemma/analysis IDs. Version 2 and the
legacy unversioned format migrate without inventing metadata. Storage remains
in existing on-device preferences. Password fields,
`TYPE_TEXT_FLAG_NO_SUGGESTIONS`, and
`IME_FLAG_NO_PERSONALIZED_LEARNING` disable learning.

Verb correction traverses the same analyzer-derived automaton with bounded
weighted edit distance. Edit distance can rank a terminal but cannot authorize
a new string: every returned correction has an exact automaton terminal.
Low-RAM mode retains the existing complete-pipeline fuzzy disablement, and all
fuzzy candidates stay below exact, attested, and generated completion tiers.

Release gates include:

- exact, prefix, irregular/light, negative, ambiguity/deduplication,
  corruption, version, checksum, and weighted-fuzzy tests;
- JVM exact/prefix and end-to-end nominal/verb latency tests;
- `TanaLowRam` connected IME publication coverage when that AVD is present;
- APK presence checks for the artifact, manifest, and GPLv3 license;
- APK absence checks for oracle corpora, dictionary build inputs, and
  HornMorpho assets in TextRevamp;
- byte-identical offline artifact regeneration and manifest validation;
- full JVM, product boundary, assemble, and Addiyon install gates.

The ordinary connected suite measures warm final-key publication, a
one-character high-fanout prefix, rapid delete/retype, and language toggles.
The 10-minute heap/PSS gate is opt-in so normal instrumentation remains fast:

```sh
/Users/dev/code/addiyon-keyboard/gradlew :apps:addiyon:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.addiyon.keyboard.AddiyonLanguageImeTest#sustainedMorphologyTypingKeepsMemoryBounded \
  -Pandroid.testInstrumentationRunnerArguments.phase9SoakMillis=600000
```

The release soak passed on the Android 15 `TanaLowRam` AVD on 2026-08-13.
It completed 1,606 morphology typing iterations in 600,000 ms. After an
explicit GC, used heap changed from 7,457,856 to 7,461,984 bytes and PSS
changed from 205,794,304 to 204,076,032 bytes. Sampled peaks were 21,937,280
bytes of used heap and 219,596,800 bytes of PSS. The connected warm-publication
gate also passed with p95 69.258334 ms, maximum 82.454167 ms, and the
one-character prefix at 20.349375 ms.

The generated review sheet is
`tools/hornmorpho/fixtures/amharic_phase9_review.tsv`. It contains 210 rows
across seven source groups and has SHA-256
`14a7666f79d44939768bd74b60e8846554fa190f6c7bc4884763598247750f4d`.
A fluent Amharic reviewer
must complete the verdict/canonicality columns before release approval. This
repository must not claim that external linguistic or GPL distribution review
has happened until the responsible reviewers sign the corresponding fields.

Release-owner sign-off remains required for the GPL source-offer/distribution
obligations. Automated implementation and verification do not constitute that
legal review.
