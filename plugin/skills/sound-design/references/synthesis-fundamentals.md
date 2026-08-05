> Provenance: Original distillation (Wigout Studio) — synthesis concepts; Bitwig device mappings verified against v1 stock devices.

# Synthesis Fundamentals — how sound is shaped

This is the foundation layer. Before you translate a described sound into
knob moves (`descriptor-dictionary.md`) or reach for a specific device's
parameter guide (`references/devices/`), you need the mental model of
*why* a control changes what you hear. Timbre is the shape of a sound's
harmonic content **and how that shape moves over time**. Every synthesis
technique is just a different way of producing and animating that shape.

The unifying picture of a synth voice is a short signal path:

```
SOURCE  →  SHAPER  →  AMPLIFIER
(oscillators)  (filter)   (VCA / amp env)
        ↑          ↑          ↑
        └──── MODULATION ─────┘   (envelopes, LFOs)
```

The source makes raw harmonics, the shaper removes or emphasises some of
them, the amplifier sets the loudness contour, and modulation makes all
three move so the sound feels alive instead of static. Keep this path in
mind — almost every request ("warmer", "punchier", "more movement") is a
move on one of these stages.

---

## Synthesis families — and which Bitwig device embodies each

Pick the family by the *character* of the target, not by habit. The
family decides what harmonics you can even produce; the filter and
envelopes only shape what the source already contains.

### Subtractive
Start with a harmonically **rich** waveform (saw, square) and *subtract*
content with a filter. This is the default mental model for most "analog"
sounds: pads, basses, plucks, leads. Brightness lives almost entirely in
the filter cutoff, body in the oscillators, motion in the envelopes.
- **Bitwig:** **Polysynth** is the textbook two-oscillator subtractive
  synth (fast, classic results). **Polymer** is the modern
  subtractive-hybrid — one swappable oscillator slot into a filter and
  amp envelope. As an insert on any source, **Filter+** subtracts
  content after the fact.

### FM (frequency / phase modulation)
One oscillator modulates the *frequency* (or phase) of another. Small
changes in the modulator's pitch **ratio** reshape the entire harmonic
spectrum, and non-integer ratios produce **inharmonic** partials — the
clangy, metallic, glassy tones a filter can never carve out of a saw.
This is the family for bells, mallets, electric pianos, and biting
digital basses.
- **Bitwig:** **FM-4** (four operators, tunable **Ratio**, selectable
  **Algorithm** routing, **Feedback**) is the direct FM engine.
  **Phase-4** does phase-modulation between oscillators for glassy,
  slightly unstable digital tones. Automate or modulate the ratio — it is
  the timbre control, not a tuning detail.

### Wavetable
The oscillator stores a *table* of many single-cycle waveforms; a
**position** control scans through them. Sweeping that position morphs
the harmonic content continuously, which is why wavetables excel at
evolving, digital, "moving" timbres — modulate the table position with an
envelope or LFO and the sound breathes.
- **Bitwig:** Polymer's swappable oscillator slot hosts a **Wavetable
  oscillator** — confirmed live 2026-07-12 (factory preset "Crystal
  Computer Arp": module-named "Wavetable" remote page whose **Index**
  control is the table position, with IndexMod/PhaseMod for movement).
  The module chooser itself is not API-visible, so swapping engines is a
  UI action. **Poly Grid** (and its effect twin **FX Grid**) has a
  dedicated wavetable oscillator module. Treat table position as a
  first-class modulation target.

### Additive
Build a tone by *summing* individual sine partials, setting the level of
each harmonic directly. It is the inverse of subtractive: instead of
carving a rich source down, you assemble the spectrum from the ground up.
Great for precise, pure, or organ-like harmonic mixes.
- **Bitwig:** **Organ** is the practical stock embodiment — its drawbars
  are literally harmonic level faders (an additive mixer). For fully
  general additive work, build it in **Poly Grid**.

### Physical modeling
Instead of drawing a waveform, model the *physics* of a resonant body
(a string, tube, or membrane) excited by a burst of energy. The excitation
(pluck, bow, strike) plus the resonator's tuning and damping give
organic, playable timbres that respond to dynamics.
- **Bitwig:** there is no dedicated physical-modeling instrument in the
  stock set — be honest about that. You approximate it: a plucked-string
  (Karplus–Strong) voice is a short noise/impulse burst fed into a tuned
  delay-feedback resonator, which you patch in **Poly Grid**. For real
  captured instruments, a good multisample in **Sampler** beats any
  model. FM-4 also fakes struck/plucked attacks well.

---

## Oscillators — the raw harmonic material

The oscillator sets the ceiling of what any later stage can do. You
cannot filter brightness *into* a sound that has no upper harmonics.

**Waveform → harmonic content:**
- **Sine** — a single partial, no overtones. Pure, sub, flute-like. The
  building block of additive and FM.
- **Triangle** — mostly fundamental with weak, fast-decaying odd
  harmonics. Soft, hollow, mellow.
- **Sawtooth** — every harmonic present, falling off gently. The
  **brightest, buzziest** basic wave; the workhorse for strings, brass,
  supersaws, and aggressive leads.
- **Square / pulse** — odd harmonics only; hollow, reedy, "woody". The
  **pulse width** (duty cycle) sets how hollow: 50% is a full square,
  narrow pulses get thin and nasal. Modulating pulse width (PWM) gives a
  shimmering, chorused thickness from a single oscillator.

**Detune & unison** — stack copies of an oscillator slightly out of tune.
The beating between them reads as **width and thickness**; push it far and
you get the classic supersaw. A little detune warms and animates; too
much smears pitch. (More detail: `descriptor-dictionary.md`, "wide".)

**Oscillator sync** — hard-sync forces one oscillator to restart with
another's cycle, tearing its waveform into a bright, formant-rich,
aggressive tone. Sweeping the synced oscillator's pitch is the signature
"sync lead" scream. E.g. **Phase-4**; **Polysynth has it too — confirmed
live 2026-07-11: per-oscillator Sync1/Sync2 on the factory OSC1/OSC2
remote pages** (visible on the init preset; presets with saved macro
pages replace the factory pages, so it may not appear on a preset's
"Perform" page).

**Sub oscillator / octave** — a sine or square an octave (or two) below
the main pitch adds weight and low-end authority without changing the
character up top. Essential for basses and big leads.

---

## Filters — the primary timbre sculptor

After the source, the filter does the most audible work. It removes or
emphasises bands of the spectrum the oscillator produced.

**Type:**
- **Low-pass (LP)** — removes highs above the cutoff → **darker, warmer,
  rounder**. The most-used filter in subtractive synthesis.
- **High-pass (HP)** — removes lows below the cutoff → **thinner,
  lighter, more distant**. Cleans mud, makes a sound sit higher.
- **Band-pass (BP)** — keeps a band, cuts both ends → nasal, "telephone",
  focused, hollow.
- **Notch** — cuts a narrow band, keeps the rest → subtle phasey scoop.

**Cutoff** — the single biggest brightness control you have. **Cutoff
down + modest resonance → darker/warmer; cutoff up → brighter/harsher**
(see `descriptor-dictionary.md`). Most "too harsh / too dull" fixes are a
cutoff move first.

**Resonance (Q)** — boosts a peak right at the cutoff. A little adds
emphasis and vocal/nasal character; more gives a whistle or acid squelch;
at the extreme the filter self-oscillates into a sine. Resonance plus a
cutoff sweep is the core of every filter riser and wobble.

**Slope** — how steeply the filter cuts past the cutoff, in dB per
octave. **12 dB/oct** is gentle and leaves colour above the cutoff;
**24 dB/oct** cuts hard and sounds more dramatic and "synthetic". Steeper
= more aggressive removal.

**Drive** — pushing level into the filter adds harmonic saturation, so a
filter can *add* grit even as it subtracts highs. Filters in **Polymer**,
**Polysynth**, and the insert **Filter+** expose cutoff, resonance, and a
type/slope choice, but **the cutoff LABEL varies by device** — Polymer
calls it "Frequency", **Polysynth calls it "Cutoff" (confirmed live
2026-07-10)**. "Resonance" is a common label across devices, but read the
live displayed parameter names (`get_selected_device_parameters`) rather
than assume a label.

---

## Envelopes — timbre over time

A static spectrum sounds synthetic and dead; envelopes make it move. An
**ADSR** envelope outputs a contour you route to a destination:

- **Attack** — time to rise from silence to peak. Short = a percussive
  **transient** (pluck, stab); long = a slow **swell** (pad, strings).
- **Decay** — time to fall from the peak down to the sustain level. Sets
  the length of the initial "hit".
- **Sustain** — the level held while the note is down: the **body** of
  the sound. High sustain = organ-like hold; zero sustain = a purely
  percussive blip that ignores how long you hold.
- **Release** — time to fall to silence after note-off: the **tail**.
  Short = tight and abrupt; long = ringing, ambient.

**Amp envelope** shapes loudness — this is what makes the same oscillator
read as a *pluck* (fast attack, short decay, low sustain) versus a *pad*
(slow attack, full sustain, long release).

**Modulation (filter) envelope** — route a *second* envelope to the
**filter cutoff** and you get the classic synth motion: a fast attack +
short decay on cutoff gives a percussive "wow"/pluck bite that opens
bright then closes dark, entirely independent of the loudness contour.
This filter-cutoff-by-envelope trick is behind most plucks, basses, and
"expressive" leads.

---

## LFOs and modulation routing

An **LFO** is a low-frequency oscillator used not as sound but as a
*control* signal — a slow, repeating shape (sine, triangle, ramp, square,
random) that continuously moves a parameter. Its key controls: **rate**
(speed, free-running or tempo-synced), **depth** (how far it pushes the
target), and **shape**.

Where you route the **lfo** decides what you hear:
- → **pitch** = vibrato (small depth) or sirens/warble (large).
- → **amplitude** = tremolo, pulsing, gating.
- → **filter cutoff** = the wobble/sweep that defines dubstep bass and
  evolving pads.
- → **pulse width** = shimmering movement from one oscillator.
- → **wavetable position / FM ratio** = evolving, morphing textures.

Bitwig's strength here is its **unified modulator system**: LFOs,
envelopes, step sequencers, and more attach to almost *any* parameter of
*any* device, not just the synth's built-in slots. That means the same
routing logic (source → depth → destination) applies everywhere, and you
can layer several modulators on one target. For the catalog of modulator
sources and creative routings, see `../../coach/references/modulators.md`.

---

## Noise and sub — texture and weight

**Noise** is all frequencies at once, no pitch. A short burst of noise
through the amp envelope's attack adds a realistic **transient** (breath
on a flute, pick on a string, air on a hi-hat); sustained noise, filtered,
becomes wind, surf, or a riser. Most acoustic-flavoured synth patches owe
their believability to a little noise in the attack.

**Sub** (covered under oscillators above) is the other end: pure low-end
weight. Together they bracket the spectrum — noise adds life at the top,
sub adds authority at the bottom.

---

## How to reason from here

1. **Wrong family?** If the target is inharmonic (bell, metallic, EP) and
   you are on a subtractive patch, no filter move will get you there —
   swap to FM-4/Phase-4. If it is evolving/digital, reach for a wavetable
   source. Choose the source *before* dialling in.
2. **Right family, wrong tone?** Work the path in order: oscillator
   (harmonic ceiling) → filter (brightness/character) → envelopes
   (movement and contour) → modulation (life).
3. **Translate the adjective.** Map the described word to concrete moves
   with `descriptor-dictionary.md`, then resolve exact parameter names and
   step values from the per-device guide in `references/devices/`.

Everything downstream in this knowledge base assumes this signal-path
model. When a change does not do what you expect, re-check which *stage*
you are actually on — the fix is usually one box to the left or right.
