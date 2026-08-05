> Provenance: original distillation, engineer-v2 cycle 2026-07-10; device names cross-checked against the fact-checked sound-design/coach KBs.

# Mix workflow — the staged pass

A mix is a sequence of decisions, and the order matters more than any single
move. Work top-to-bottom through the stages below: fix levels before tone, tone
before dynamics, dynamics before space, and leave loudness for last. The reason
is causal — every stage feeds the next, so a problem you leave in an early stage
gets amplified and re-processed by everything downstream. Fix it once, at the
cheapest stage, instead of chasing it with five plugins later.

Each stage lists what the bridge can **read/write** for it and which script
**verifies** it. Read the project first with `bw_snapshot` before you touch
anything — advice has to reference what the project actually contains.

## 1. Gain staging

Before any processing, get levels sane — this is gain staging. Aim to leave
**headroom**: individual tracks peaking roughly **-12 to -6 dBFS** (directional,
not law), and the master
comfortably below 0 with several dB to spare before its chain. The point of
headroom is that clipping is unrecoverable — a signal that clips at any stage
carries that distortion forward, and no downstream EQ or compressor can undo it.
Worse, level-dependent devices (compressors, saturators) react to *how hot* the
signal is, so sloppy staging silently changes how every device below behaves. A
too-hot signal into the master also leaves the limiter no room to work.

- **Bridge:** read levels from `bw_snapshot` (depth 1) across tracks and master;
  set track volume via the curated mixer tools or `bw_set`. Prefer a device's
  input trim / a gain utility (describe by function — confirm the exact device
  live) over the fader for staging, so the fader stays free for balance.
- **Verify:** `mix_report.py --audio <wav>` — `loudness.sample_peak_dbfs` and
  `loudness.crest_db` tell you whether the summed mix has headroom and life.

## 2. Balance

Set the static **balance** with faders and pan alone, before any EQ. Most
"muddy" or "small" mixes are just balance problems — the wrong things are too
loud. Get the relationship between elements right with volume and stereo
placement first; you will need far less corrective processing afterward. Then
**mono-check the balance**: sum to mono and confirm nothing important vanishes
and the balance still holds (a part that disappears in mono is a phase/width
problem, not a level one).

- **Bridge:** track volume/pan and send levels via the curated mixer tools or
  `bw_set`.
- **Verify:** `mix_report.py` — `spectrum.bands` (low/mid/high) and
  `stereo.correlation` / `stereo.mono_drop_db` flag a lopsided balance or a mono
  collapse before you commit to fixes.

## 3. Corrective EQ

Now shape tone. The discipline here is **cut before you boost**: subtractive
moves that remove problems (rumble, a boxy resonance, harshness) are cleaner and
cheaper than boosting, and they free up headroom instead of eating it. This is
corrective eq — surgical, early in the chain — not the creative voicing you do
later. Reach for EQ+; the linked eq guide covers band types, Q, and the
high-pass move.

- **Bridge:** device parameters via the bridge's verified parameter path (the
  same path the sound-design role uses); confirm the exact band/parameter is
  addressable live.
- **Verify:** `masking_check.py --a <wav> --b <wav>` — when two stems fight,
  its `bands`/`worst`/`advice` tell you *which* band to carve and on which stem,
  so an EQ cut is aimed rather than guessed. See `masking-and-space.md`.
- Guide: [EQ+](../../sound-design/references/devices/fx-eq-plus.md)

## 4. Dynamics — compression

Compress to control dynamics and shape punch, not to make things loud. At the
mix stage the three levers each do a specific job: **ratio** sets how firmly
peaks are reined in (≈2:1 to level and glue, ≈4:1 to firmly control);
**attack** decides whether the transient survives (slow attack lets the hit
snap through and keeps punch, fast attack softens it); **release** sets how the
gain recovers between hits (tune it to the groove so reduction breathes instead
of pumping). Aim for a few dB of gain reduction, not constant crushing.

- **Bridge:** device parameters via the verified parameter path; confirm live.
- **Verify:** ears plus the report card — compression that over-squashes shows
  up as a collapsed `loudness.crest_db` in `mix_report.py`.
- Guide: [Compressor+](../../sound-design/references/devices/fx-compressor.md)

## 5. Space — sends

Add depth with reverb and delay, and do it on **sends** (FX tracks), not
inserts, whenever several sources should share one space. Run the effect at 100%
wet on the FX track and control the amount with each track's send level — this
glues elements into one room and keeps CPU and dry/wet discipline in one place.
Reserve inserts for a space that belongs to a single sound. Dry/wet discipline
matters: too much wet smears the mix, so keep tails dark and pre-delay generous
(see the sound-design space recipe).

- **Bridge:** send levels via the curated mixer tools or `bw_set`; the FX-track
  routing model is described in the coach mixing guide.
- **Verify:** `mix_report.py` — `stereo.width` and `mono_drop_db` catch a wash
  that widened the mix into mono trouble.
- Routing: [mixing in Bitwig](../../coach/references/mixing-in-bitwig.md)

## 6. Bus glue

Group related tracks (drums, vocals) early and process the whole section on the
group track. A gentle compressor on the bus — low ratio (~2:1), only **1-3 dB**
of gain reduction, medium attack/release — makes the parts move as one. A touch
of saturation on the same bus reads as cohesion and perceived punch. This is a
different job from the master limiter; keep them separate.

- **Bridge:** device parameters on the group track via the verified path.
- **Verify:** `mix_report.py` on a bounce of the group, if isolated.

## 7. Master chain

Process the summed mix last, in this order: **corrective EQ** (broad, gentle —
fix a spectral tilt, not surgery) → **glue compression** (1-2 dB, slow, holding
the whole mix together) → **limiter-style ceiling control** (a brickwall/peak
limiter set so true peaks stay under the target ceiling — describe by function
and confirm the exact device live). Loudness comes **last**: get the balance and
tone right, then measure loudness against a target and nudge — never chase a
loudness number before the mix is right, it just bakes in problems.

- **Bridge:** device parameters on the master track via the verified path.
- **Verify:** `mix_report.py --audio <mix.wav> --target streaming` (or `club`) —
  `target.lufs_delta` and `target.true_peak_margin_db` are the loudness/ceiling
  verdict. See `loudness-targets.md` for what the numbers mean.

## When to hand off

If a sound is *wrong at the source* — a synth that is inherently thin, a patch
that is harsh no matter how you EQ it, a bass with no fundamental — that is a
timbre problem, not a mix problem. Mixing decides how a sound **sits** among the
others; sound-design decides what the sound **is**. When you find yourself
fighting the same element at every stage, stop and hand it to the sound-design
role to fix the patch, then re-mix. That is the sits-vs-is boundary.
