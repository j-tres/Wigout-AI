> Provenance: Original distillation (Wigout Studio) — device and parameter names cross-checked against coach `../../../coach/references/devices-fx.md`; confirm exact displayed labels live.

# Compressor+

**What it does:** the full-featured dynamics processor. It reduces the level of
whatever rises above a threshold, narrowing the gap between loud and quiet parts.
Use it to control dynamics (tame an uneven vocal or bass), add punch (shape the
transient), or glue a bus (make many sources move as one). It also hosts a
sidechain input so an external signal can trigger the gain reduction (classic
kick-ducks-bass pumping).

## Key parameters (what each does sonically, useful range)

**Threshold**, **Ratio**, **Attack**, and **Release** are corroborated in the
fact-checked KB; makeup gain and the sidechain controls are described by
function. **Confirm the exact displayed labels live.**

- **Threshold** — the level above which compression starts, in dB. Continuous.
  Lower it and more of the signal gets compressed (more gain reduction); raise it
  and only the loudest peaks are touched. Watch the gain-reduction meter — aim for
  a few dB on the loudest hits, not constant crushing.
- **Ratio** — how hard the part above Threshold is reduced (e.g. 2:1 gentle, 4:1
  firm, high ratios approach limiting). Continuous. Low ratios level and glue;
  high ratios clamp and control.
- **Attack** — how fast the compressor clamps down after the signal crosses
  Threshold. Continuous. Fast attack catches the transient (softens punch, can
  dull); slow attack *lets the transient through* before compressing (keeps/adds
  punch). This is the single biggest lever on whether a sound gets punchier or
  softer.
- **Release** — how fast the compressor lets go after the signal falls back below
  Threshold. Continuous. Too fast can pump/distort; too slow leaves the sound
  squashed between hits. Tune it to the groove so gain reduction breathes with the
  tempo.
- **Makeup / output gain** — brings the overall level back up after compression
  lowered it, so you A/B tone and dynamics rather than just loudness. Continuous.
  Confirm the exact label live.
- **Sidechain input + its filter** — feeds an *external* signal to trigger the
  compression, with a filter on that detector signal so (for example) only a
  kick's low end ducks the bass. Confirm live how the sidechain source and its
  filtering are labeled and routed on your build.
- **Knee** (if exposed) — how gradually compression engages around Threshold
  (soft = musical/transparent, hard = grabby). Confirm live whether your build
  exposes a knee control.

## Discrete parameters (address at exact normalized step)

Most of Compressor+ is continuous. Any stepped selector (e.g. a detection mode or
sidechain source picker, if present) only takes if a write lands exactly on a
step. **Do not invent normalized step values; read the parameter's displayed
value live to enumerate the steps**, then match the readout to the option you
want.

## Good starting points

- **Snare/drum punch:** medium-high Threshold so only hits trigger, ratio around
  4:1, *slow* Attack to let the transient snap through, medium Release timed to
  the groove; makeup to match. Aim for a few dB of gain reduction on hits.
- **Bus glue:** low ratio (about 2:1), Threshold set for only 1–3 dB of gain
  reduction, medium Attack and Release so the whole bus moves together without
  obvious pumping.
- **Even out a bassline/vocal:** moderate ratio, faster Attack to catch peaks,
  Release tuned so it recovers between notes; watch the meter for consistent, not
  extreme, reduction.
- **Sidechain pump:** route the kick to the sidechain input, filter the detector
  to the kick's low end, fast Attack and a Release timed to the tempo so the bass
  ducks and recovers on each kick. For the loudness/limiting stage instead, see
  `../../../coach/references/mixing-in-bitwig.md`.
