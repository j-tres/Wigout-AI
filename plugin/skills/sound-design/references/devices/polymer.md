> Provenance: Original distillation (Wigout Studio) — parameters verified against Bitwig v1 Polymer.

# Polymer

**Synthesis:** subtractive hybrid — one *swappable* oscillator slot feeding a
filter and a note-triggered amp envelope. The oscillator slot hosts different
oscillator engines **including a Wavetable oscillator (confirmed live
2026-07-12: the factory preset "Crystal Computer Arp" runs one — its
module page exposes Index, IndexMod, PitchMod, PhaseMod, Unison, U.Detune,
Pitch, Detune; "Index" is the wavetable-position control)**, so the same
device covers analog-style and digital sources. The loaded modules surface as
*module-named remote-control pages* (an init Polymer exposes a "Union" page
for its default Union oscillator and a "Low-pass MG" page for its filter;
"Sallen-Key" is another filter-module page observed live); the module
*chooser* itself is not visible over the controller API (`slotNames` lists
only FX / Note FX), so swapping engines is a UI action.

**Character:** the modern all-rounder. Fast to a usable result, CPU-light, and
the default reach for "a synth" — pads, synth strings, plucks, clean/sub
basses, leads. Flexible without an architecture to learn.

## Key parameters (what each does sonically, useful range)

The maps below name a control by its **function** and, where the label is
corroborated, its exact Bitwig name. **Always confirm the exact displayed label
live** — Polymer's oscillator slot changes which controls exist.

- **Oscillator type / character** (slot selector + the slot's own controls) —
  sets the raw harmonic material and is the biggest single lever on timbre.
  Brighter/buzzier sources (saw-family) give more for the filter to work with;
  purer sources (sine/triangle) read as sub/soft. The per-slot shape controls
  (e.g. wavetable position, a shape/PWM-style control) vary by slot — read the
  live labels. *Discrete selector — see below.*
- **Filter cutoff** (label depends on the loaded filter module — the default
  Low-pass MG module's factory page labels it **"Cutoff"** with **"Reso"**,
  confirmed live 2026-07-11; read the current page live) — the primary
  brightness control. Down = darker/warmer, up = brighter/more open.
  Continuous 0..1. This is the first move for most "too dull / too harsh"
  fixes.
- **Filter Resonance** — emphasis (a peak) right at the cutoff. A little adds
  nasal/vocal presence; pushed high it whistles and can self-oscillate into a
  sine. Continuous 0..1.
- **Filter type / slope** — selects low-pass vs high-pass/band-pass and how
  steeply it cuts. Low-pass is the subtractive default. *Discrete selector —
  see below; confirm the available types live.*
- **Amp envelope (Attack / Decay / Sustain / Release)** — the loudness contour
  and what turns one oscillator into a pluck vs a pad. Attack short = percussive
  transient; attack long = slow swell. Release sets the tail. In Polymer,
  **Attack and Release do most of the expressive work.** Confirm live whether
  the envelope exposes full ADSR or a reduced set.
- **Filter/modulation envelope** (if present) — a second envelope routed to the
  filter cutoff gives the classic pluck "wow" (opens bright, closes dark)
  independent of loudness. Confirm live whether Polymer's patch exposes this or
  whether you add it via a modulator.

For adding movement (LFO, Random, Envelope Follower, etc.), Polymer's controls
are standard modulation targets — see `../../../coach/references/modulators.md`.

## Discrete parameters (address at exact normalized step)

Discrete/stepped selectors do not respond to a nearby value — a write only
verifies if it lands exactly on a step. **Do not assume normalized step
values; read the parameter's displayed value live to enumerate the steps**, then
target the step whose readout matches the option you want.

- **Oscillator type / slot** — discrete list of oscillator engines.
- **Filter type / slope** — discrete list of filter modes.
- Any waveform/shape *selector* inside the chosen oscillator slot (a continuous
  shape/position knob is not discrete — check the readout to tell which it is).

## Good starting points

- **Warm sub bass:** pick a saw or triangle-leaning source, lower the filter
  cutoff until only the low body remains, modest resonance, short-to-medium
  release. Add a touch of drive/saturation downstream for weight.
- **Bright pluck:** brighter oscillator, filter cutoff mid-high, short amp
  attack + short decay + low sustain; if a filter envelope is available, give it
  a fast attack/short decay on the cutoff for the "wow".
- **Slow pad:** long amp attack and long release, sustain high, filter cutoff
  moderate; add a slow LFO to the cutoff or to the oscillator's shape control
  for gentle movement.
