> Provenance: Original distillation (Wigout Studio) — parameters verified against Bitwig v1 Phase-4.

# Phase-4

**Synthesis:** phase modulation across **four oscillators** that can modulate
each other's phase (DX-adjacent, without being a strict FM operator maze).
Phase modulation with non-integer pitch relationships produces inharmonic,
metallic partials a filter cannot carve out of a saw.

**Character:** glassy, digital, and slightly unstable — great when a sound needs
motion and edge. Reach for it for evolving leads, metallic plucks, cold/glassy
digital pads, and gnarly/Reese-style basses.

## Key parameters (what each does sonically, useful range)

Phase-4's exact control labels are not corroborated in the fact-checked KB, so
the entries below are named by **function**. **Confirm each exact displayed label
live** before you rely on it — describe the move by what it does, not by a
guessed proper noun.

- **Per-oscillator pitch / ratio** — each oscillator's tuning relative to the
  played note. This is the core timbre control in phase modulation: integer
  relationships stay harmonic/musical, non-integer relationships turn the tone
  metallic and glassy. Small changes reshape the whole spectrum, so it is worth
  automating or modulating rather than setting once.
- **Per-oscillator waveform / shape** — the source shape each oscillator reads
  from its phase ramp. Changes the harmonic content that then gets modulated.
  *Often a discrete selector — see below.*
- **Phase-modulation amount (between oscillators)** — how strongly one
  oscillator bends another's phase. Low = subtle color; high = dense,
  clangy, aggressive spectra. This is the "brightness/edge of the FM
  character" control. Modulate it with an envelope for a metallic attack that
  settles into a purer tone.
- **Oscillator sync** — hard-syncs one oscillator's cycle to another, tearing
  its waveform into a bright, formant-rich tone; sweeping the synced
  oscillator's pitch is the signature sync-lead scream. Confirm live whether
  sync is a toggle, a per-oscillator mode, or both.
- **Amp envelope (Attack / Decay / Sustain / Release)** — standard loudness
  contour (pluck vs pad). Confirm live whether the envelope is full ADSR.
- **Modulation routing** — a phase-mod synth's routing (which oscillator feeds
  which) is a major sonic control. Confirm the live layout before assuming an
  operator can reach a given target.

## Discrete parameters (address at exact normalized step)

A stepped selector only takes if the write lands exactly on a step. **Do not
guess normalized step values — read the parameter's displayed value live to
enumerate the steps**, then match the readout to the option you want.

- **Per-oscillator waveform / shape selector** (if the shape is chosen from a
  list rather than swept continuously — check the readout to tell which).
- **Sync mode / routing selector** — any control that picks a discrete
  modulation topology or sync behavior.

## Good starting points

- **Metallic pluck / bell-ish lead:** set one oscillator to a non-integer pitch
  relative to another, raise the phase-modulation amount, and put a fast-attack
  short-decay envelope on that amount so the clang decays into a cleaner tone;
  short amp decay, low sustain.
- **Cold glassy pad:** integer-ish pitches for a musical base, moderate
  phase-mod amount, long amp attack and release; add a slow LFO to a
  per-oscillator pitch or to the phase-mod amount for drift.
- **Reese/gnarly bass:** two detuned oscillators, moderate phase-mod between
  them for grind, low pitch, filter/tone down; confirm which oscillators are
  routed to the output live.
