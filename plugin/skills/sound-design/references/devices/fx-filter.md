> Provenance: Original distillation (Wigout Studio) — device and parameter names cross-checked against coach `../../../coach/references/devices-fx.md`; confirm exact displayed labels live.

# Filter+

**What it does:** a multimode filter for tone shaping and movement. Unlike EQ+
(which places many static bands), Filter+ is a single sweepable filter built to
be *played* — automated across a build, modulated by an LFO for wobble, or driven
for character. Reach for it for risers/sweeps, rhythmic movement, and coloring a
sound with resonance and drive rather than surgically correcting it.

## Key parameters (what each does sonically, useful range)

**Frequency** and **Resonance** are corroborated in the fact-checked KB; the type
selector and drive are described by function. **Confirm the exact displayed
labels live.**

- **Frequency** (cutoff) — the corner where the filter starts to act, and the
  primary brightness control. Continuous. Down = darker/warmer; up = brighter/more
  open. This is the knob you automate for a filter sweep and modulate for a
  wobble.
- **Resonance** — a level boost right at the cutoff Frequency. Continuous. A
  little adds nasal/vocal emphasis and makes a sweep sing; pushed high it whistles
  and can self-oscillate into a tone of its own. High resonance also thins the
  body — compensate if the sound gets weedy.
- **Filter type / mode** — selects low-pass, high-pass, band-pass, or notch
  behavior. Low-pass (keep lows, cut highs) is the default for taming/warming;
  high-pass clears lows; band-pass isolates a middle region (telephone/formant
  character); notch scoops a slice. *Discrete selector — see below; confirm the
  available modes live.*
- **Drive** (a saturation-into-the-filter control, confirmed in the coach KB
  `../../../coach/references/devices-fx.md`) — pushes the signal harder into
  the filter for added harmonics and grit, especially musical when combined
  with resonance. Confirm the exact displayed label live.
- **Slope / poles** (if exposed) — how steeply the filter cuts past the corner.
  Steeper = more dramatic removal; gentler = more natural. Confirm live whether
  your build exposes a slope choice. *If it is a selector, treat it as discrete.*

Filter+ shines as a *modulation target*: route an LFO, envelope, or the mod
sources in `../../../coach/references/modulators.md` to Frequency for wobble,
sweeps, and rhythmic gating.

## Discrete parameters (address at exact normalized step)

A stepped selector only takes if the write lands exactly on a step. **Do not
invent normalized step values; read the parameter's displayed value live to
enumerate the steps**, then match the readout to the option you want.

- **Filter type / mode** — discrete list (low-pass / high-pass / band-pass /
  notch, and any others). Read the live list.
- **Slope / poles selector** — if present as a stepped choice.

## Good starting points

- **Filter sweep into a drop:** low-pass mode, Frequency low and closed, moderate
  Resonance, then automate the Frequency open across the build so the sound
  brightens into the drop.
- **Wobble bass:** low-pass, moderate Resonance, and an LFO synced to tempo on the
  Frequency; depth of the LFO sets how wide the wobble travels.
- **Thin/telephone effect:** band-pass mode, narrow the passband (Frequency in the
  mids, Resonance up a little) to strip body for a lo-fi vocal or breakdown.
- **Add grit without EQ:** raise the drive/input control with moderate Resonance
  for harmonic edge, then use the Frequency to keep the added harmonics in check.
