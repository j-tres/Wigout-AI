> Provenance: Original distillation (Wigout Studio) — parameters verified against Bitwig v1 Polysynth.

# Polysynth

**Synthesis:** classic two-oscillator subtractive. Two oscillators (plus, on
Polysynth, oscillator **sync**) feed a filter and note-triggered envelopes — the
textbook analog-style signal path. Body lives in the oscillators, brightness in
the filter, motion in the envelopes.

**Character:** the bread-and-butter subtractive synth — fast, familiar, "classic"
results. Reach for it for synth leads, pads, and basses when Polymer feels too
modern or you just want a dependable two-oscillator sound.

## Key parameters (what each does sonically, useful range)

Filter-control labels are **page-dependent, not fixed** (all observed live):
Polysynth's factory FILTER page labels cutoff **"Filt Freq"** and resonance
**"Reso"** (init preset, 2026-07-11); preset macro pages label the same
control freely — **"Cutoff"** on one preset (2026-07-10), **"LP"** on another
(2026-07-11). Never assume a label; read the current page live (e.g. via
`get_selected_device_parameters`). Note the init preset exposes the factory
pages (OSC1, OSC2, MIX, FILTER, FILTER/EG, AMP, Envelope, Common, Vibrato);
a preset with saved macro pages **replaces** them entirely (observed: a
preset exposing only "Perform").

- **Oscillator 1 / 2 waveform** — the raw harmonic material of each oscillator.
  Saw = bright/full, square/pulse = hollow/reedy, triangle/sine = soft/sub.
  Mixing two waveforms is the core of the sound. *Waveform choice is usually a
  discrete selector — see below.*
- **Oscillator pitch / detune (osc 1 vs osc 2)** — offset the two oscillators
  by an interval (octave/fifth for weight) or by a few cents for **detune**: the
  beating between them reads as width and thickness. A little warms and animates;
  a lot smears pitch.
- **Pulse width / shape** (if the chosen waveform exposes it) — sets how hollow a
  pulse is; modulating it (PWM) gives shimmering movement from one oscillator.
  Confirm the live label and whether it is per-oscillator.
- **Oscillator sync** — hard-syncs one oscillator to the other, tearing its
  waveform into a bright, formant-rich tone; sweeping the synced oscillator's
  pitch is the signature sync-lead scream. **Confirmed live 2026-07-11:**
  per-oscillator **Sync1** / **Sync2** controls on the factory OSC1/OSC2
  remote pages (init preset).
- **Filter cutoff** (label varies by page — "Filt Freq" on the factory
  FILTER page, "Cutoff"/"LP" seen on preset macro pages; read it live) —
  the primary brightness control; down = darker/warmer, up = brighter.
  Continuous 0..1, the first move for most brightness fixes.
- **Filter Resonance** — a peak at the cutoff; a little adds nasal presence,
  a lot whistles/self-oscillates. Continuous 0..1.
- **Filter type / slope** — low-pass vs high-pass/band-pass and how steeply it
  cuts (gentle vs. dramatic). *Discrete selector — see below.*
- **Amp envelope (Attack / Decay / Sustain / Release)** — loudness contour:
  pluck (fast attack, short decay, low sustain) vs pad (slow attack, high
  sustain, long release).
- **Filter/modulation envelope** — route a second envelope to the filter
  Cutoff for the classic pluck "wow" (opens bright, closes dark) independent
  of loudness. Confirm live how it is exposed and its amount/polarity control.

## Discrete parameters (address at exact normalized step)

A stepped selector only takes if the write lands exactly on a step. **Do not
assume normalized step values — read the parameter's displayed value live to
enumerate the steps**, then match the readout to the option you want.

- **Oscillator 1 / 2 waveform** — discrete list of waveforms.
- **Filter type / slope** — discrete list of filter modes.
- **Sync mode** — if sync is chosen from discrete options rather than a plain
  on/off toggle.

## Good starting points

- **Fat analog bass:** both oscillators saw, osc 2 an octave down (or a few cents
  detuned) for weight, Filter Cutoff low-mid with modest Resonance, a fast
  filter-envelope attack/short decay for punch; tight amp envelope.
- **Warm pad:** two saws detuned a few cents for width, Filter Cutoff
  moderate, long amp attack + long release + high sustain; slow LFO to Cutoff
  or pulse width for gentle life.
- **Sync lead:** enable oscillator sync (Sync1/Sync2 on the factory
  OSC1/OSC2 remote pages — confirmed live 2026-07-11), mid-high filter
  cutoff, sweep or modulate the synced oscillator's pitch for the scream;
  short amp attack, sustained body.
