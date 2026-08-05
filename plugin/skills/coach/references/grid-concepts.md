<!-- provenance: original distillation by Wigout Studio authors, 2026-07. No Bitwig-manual text. -->
# The Grid — the mental model that makes it click

The Grid is Bitwig's modular sound-design environment. It comes in three
flavors: **Poly Grid** (a polyphonic instrument), **FX Grid** (an audio
effect), and **Note Grid** (a note/MIDI effect). Same patching language,
different jobs.

## The one idea to teach first: everything is a signal

In The Grid there is no hard line between audio, control, and pitch —
they are all just **signals** flowing down patch cords. A slow envelope
and a screaming oscillator are the same kind of thing at different rates,
so you can patch an oscillator into a filter's cutoff, or an envelope
into a pitch, and it simply works. This "no wrong connections" mindset is
what frees people who feel boxed in by fixed-architecture synths. Once a
user internalizes "it's all signals," the module count stops being
intimidating.

## The second idea: it's phase-driven

Oscillators and LFOs in The Grid are driven by **phase** — a ramp from 0
to 1 that repeats. A **Phasor** module generates that ramp; oscillator
shapes read it to produce a waveform. Because phase is exposed as a
signal you can patch, you can reset it, share one phase across several
oscillators to keep them locked, or bend it for sync and FM effects.
Thinking in phase (rather than "an oscillator is a black box") is the key
to the more advanced patches.

## Core module families

- **Oscillators** — sine, saw, pulse, wavetable, and phase/FM sources.
- **Filters** — low/high/band-pass, comb, and more, all patchable.
- **Envelopes & LFOs** — ADSR, AD, and free/sync'd modulation ramps.
- **Math & Logic** — add, multiply, scale, compare, quantize; the glue
  that shapes and combines signals.
- **Mix & Level** — mixers, VCAs, gain, pan.
- **Data** — arrays, steps, and pitch/scale tools for sequencing and
  quantizing.
- **I/O** — note input modules carrying pitch, gate, and velocity
  signals, plus audio in/out; how the patch talks to the rest of the
  track.

## Three starter patches (described conceptually)

1. **Classic subtractive voice.** Note pitch input → sawtooth oscillator
   → low-pass filter → VCA controlled by an ADSR envelope → audio out. Map
   a second envelope to the filter cutoff and you have a complete
   synth — the "hello world" of The Grid.

2. **FM bell.** Two sine/phase oscillators where one modulates the
   other's phase; an envelope on the modulation amount so the metallic
   attack decays into a purer tone. This teaches why FM-4 sounds the way
   it does, built from parts.

3. **Generative/rhythmic texture.** A tempo-synced Phasor or LFO plus a
   random source feeding filter cutoff and level, with pitch quantized
   through a scale module. Hold one chord and the patch evolves itself —
   a self-playing ambient bed.

## Hard boundary: the Controller API cannot patch Grid internals

State this plainly whenever The Grid comes up. Bitwig's Controller API
(and therefore this plugin) **cannot create modules, draw patch cords, or
edit anything inside a Grid patch.** The **coach** teaches Grid concepts
so the user can build patches themselves in the UI. The **composer** can
only add a Grid device to a track and tweak the parameters that patch
already **exposes** to the outside (the mapped macros/knobs) — it cannot
reach inside. So when a user wants a bespoke Grid patch, the right move is
to teach it and let them wire it, not to promise the tooling will build
it.
