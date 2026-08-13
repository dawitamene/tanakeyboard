# Addiyon nominal runtime subset

The Android runtime follows the nominal cascade pinned in `cas/n.cas`:

1. optional adposition;
2. optional distributive `እየ` or collective `እነ` marker;
3. the HornMorpho lexeme and its lexical restrictions;
4. ordinary `-ኦች` or alternate-class `-አን` number marking;
5. human `-እየ/-እዬ/-እዮ` marking;
6. determiner or possessive marking;
7. accusative marking;
8. the empty nominal copula transition;
9. conjunctive/adverbial suffixes;
10. postposition `ጋ`;
11. boundary rewrites from `n_aff_bound.fst`;
12. canonical display selection before accepted orthographic variants.

The runtime supports indexed kinds 0–3. Ordinary nouns and adjectives honor
the lexical `pl`, `def`, `p`, `adp`, `gen`, `dis`, `col`, `delinit`, gender,
and human restrictions. Person names accept ordinary adpositions,
accusative, collective context, conjunctives, and `ጋ`; place names accept
ordinary adpositions, accusative, conjunctives, and `ጋ`. Neither proper-noun
class receives ordinary plural, determiner, or possessive expansion.

Generation and analysis use the same bounded transition graph. Forward
search keeps only states compatible with the typed prefix and stops at a
ranking buffer. Reverse lookup walks the surface inverses of those same
transitions and feeds indexed equality and prefix-range SQLite queries.
Ambiguous analyses remain distinct until display candidates are collapsed by
their normalized surface.

The supported boundary behavior includes:

- contracted and uncontracted initial-`አ` adposition forms;
- canonical and selected accepted ordinary-plural boundaries;
- alternate `-አን` stems, including `-ዊያን`;
- human suffixes and the third-masculine possessive interaction;
- all possessive persons, plural persons, and formal second-person variants;
- masculine, feminine, and `-ኢቱ` determiners;
- bare and adposition-prefixed distributive/collective forms;
- canonical accusative, conjunctive, and `ጋ` ordering.

The following pinned HornMorpho branches are deliberately excluded:

- pre-inflected lexical records, because they are not productive bases;
- overt nominal copular spellings, because the pinned `n.mtx` nominal copula
  transition is empty and overt copulas belong to the separate copula
  cascade;
- raw boundary/gemination notation and epenthetic diagnostic realizations
  such as `ሰውእየ` and `ቤተም`, because they are analyzer compatibility forms
  rather than preferred keyboard display forms;
- pronoun, determiner, numeral, and verbal-noun branches, which need their own
  lexical and ranking policies;
- conjunction-plus-postposition combinations rejected by HornMorpho's
  `prior` constraints;
- guesser-only analyses, which cannot establish lexical validity.

The checked-in Phase 4 corpus records every accepted case, negative near
miss, and explicit exclusion used for parity measurement.
