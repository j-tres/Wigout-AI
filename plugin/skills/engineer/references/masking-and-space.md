> Provenance: original distillation, engineer-v2 cycle 2026-07-10; device names cross-checked against the fact-checked sound-design/coach KBs.

# Masking and space — making room in a crowded mix

## What masking is

When two sounds put strong energy in the same frequency band at the same time,
the louder one hides the quieter one — the ear cannot resolve both. That is
**masking**: simultaneous competition for the same band. It is why a mix can have
every part audible in solo yet turn to mush all together — the parts are not too
quiet, they are fighting over the same frequencies. The fix is almost never
"turn it up" (that just starts a war); it is to make *room*.

The classic offending pairs, by band:

- **Kick vs bass** in the low end (roughly sub through low-mids) — the two
  biggest, lowest elements almost always overlap and need deliberate separation.
- **Vocal / lead vs pads / keys** in the mids — sustained harmonic instruments
  blanket the exact range a lead needs to be intelligible.
- Snare vs guitars / synths in the high-mids; hats vs cymbals vs air up top.

## Reading `masking_check.py`

`masking_check.py --a <wav> --b <wav>` compares two stems band by band and
returns:

- **`bands`** — an overlap score from 0 to 1 for each of `sub`, `bass`,
  `lowmid`, `mid`, `highmid`, `high`. Higher means the two stems are loud in that
  band at the same time more often.
- **`worst`** — the top couple of offending bands, ranked, so you know where to
  aim.
- **`advice`** — a plain-language suggestion keyed to the worst band and its
  severity.

Treat the thresholds as **proxies**, not verdicts: the tool flags ~0.6+ as
strong overlap and ~0.3+ as moderate, but it is a band-energy overlap estimate,
not a psychoacoustic model. Confirm with solo/mute and your ears before you cut
anything — `limits` in the output says as much.

## Fixes, cheapest first

Work down this list and stop as soon as the problem is solved; each step costs
more than the one above it.

1. **Arrangement** — the cheapest fix is to not have the collision at all. Move
   one part to a different octave, thin its voicing, or clear a rhythmic pocket
   so the two elements are not sounding at the same instant. This is a
   composition change — **hand it to the composer role**; a good arrangement
   needs the least mixing.
2. **Pan separation** — push the two competitors to different sides of the stereo
   field so they no longer share the same space. Free, and often enough for
   mid/high collisions (keep low-frequency elements centered — see the width note
   below).
3. **EQ carving** — cut **2-4 dB** in the contested band on the *supporting*
   stem exactly where the *lead* stem needs it, so the lead pokes through a hole
   you carved for it. Small, surgical, and reciprocal (a matching gentle boost on
   the lead if needed). Do it with EQ+; the eq guide covers narrow-Q cuts.
   Guide: [EQ+](../../sound-design/references/devices/fx-eq-plus.md)
4. **Sidechain ducking** — when the collision is rhythmic and constant (the
   kick-vs-bass classic), duck the supporting stem *only when the lead hits*.
   Route the trigger signal into the compressor's sidechain input, filter the
   detector to the relevant band, fast attack, release timed to the groove so the
   supporting stem dips and recovers on each hit. Bitwig's sidechain source is
   selected on the compressor itself — **describe the exact source/routing by
   function and confirm it live**, do not assume a menu path.
   Guide: [Compressor+](../../sound-design/references/devices/fx-compressor.md)
5. **Width, with the mono caveat** — spreading a part wider can also unmask it,
   but widening decorrelates left and right and hurts mono compatibility, worst
   in the low end. Keep bass and sub mono; apply width to mids and highs only,
   and check `stereo.mono_drop_db` from `mix_report.py` afterward. See the width
   / mono-bass section of
   [fx character](../../sound-design/references/fx-character.md).

## Workflow tie-in

Masking checks compare **stems**. If the elements you need to compare do not
already exist as separate tracks — you only have a stereo bounce — run
`stem_split.py --audio <wav> --out-dir <dir>` first (optional dependency:
invoke as `uv run --group stems python stem_split.py ...` — the `--group`
flag must be on the `uv run` line, or uv's implicit re-sync strips the
package) to separate them, then feed the pieces to `masking_check.py`. Choose which pairs to check from the report card's band
imbalances: if `mix_report.py` shows the `low` band dominating
`spectrum.bands`, check the kick-vs-bass pair; a hollow or crowded `mid` points
you at the vocal/lead-vs-pads pair. Fix at the cheapest stage that works, then
re-measure. Stage context: [mix workflow](mix-workflow.md).
