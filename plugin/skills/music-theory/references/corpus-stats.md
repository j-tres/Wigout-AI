<!-- provenance: original; statistics distilled from McGill Billboard (CC0, DDMAL/McGill) and When in Rome open-license sub-corpora (CC-BY-SA-4.0 (aggregate; pop slice CC0)), see chord_model.json provenance block. Attribution: McGill Billboard Project (DDMAL, McGill University; CC0 waiver) + When in Rome (Gotham et al., CC BY-SA 4.0; sub-corpora: OpenScore-LiederCorpus, Bach WTC I, Bach & Purcell grounds; OpenScore Lieder scores CC0). -->
# Corpus Chord Statistics — cookbook

When to use which harmony surface:
- **These stats** — "what usually comes next", "how common is my
  progression", "what do real songs do" — evidence questions.
- **`theory_engine.py`** — spelling and analysis (keys, numerals→MIDI,
  voice leading) — math questions. Realize every stats suggestion
  through it before writing notes.
- **The curated fourteen** ([harmony-progressions.md](../../composer/references/harmony-progressions.md))
  — fast, opinionated starting points; stats give corpus evidence
  beyond them.

## Subcommands (from `plugin/scripts/`, `uv run python chord_stats.py ...`)

**next** — ranked continuations for a chord context:
`next --context "i,VI,III" --mode minor --genre pop`
Returns `candidates` (numeral, quality, probability, count,
globalProbability), `backoff` (trigram→bigram→unigram — how much
context the corpus actually matched), `contextUsed`/`truncated`
(only the last 2 chords condition the lookup), `notes`, `limits`.

**progressions** — most-common loops in a bucket:
`progressions --mode major --genre pop --length 4`
`shareOfStored` is the share within the stored top-loops table for
that length, NOT of the whole corpus.

**diagnose** — commonness of an existing progression:
`diagnose --numerals "I,V,vi,IV" --mode major --genre pop`
Per-transition probability/rank, `overall.meanLog10Prob` +
`overall.percentile` (vs the bucket's sampled score distribution),
`overall.loopRank` (if it IS a stored top loop), and up to 3
`substitutions` (single-chord swaps that score more common). An
unseen transition zeroes the overall score — that means "the corpus
slice doesn't do this", which may be exactly the point; say so
rather than "fixing" it unprompted.

## Buckets are corpus slices, not genres

`pop` (856 songs, McGill Billboard) and `classical` (200 human
analyses, When in Rome open sub-corpora), plus `global` (everything,
and the fallback for any other `--genre` value — the response's
`bucketUsed`/`notes` disclose the fallback). Mode is a separate axis:
every bucket has `major` and `minor` tables. There is NO per-genre
conditioning (no open genre-annotated corpus exists) — `pop` means
"Billboard chart songs 1958–1991, expert-annotated" and `classical`
means "common-practice analyses from When in Rome's open-license
sub-corpora, human-authored only — machine-generated (AugmentedNet)
analyses are excluded".

## Caveats (relay these)

- Small corpora (1056 songs/analyses total): bigram stats are solid,
  trigrams and long loops are thin — expect `backoff` to fire often;
  that is disclosure, not failure.
- Era/style skew: chart pop 1958–1991 + common-practice classical.
  No electronic, hip-hop, or contemporary genre evidence.
- Keys/modes were resolved automatically at build (annotated tonics
  where present); low-confidence songs were dropped (rate in the
  model's provenance and in every response's `limits`).
- Numeral quality is encoded in the figure (`V7` dom7, `ii7` min7,
  `Imaj7` maj7, `viio` dim, `III+` aug). music21 spells uppercase-`7`
  figures diatonically (same caveat as the blues note in
  harmony-progressions.md) — the `quality` field in `next` responses
  carries the corpus intent when it differs.
- These are counts, not taste. A rare move is not a wrong move.
- `substitutions` can propose rare chords whose high scores ride tiny
  bigram denominators (a chord seen twice that both times moved to V
  scores "perfectly common"). Sanity-check a substitution's `count`
  context via `next` before recommending it.
- License/attribution: the artifact aggregates a CC0 slice (McGill
  Billboard) and a CC-BY-SA slice (When in Rome) — the combined model
  is shared under CC-BY-SA-4.0 (aggregate; pop slice CC0) with the
  attribution in its provenance block.
