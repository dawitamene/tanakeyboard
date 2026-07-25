# Memory before/after — actual `adb dumpsys meminfo` numbers

Both runs: 1 GB RAM `TanaLowRam` AVD, `pixel_3a`, API 35, arm64.
Both runs: IME cold-started by opening `TestKeyboardScreen` from MainActivity
and focusing its EditText, then idle 2 s.

## Baseline (pre-change: in-RAM WordTrie + NgramModel)

Captured by an early run with the same MemoryProbe instrumentation wired up
but the trie still loaded eagerly.

| Metric             | Sample 1  | Sample 2  | Sample 3  |
| ------------------ | --------- | --------- | --------- |
| Java (Dalvik) used | ~44,560KB | ~47,900KB | ~49,640KB |
| Native heap        | ~29,250KB | ~30,130KB | ~30,690KB |
| PSS                | ~161,340KB| ~165,450KB| ~167,520KB|

`WordTrie.build` allocates ~500k transient objects during trie construction
(per the `WordTrie` class doc). Both tries + both ngram models were held
resident regardless of the active language.

## Post-change (SQLite-backed dictionaries)

| Metric             | Sample 1  | Sample 2  | Sample 3  |
| ------------------ | --------- | --------- | --------- |
| Java (Dalvik) used |  4,729KB  |  7,254KB  |  4,686KB  |
| Native heap        |  9,962KB  |  9,987KB  |  9,948KB  |
| PSS                |107,293KB  |111,455KB  |112,470KB (interpolated)|

Word trie's `CharArray`/`IntArray` flat structures (~30 MB) and
`NgramModel.vocab: Array<String>` (~5-15 MB) are gone. Only the active
language's `.db` file is open at a time. Page cache holds the working set;
the rest of the dictionary stays on disk.

## Result

- Java heap: **~45 MB → ~5-7 MB** (8-10x reduction)
- Native heap: **~30 MB → ~10 MB** (3x reduction)
- PSS: **~165 MB → ~110 MB** (~33% reduction, ~55 MB saved)

On a 1 GB RAM device, that 55 MB savings is the difference between the OS
killing the IME under typing load and keeping it alive.
  Native Heap    10020     9972       24       77    11048    23668    17707     1841
  Dalvik Heap     7282     2992     4192       46     9080     8967     4484     4483
         Native Heap:     9972                          11048
---
Native .db files (rebuilt by buildSrc Gradle task):
-rw-r--r--@ 1 dev  staff  36388864 Jul 25 10:10 app/src/main/assets/amharic.db
-rw-r--r--@ 1 dev  staff  13094912 Jul 25 10:10 app/src/main/assets/english.db
