> Provenance: Original distillation (Wigout Studio) — device and parameter names cross-checked against coach `../../../coach/references/devices-fx.md`; confirm exact displayed labels live.

# Chorus+

**What it does:** thickens and widens a sound using modulated short delays. It
makes a few slightly pitch-wavering, slightly delayed copies of the signal and
mixes them back, so one voice sounds like several — fatter, shimmering, and wider
in the stereo field. Reach for it to fatten a thin synth, add subtle stereo
movement to keys/pads, or turn a mono source into something that spreads.

## Key parameters (what each does sonically, useful range)

**Rate** and **Depth** are corroborated in the fact-checked KB; voice count and
mix are described by function. **Confirm the exact displayed labels live.**

- **Rate** — the speed of the modulation (how fast the copies drift in and out of
  tune). Continuous. Slow = gentle, glassy movement; fast = obvious warble/vibrato
  that can sound seasick if overdone. Slow settings read as "wide and lush", fast
  as "effected".
- **Depth** — how far the modulation pushes the pitch/time of the copies.
  Continuous. Low = subtle thickening; high = strong detune/warble. Small Depth
  plus slow Rate is the tasteful widening zone; large Depth is a special effect.
- **Voices / number of copies** (if exposed) — how many modulated copies are
  summed. More voices = thicker and wider but denser and more smeared. Confirm the
  exact label and range live.
- **Mix / dry-wet** — the balance of the chorused copies against the dry signal.
  Continuous. Because chorus works by combining delayed copies, higher mix widens
  and thickens but also introduces more comb-filtering coloration — back off if
  the tone goes hollow. Confirm the exact label live.

## Discrete parameters (address at exact normalized step)

Chorus+ is mostly continuous. Any stepped selector (e.g. a mode/voice-count
picker, if present) only takes if a write lands exactly on a step. **Do not invent
normalized step values; read the parameter's displayed value live to enumerate the
steps**, then match the readout to the option you want.

## Good starting points

- **Gently widen a mono pad:** low Depth, slow Rate, low Mix — just enough to lift
  the sound off the center and add life. Big settings sound seasick.
- **Fatten a thin synth/lead:** moderate Depth and Mix, moderate Rate; blend so the
  dry attack still leads and the chorus fills behind it.
- **Lush stereo keys:** slow Rate, low-moderate Depth, more voices if available for
  a shimmering spread.
- **Width caveat:** widening effects can weaken mono/phase compatibility, and the
  problem is worst in the low end. Keep bass and sub mono — apply chorus/width to
  mids and highs, not the low register. See the "width" recipe and mono-bass
  caveat in `../fx-character.md`.
