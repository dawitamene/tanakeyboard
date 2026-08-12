# HornMorpho nominal oracle

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
