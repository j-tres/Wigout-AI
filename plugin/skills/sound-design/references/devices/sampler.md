> Provenance: Original distillation (Wigout Studio) — parameters verified against Bitwig v1 Sampler.

# Sampler

**Synthesis:** sample playback. Plays back an audio file across the keyboard with
pitch tracking, loop modes, a filter, and an amplitude envelope. Not synthesis
in the oscillator sense — the *source* is a recording, and the controls shape how
it is pitched, looped, filtered, and enveloped.

**Character:** the route to realistic acoustic timbres, because a good
multisample beats any synthesis of a real instrument. Reach for it for strings,
brass, pianos, vocal chops, drum one-shots, and any "real instrument" request. Be
honest with the user: with only a single sample (not a multisample), realism
falls off as you play far from the recorded pitch.

## Key parameters (what each does sonically, useful range)

Sampler's exact control labels are not individually corroborated in the
fact-checked KB, so the entries below are named by **function**. **Confirm the
exact displayed label live** before relying on it.

- **Sample Start** — where playback begins in the file. Moving it past a soft
  onset makes the attack more immediate/percussive; trimming silence tightens
  timing. A great subtle per-note modulation target (via Random) for
  humanization.
- **Loop mode / Loop Start / Loop End** — whether and how the sample sustains by
  looping a region. Loop off = the sound ends when the sample does (one-shots);
  loop on = it sustains for held notes, and the loop points/crossfade decide how
  smooth or buzzy the sustain is. *Loop mode is a discrete selector — see below.*
- **Playback / pitch mode** — how the sample is transposed as you play up/down
  the keyboard (repitch vs. time-stretch style behavior). This strongly affects
  character away from the root note. *Often a discrete selector — see below.*
- **Keytracking / root key / tune** — the reference pitch and how strongly
  keyboard position transposes the sample. Sets whether it tracks the keyboard
  1:1 or is fixed. Confirm the exact labels live.
- **Filter (Frequency / Resonance)** — shapes brightness of the played sample
  exactly like a subtractive filter (down = darker, up = brighter; resonance
  emphasizes the cutoff). Bitwig commonly labels cutoff **Frequency**; confirm
  live on this device. May include a filter-type selector (*discrete*).
- **Amp envelope (Attack / Decay / Sustain / Release)** — reshapes the sample's
  loudness contour over its natural one: soften a hard attack, add a slow swell,
  or shorten the tail. Confirm live whether it is full ADSR.

For per-note variation (e.g. Random → Sample Start) and other movement, see
`../../../coach/references/modulators.md`.

## Discrete parameters (address at exact normalized step)

A stepped selector only takes if the write lands exactly on a step. **Do not
invent normalized step values — read the parameter's displayed value live to
enumerate the steps**, then match the readout to the option you want.

- **Loop mode** — discrete (e.g. off / forward / other loop behaviors); read the
  live list.
- **Playback / pitch mode** — discrete list of transposition behaviors.
- **Filter type** — if a filter-mode selector is present.

## Good starting points

- **Realistic sustained instrument (strings/pad):** load a multisample if you
  have one, loop on with clean loop points, keytracking on, gentle Filter
  Frequency roll-off if brittle, slightly longer amp attack and release to
  smooth the entry and tail.
- **Vocal chop / one-shot:** loop off, trim Sample Start to the onset, short amp
  envelope; add Random → Sample Start for subtle per-hit variation.
- **Lo-fi / gritty texture:** shorten and offset the loop for a buzzy sustain,
  lower Filter Frequency, and add saturation/bitcrush downstream (see the FX
  references) rather than expecting it from the Sampler itself.
