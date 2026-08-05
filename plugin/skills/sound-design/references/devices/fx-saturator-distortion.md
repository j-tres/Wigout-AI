> Provenance: Original distillation (Wigout Studio) — device and parameter names cross-checked against coach `../../../coach/references/devices-fx.md`; confirm exact displayed labels live.

# Saturator

**What it does:** adds harmonics by waveshaping the signal — bending the waveform
so new overtones appear that were not in the source. This is how you get warmth,
grit, "analog" density, and *loudness-without-level*: saturation raises perceived
weight and presence without pushing the peak up the way a fader would. Reach for
it to fatten thin sounds, add edge, and glue a bus with harmonic density.

**On distortion vs. saturation:** these are the same mechanism at different
intensities. Gentle waveshaping = warm saturation; hard waveshaping/clipping =
distortion/fuzz. Bitwig ships other, harder-edged distortion-family and
bit-reduction devices as well — when you want more aggressive breakup than
Saturator gives, browse the effect list and **confirm the exact device name live**
rather than assuming one; do not rely on a remembered proper noun for those.

## Key parameters (what each does sonically, useful range)

**Drive** is corroborated in the fact-checked KB; the curve selector and output
compensation are described by function. **Confirm the exact displayed labels
live.**

- **Drive** (input gain into the shaper) — how hard you push the signal into the
  waveshaper, and thus how much harmonic content is generated. Continuous. Low =
  subtle warmth/thickening; high = obvious grit and eventually distortion. This is
  the main character control.
- **Saturation curve / type** — selects the *shape* of the waveshaping (softer
  tube-like rounding vs. harder, brighter, more aggressive clipping). Different
  curves emphasize even vs. odd harmonics and read as "warm" vs. "edgy". *Discrete
  selector — see below; confirm the available curves live.*
- **Output / compensation gain** — trims the level back down after Drive raises
  it, so you can add harmonics without the output getting louder (fair A/B).
  Continuous. Match output to input so you judge tone, not just volume. Confirm
  the exact label live.
- **Mix / dry-wet** (if exposed) — blends the saturated signal against the clean
  one, i.e. parallel saturation: keep the transients clean and add harmonic body
  underneath. Confirm whether your build exposes a wet/dry control here or whether
  you achieve parallel blend with an FX-layer container (see
  `../../../coach/references/mixing-in-bitwig.md`).

## Discrete parameters (address at exact normalized step)

A stepped selector only takes if the write lands exactly on a step. **Do not
invent normalized step values; read the parameter's displayed value live to
enumerate the steps**, then match the readout to the option you want.

- **Saturation curve / type** — discrete list of shaping curves. Read the live
  list to pick the character (softer vs. harder).

## Good starting points

- **Warm-up a bus (drums/mix):** low Drive, a soft curve, output matched to
  input — just enough harmonic density to make the bus feel bigger and more
  present without raising the peak.
- **Fatten a thin synth/bass:** moderate Drive with a curve that adds low-order
  harmonics; on bass, saturation adds upper harmonics that let small speakers
  imply the fundamental (helps translation).
- **Aggressive grit / lead:** high Drive, a harder curve; then tame the harshness
  with a downstream low-pass in `fx-filter.md` (Filter+) or a high-cut in EQ+.
- **Parallel dirt:** blend a heavily driven copy under the clean signal (mix
  control or an FX-layer branch) so the attack stays clean and the body gets
  grit. See the "grit" recipe in `../fx-character.md`.
