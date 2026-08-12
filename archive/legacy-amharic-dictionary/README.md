# Legacy Amharic corpus dictionary

These files are retained only as an archive. Nothing under this directory is a
Gradle input or is packaged in an APK.

- `corpus_surface_words.dat`: the former approximately 254,000-row surface-form
  dictionary
- `corpus_ngrams.dat`: the former prediction model tied to that dictionary
- `am_et_hunspell_words.txt.gz`, `term_frequency_yididiyan.txt.gz`, and
  `wordlist_abdulmunim.txt.gz`: inputs used by the retired corpus word-list
  builder

The active Amharic baseline lives under `language/amharic/hornmorpho` and is
generated with `tools/build_amharic_dict.py`.
