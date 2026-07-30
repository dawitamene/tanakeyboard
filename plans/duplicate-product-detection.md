# Duplicate product listing detection

> **Note:** this plan is not about the keyboard. It landed in `plans/` because that's where plans
> go by convention; move it to the marketplace repo when we start.

## 1. Problem

Some stores re-post the same product repeatedly (to game recency ranking / fill the feed). The
re-posts have:

- the **same images** (possibly re-encoded, resized, re-cropped, watermarked)
- the **same description** with a tiny edit
- the **same title**
- a **close but not identical price**

We want: at post time, decide whether the new listing duplicates an existing one. Compare only
against listings from the **last 6 months** — re-posting after that is allowed. Combine per-signal
similarities into a confidence, flag at **> 0.70**.

Hard constraints: **no paid APIs, no GPUs, no embedding service, no measurable latency cost.**

## 2. The core idea

The naive framing — "compare the new product against every product from the last 6 months" — is the
whole problem. Everything below exists to avoid it.

Split into two stages:

| Stage | What it does | Cost |
|---|---|---|
| **1. Blocking** | Turn "find similar things" into **exact-equality index lookups**. Returns a few hundred candidates. | ~20 index probes, sub-millisecond each |
| **2. Scoring** | Full pairwise similarity + fusion, but only on those candidates. | ≤ 400 × ~50 µs |

Stage 1 is where the cleverness lives. The trick that makes it work for images — and, reused
verbatim, for descriptions — is **multi-index hashing (banding)**.

## 3. Images

### 3.1 Tier 0 — exact bytes (free, zero false positives)

Hash the **decoded, normalized pixels** (not the file bytes — re-encoding changes those): decode →
resize to a fixed 256×256 → grayscale → `xxh3_64`. One index lookup. Catches straight re-uploads,
which is probably the majority of the traffic. Do this first and short-circuit.

### 3.2 Tier 1 — perceptual hash

**256-bit pHash:**

1. Decode, auto-crop uniform borders (scan rows/cols for constant color — defeats "add a border"), grayscale.
2. Resize to 64×64 (box filter).
3. 2D DCT-II.
4. Take the top-left 16×16 coefficient block (the low-frequency content).
5. Median over the 255 non-DC coefficients; `bit[i] = coeff[i] > median`. Force the DC bit to 0.

Result: 32 bytes per image, computed once at upload. ~2–4 ms on CPU — and you're already decoding
and resizing for thumbnails, so it's nearly free if you hook into that pass.

pHash is robust to: re-encoding, JPEG quality changes, resizing, small brightness/contrast shifts,
light watermarking. It is *not* robust to heavy cropping or rotation (see §3.5).

### 3.3 The blocking trick — banded lookup (pigeonhole)

We need "find all hashes within Hamming distance *r*". That's normally a linear scan. Instead:

> Split an *m*-bit hash into *p* bands. If two hashes differ in ≤ *r* bits and **p > r**, then by the
> pigeonhole principle **at least one band must be bit-identical** — you can't spread *r* errors
> across more than *r* bands.

So: store one row per (band index, band value), query with 16 equality lookups, union the results.
**Exact recall for distance ≤ r, using nothing but a B-tree.**

Choosing the configuration is the whole design decision:

| hash bits | bands | bits/band | guaranteed recall ≤ | buckets/band | rows/band @1M images | candidates |
|---|---|---|---|---|---|---|
| 64 | 4 | 16 | 3 | 65,536 | ~15 | ~60 |
| 64 | 8 | 8 | **7** | 256 | ~3,900 | **~31,000** ✗ |
| 128 | 8 | 16 | 7 | 65,536 | ~15 | ~120 |
| **256** | **16** | **16** | **15** | 65,536 | ~15 | **~240** ✓ |

The second row is the trap: you can't buy recall by slicing a short hash more finely, because narrow
bands have too few buckets and every probe returns a huge slice of the table. **You buy recall with a
longer hash.** 256 bits gets us distance ≤ 15 (≈ 6% bit error — comfortably covers recompression +
watermark + mild crop) while keeping bands at 16 bits so each probe returns ~15 rows.

Cost of the extra length: 32 bytes/image instead of 8. Irrelevant.

### 3.4 Two guards that naive implementations miss

**Hot buckets.** Real pHashes are *not* uniformly distributed — product photos on white backgrounds
cluster hard. A single band probe can return 100k rows. Fix: `LIMIT` **per band** (e.g. 50, ordered
`created_at DESC`) via a lateral join, not a global limit. Bounds worst-case work regardless of skew.

**Flat images.** Blank, solid-color, or gradient images have almost no DCT energy outside DC, so
their pHashes collapse onto a handful of values and match everything. Compute a flatness score
(variance of the non-DC coefficients) at hash time; if it's below a threshold, set `is_flat` and
**skip the image entirely** for matching.

**Stock-photo IDF.** Different dropshippers legitimately use the same supplier photos — that's not a
duplicate listing. Track how many *distinct stores* share each image bucket and weight the image's
evidence by `1 / log(1 + distinct_store_count)`. An image used by 200 stores proves nothing; an
image used by exactly one store, twice, proves a lot. This is cheap (a counter per bucket) and it's
the single best defence against the worst false-positive class.

### 3.5 Optional hardening (add only if we see evasion)

- **Mirror:** hash the horizontally-flipped image too and index `min(h, h_flipped)` as the canonical form.
- **Crop:** additionally hash 4 quadrants + a center 80% crop, index all as separate rows. 5× rows, catches partial crops.
- **Recolor:** a 64-bit color-moment hash as a separate signal (grayscale pHash is blind to color).

Don't build these up front. Ship §3.1–3.4, watch what the reposters actually do.

### 3.6 Image-set similarity

A product has *m* images, the candidate has *n*. For each of ours, take the best match on theirs
(`sim = 1 - hamming/256`), greedily bipartite-match (m, n ≤ ~10, so greedy is fine), count a pair as
matched at `sim ≥ 0.94`, then:

```
img_sim = Σ(idf-weighted matched sims) / min(m, n)
```

`min(m, n)` in the denominator so a 3-image repost of a 10-image original still scores 1.0.

## 4. Description

**Reuse the exact same machinery.** SimHash (Charikar) is built for "near-duplicate documents with
tiny edits" — it's what Google used for crawl dedup:

1. Normalize: lowercase, strip punctuation/emoji/URLs/phone numbers, collapse whitespace.
2. Word 4-shingles, each hashed to 64 bits, weighted by count.
3. For each bit position, sum `+w` if set, `-w` if not; final bit = sign.

64-bit SimHash, **4 bands × 16 bits, distance ≤ 3** — the canonical web-scale config, and the same
band table as images with a different `kind` discriminator.

At scoring time, don't use the SimHash distance as the similarity — you have the actual text for
≤400 candidates. Compute **exact Jaccard on word 5-shingles**. SimHash is for retrieval; Jaccard is
for scoring.

## 5. Title

Normalize aggressively — lowercase, strip punctuation and emoji, drop marketing filler
(`brand new`, `free shipping`, `🔥`, `100% original`, …), collapse whitespace. Then:

- **Blocking:** index `md5(title_norm)` for the exact-match fast path.
- **Scoring:** character 3-gram Jaccard between normalized titles. Robust to word order and typos,
  and cheap.

## 6. Price

```
price_sim = max(0, 1 - |pa - pb| / (0.25 * max(pa, pb)))
```

1.0 at identical, 0 at ≥25% apart. Normalize currency first. Never use price for blocking — it's a
weak signal and a wide range.

## 7. Fusion

Features per candidate pair: `img_sim`, `title_sim`, `desc_sim`, `price_sim`, `attr_sim` (category +
brand + condition), `same_store`, `exact_image_count`.

### v1 — hand-tuned, ship this

```
base = 0.45*img + 0.18*title + 0.22*desc + 0.10*price + 0.05*attr

if same_store:                       base += 0.05        # capped at 1.0

# hard rules override the weighted sum
if exact_image_count >= 1 and price_sim > 0.6:
    conf = max(base, 0.95)
elif img_sim < 0.35 and desc_sim < 0.85:
    conf = min(base, 0.55)           # no visual evidence → not a duplicate
else:
    conf = base
```

That second rule is load-bearing. Two different sellers listing the same iPhone with the
manufacturer's stock title, stock description and near-identical price will score ~1.0 on every text
feature and are **not** duplicates. Without the gate, the top of the flag queue is entirely popular
SKUs.

### v2 — learned, after we have labels

Fit a logistic regression on the 7 features. Ships as 8 floats, runs in nanoseconds, and beats
hand-tuned weights the moment you have ~500 labels. Retrain quarterly. Keep v1 as the fallback.

## 8. Grouping

A store that posts the same item 20 times generates 190 pairwise flags. Instead, maintain a
`duplicate_group_id` via union-find; the canonical member is the earliest still-live listing. Flags
attach to the group. The moderation UI shows one group, not 190 pairs.

## 9. Data model (Postgres reference)

```sql
create table product_fingerprint (
  product_id       bigint primary key,
  store_id         bigint      not null,
  category_id      int,
  created_at       timestamptz not null,
  price_cents      bigint,
  currency         char(3),
  title_norm       text,
  title_norm_hash  bytea,            -- md5(title_norm)
  desc_simhash     bytea,            -- 8 bytes
  desc_shingles    bytea,            -- optional: 128 x uint32 MinHash
  image_count      smallint,
  duplicate_group_id bigint
);

create table product_image_hash (
  product_id  bigint   not null,
  image_ix    smallint not null,
  bytes_hash  bytea    not null,     -- 8 bytes, xxh3 of normalized pixels
  phash       bytea    not null,     -- 32 bytes, 256-bit
  is_flat     boolean  not null default false,
  primary key (product_id, image_ix)
);
create index on product_image_hash (bytes_hash);

-- THE index. One row per (kind, band_ix, band_val).
create table fingerprint_band (
  kind        smallint    not null,  -- 0 = image phash, 1 = desc simhash
  band_ix     smallint    not null,
  band_val    int         not null,  -- 16-bit
  product_id  bigint      not null,
  image_ix    smallint,              -- null for text
  store_id    bigint      not null,
  created_at  timestamptz not null
) partition by range (created_at);   -- monthly partitions

create index on fingerprint_band (kind, band_ix, band_val, created_at desc)
  include (product_id, image_ix, store_id);
```

**Monthly partitions give us the 6-month rule for free.** Drop the 7th-oldest partition nightly:
retention is a metadata operation, and the index stays bounded by *6 months of posts* rather than
growing with the whole catalog forever. That's what keeps this fast at year 5.

### Sizing

`band_rows ≈ products_in_window × images_each × 16`

At 500k products / 6 months × 5 images = **40M rows**, ~3–4 GB with the index. If that's too much,
drop to 128-bit / 8 bands (halves it, recall ≤ 7 instead of ≤ 15).

## 10. Query

Per-band `LIMIT` via lateral join — this is what bounds the hot-bucket case:

```sql
select distinct c.product_id, c.image_ix
from unnest(array[0,1,...,15], array[$1,...,$16]) as b(ix, val)
cross join lateral (
  select product_id, image_ix
  from fingerprint_band
  where kind = 0 and band_ix = b.ix and band_val = b.val
    and created_at >= now() - interval '6 months'
    and product_id <> $me
  order by created_at desc
  limit 50
) c;
```

If dedup is **same-store only**, add `and store_id = $store` — candidate counts collapse to near
zero and the whole thing gets ~10× cheaper.

### Flow

```
check(new_product):
    cands = {}
    for img in new.images:                                  # tier 0
        cands += lookup_exact(img.bytes_hash, 6mo)          #   mark exact_image
    for img in new.images:                                  # tier 1
        if img.is_flat: continue
        cands += lookup_bands(IMAGE, bands(img.phash), 6mo, cap=50)
    cands += lookup_bands(DESC, bands(new.desc_simhash), 6mo, cap=200)
    cands += lookup_title_hash(new.title_norm_hash, 6mo, cap=200)

    for pid in top_200(cands, by=probe_hit_count):
        f = features(new, load(pid))
        conf = fuse(f)
        if conf >= 0.70: emit(pid, conf, f)
```

## 11. Performance budget

| Step | Cost |
|---|---|
| pHash, 5 images (upload path, shared with thumbnailing) | ~15 ms |
| SimHash + title normalize | < 1 ms |
| 16 band probes × 5 images + 4 text + 1 title | ~85 probes, ~8 ms |
| Load ≤200 candidate fingerprints | ~5 ms |
| Score 200 pairs | ~10 ms |
| **Added to post latency** | **~40 ms** |
| Storage per product | ~200 B + 40 B/image |

Run it **synchronously with a 150 ms timeout and fail-open** — on timeout, allow the post and
enqueue an async re-check. Never block a seller because the dedup service hiccuped.

## 12. Rollout

- **Phase 0** — schema, hashing in the upload pipeline, backfill 6 months of history.
- **Phase 1** — shadow mode, 2 weeks. Compute and log every pair scoring ≥ 0.40 with its full feature
  vector. No enforcement, no user-visible effect.
- **Phase 2** — hand-label ~300 pairs stratified across the score range. Plot precision/recall,
  pick the operating point (0.70 is a guess until this exists). Fit v2 weights if the data supports it.
- **Phase 3** — enforce. ≥ 0.85 → reject at post with "this looks like your existing listing" + a
  link to it. 0.70–0.85 → moderation queue. Always log, always appealable.
- **Phase 4** — the actual business goal: aggregate per store. A store with a high duplicate rate gets
  rate-limited or reviewed. Catching individual dupes matters less than identifying the handful of
  stores doing it.

## 13. Known failure modes

| Evasion | Handled? |
|---|---|
| Re-encode / resize / quality change | Yes — pHash |
| Watermark, logo overlay | Yes — low-frequency DCT survives it |
| Added border | Yes — auto-crop before hashing |
| Small crop / zoom | Partially — §3.5 tile hashes if needed |
| Mirror flip | §3.5 canonicalization |
| Recolor | No — add color-moment hash if seen |
| One-word description edit | Yes — SimHash is designed for exactly this |
| **Genuinely new photos of the same item** | **No.** Text + price only. Accept the miss. |
| Different stores, same supplier stock photos | Yes — image IDF (§3.4) + `same_store` |
| Legit store with 20 identical units in stock | **Open question — see below** |

## 14. Open questions

1. **Same-store only, or cross-store too?** Restricting to same-store makes this ~10× cheaper and
   eliminates the stock-photo false-positive class — but misses image theft between stores. Which is
   the actual problem?
2. **Is a store with real multiple identical units a duplicate?** A shop with 20 of the same shirt
   in different sizes looks identical to the algorithm. Does variant/stock modelling already handle
   this, or does dedup need to?
3. **Window anchored on what?** `created_at`, or `last_bumped_at`/`renewed_at`? If a listing can be
   bumped, "6 months since posting" and "6 months since it was last visible" differ a lot.
4. **What happens on a flag?** Hard reject, review queue, or silent scoring for seller reputation?
5. **Scale?** Products in a 6-month window, posts/day, images/product — drives §9 sizing.
6. **Edits vs. new posts.** Editing a listing shouldn't make it a duplicate of its own prior version.
   Fingerprints keyed by `product_id` and updated in place handle this; confirm listings have stable IDs.
7. **Stack?** Postgres is assumed above. Mongo/MySQL/Elasticsearch all work — the banding trick needs
   only a compound equality index — but the DDL and the lateral-join limit change.

## 15. Explicitly out of scope

CLIP / MobileNet embeddings + a vector index would be substantially more robust (it would catch
"new photos of the same item"), but it needs a model host and an ANN index — real cost. If we ever
want it, the right place is **tier 2 on the moderation queue only** (a few hundred pairs/day, not
every post), where the cost is bounded. Not now.
