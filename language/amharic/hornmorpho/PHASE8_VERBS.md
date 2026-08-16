# Production verb runtime

Addiyon ships the `AHRF` version-1 weighted HornMorpho runtime at
`src/main/assets/amharic_verbs.ahrf`. It contains compiled rules, constraints,
all 1,832 base roots, and base-root ranking evidence. It contains no generated
or predetermined inflected surfaces.

HornMorpho 5.3.6 at commit
`7e3d93af760e27ea6dbc3b7a078d2d9c3335f618` is the pinned compiler and
semantic oracle. Python and HornMorpho are not Android dependencies. The
Kotlin runtime executes feature unification and surface traversal on device.

The current artifact contains 89,365 states, 331,733 transitions, 27,531 rule
weights, 237 compressed constraint pages, and zero generated surfaces. It is
memory-mapped, keeps at most four inflated pages, bounds completion traversal,
and caches 128 recent reading/limit results.

Reproduce it with the full command in
`docs/adr/amharic-verb-runtime-fst.md`. The paired manifest records the final
length, SHA-256, counts, representation, and `generatedSurfaces=0` contract.
Any validation failure disables verb suggestions without disabling the base
dictionary, nominal rules, transliteration, or prediction.
